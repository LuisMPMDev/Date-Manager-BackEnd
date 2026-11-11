package com.datemanager.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.datemanager.PruebaAdministracion;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BloqueoAdministracionTest extends PruebaAdministracion {

    private ExecutorService hilos;

    @BeforeEach
    void abrirHilos() {
        hilos = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void cerrarHilos() {
        hilos.shutdownNow();
    }

    /** Toma el bloqueo en otro hilo y lo retiene hasta que se cuente {@code soltar}; vuelve cuando ya lo tiene. */
    private Future<?> retener(CountDownLatch soltar) throws InterruptedException {
        CountDownLatch tomado = new CountDownLatch(1);
        Future<?> retenedor = hilos.submit(() -> tx.escribir(() -> {
            bloqueo.bloquear();
            tomado.countDown();
            esperar(soltar);
        }));
        assertTrue(tomado.await(10, TimeUnit.SECONDS), "el primer hilo debía tomar el bloqueo");
        return retenedor;
    }

    private static void esperar(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * El driver confirma con un único {@code IF @@TRANCOUNT > 0 COMMIT TRAN}. Si el bloqueo abriera una
     * transacción anidada (contador 2), ese commit no confirmaría nada y el bloqueo global seguiría tomado.
     */
    @Test
    void elBloqueoNoAnidaLaTransaccionYUnSoloCommitLaCierra() {
        int niveles = tx.escribir(() -> {
            bloqueo.bloquear();
            return consultarInt("SELECT @@TRANCOUNT");
        });

        assertEquals(1, niveles);
    }

    @Test
    void llamarlaDosVecesEnLaMismaTransaccionNoAnidaNiSeBloqueaASiMisma() {
        int niveles = tx.escribir(() -> {
            bloqueo.bloquear();
            bloqueo.bloquear();
            return consultarInt("SELECT @@TRANCOUNT");
        });

        assertEquals(1, niveles);
    }

    @Test
    void dentroDeUnaTransaccionQueYaHizoAlgoTambienQuedaEnUnNivel() {
        int niveles = tx.escribir(() -> {
            consultarInt("SELECT 1");
            bloqueo.bloquear();
            return consultarInt("SELECT @@TRANCOUNT");
        });

        assertEquals(1, niveles);
    }

    @Test
    void exigeUnaTransaccion() {
        assertThrows(IllegalStateException.class, () -> bloqueo.bloquear());
    }

    @Test
    void elSegundoEsperaAQueElPrimeroTermine() throws Exception {
        CountDownLatch soltar = new CountDownLatch(1);
        Future<?> primero = retener(soltar);
        long inicio = System.nanoTime();
        Future<Long> segundo = hilos.submit(() -> tx.escribir(() -> {
            bloqueo.bloquear();
            return System.nanoTime();
        }));

        Thread.sleep(800);
        assertFalse(segundo.isDone(), "el segundo debe seguir esperando mientras el primero tiene el bloqueo");
        soltar.countDown();
        primero.get(10, TimeUnit.SECONDS);
        long tomadoEn = segundo.get(10, TimeUnit.SECONDS);

        assertTrue(TimeUnit.NANOSECONDS.toMillis(tomadoEn - inicio) >= 700, "el segundo esperó al primero");
    }

    @Test
    void conLaEsperaAgotadaEsConflictoYTodoQuedaIntacto() throws Exception {
        CountDownLatch soltar = new CountDownLatch(1);
        Future<?> primero = retener(soltar);
        assertFalse(bloqueoLibreDesdeOtraConexion(), "mientras el primero lo retiene, nadie más lo obtiene");
        BloqueoAdministracion impaciente = new BloqueoAdministracion(tx, 300);

        ErrorNegocio error = exigirError(CodigoError.CONFLICTO, () -> tx.escribir(() -> impaciente.bloquear()));

        assertEquals("Hay otro cambio de usuarios o roles en curso. Inténtalo de nuevo en unos segundos.",
                error.getMessage());
        soltar.countDown();
        primero.get(10, TimeUnit.SECONDS);
        // Con el bloqueo libre vuelve a funcionar y el pool está entero.
        tx.escribir(() -> impaciente.bloquear());
        assertEquals(10, pool.disponibles());
    }

    @Test
    void sePuedeTomarOtraVezDespuesDeConfirmar() {
        tx.escribir(() -> bloqueo.bloquear());
        tx.escribir(() -> bloqueo.bloquear());

        assertTrue(bloqueoLibreDesdeOtraConexion());
    }

    @Test
    void sePuedeTomarOtraVezDespuesDeRevertir() {
        assertThrows(IllegalStateException.class, () -> tx.escribir((Runnable) () -> {
            bloqueo.bloquear();
            throw new IllegalStateException("falla después de bloquear");
        }));

        assertTrue(bloqueoLibreDesdeOtraConexion());
        tx.escribir(() -> bloqueo.bloquear());
    }

    @Test
    void lasConexionesVuelvenAlPoolSinTransaccionAbiertaNiBloqueoTomado() throws Exception {
        tx.escribir(() -> bloqueo.bloquear());
        assertThrows(IllegalStateException.class, () -> tx.escribir((Runnable) () -> {
            bloqueo.bloquear();
            throw new IllegalStateException("falla");
        }));

        Connection primera = pool.prestar();
        Connection segunda = pool.prestar();
        try {
            for (Connection c : new Connection[] { primera, segunda }) {
                assertTrue(c.getAutoCommit());
                try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT @@TRANCOUNT")) {
                    r.next();
                    assertEquals(0, r.getInt(1), "sin transacciones abiertas en la conexión devuelta");
                }
            }
        } finally {
            pool.devolver(primera);
            pool.devolver(segunda);
        }
        assertTrue(bloqueoLibreDesdeOtraConexion());
    }

    /** Intenta tomar el mismo bloqueo, sin esperar, desde una conexión que no sea la usada por las transacciones. */
    private boolean bloqueoLibreDesdeOtraConexion() {
        Connection primera = pool.prestar();
        Connection segunda = pool.prestar();
        try {
            segunda.setAutoCommit(false);
            try (Statement abrir = segunda.createStatement()) {
                // sp_getapplock con dueño «Transaction» exige una transacción abierta (la misma jugada del bloqueo).
                abrir.execute("IF @@TRANCOUNT = 0 BEGIN BEGIN TRANSACTION; IF @@TRANCOUNT = 2 COMMIT TRANSACTION; END");
            }
            try (Statement s = segunda.createStatement(); ResultSet r = s.executeQuery("""
                    DECLARE @r INT;
                    EXEC @r = sp_getapplock @Resource = N'datemanager.administracion', @LockMode = N'Exclusive',
                         @LockOwner = N'Transaction', @LockTimeout = 0;
                    SELECT @r;
                    """)) {
                r.next();
                return r.getInt(1) >= 0;
            } finally {
                segunda.rollback();
            }
        } catch (SQLException e) {
            throw new ErrorSql(e);
        } finally {
            pool.devolver(primera);
            pool.devolver(segunda);
        }
    }
}
