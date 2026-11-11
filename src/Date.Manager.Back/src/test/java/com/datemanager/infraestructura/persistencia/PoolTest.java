package com.datemanager.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.datemanager.PruebaBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** El pool contra la base de pruebas real. Cada prueba usa su propio pool y lo cierra. */
class PoolTest extends PruebaBase {

    private Pool propio;

    private Pool crear(int tamano, Duration espera) {
        propio = new Pool(System.getenv("DATEMANAGER_TEST_DB_URL"), System.getenv("DATEMANAGER_TEST_DB_USUARIO"),
                System.getenv("DATEMANAGER_TEST_DB_CLAVE"), tamano, espera);
        return propio;
    }

    @AfterEach
    void cerrarPropio() {
        if (propio != null) {
            propio.cerrar();
        }
    }

    @Test
    void diezPrestamosAgotanElPoolYElOnceFallaEnElPlazo() {
        Pool p = crear(10, Duration.ofMillis(400));
        List<Connection> prestadas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            prestadas.add(p.prestar());
        }
        assertEquals(0, p.disponibles());

        assertThrows(IllegalStateException.class, p::prestar);

        for (Connection c : prestadas) {
            p.devolver(c);
        }
        assertEquals(10, p.disponibles());
    }

    @Test
    void conElPlazoPorDefectoElPrestamoQueSobraEsperaEnVezDeFallarDeInmediato() {
        Pool p = new Pool(System.getenv("DATEMANAGER_TEST_DB_URL"), System.getenv("DATEMANAGER_TEST_DB_USUARIO"),
                System.getenv("DATEMANAGER_TEST_DB_CLAVE"), 1);
        propio = p;
        Connection unica = p.prestar();
        Thread hilo = Thread.currentThread();
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                return;
            }
            hilo.interrupt();
        });

        long inicio = System.nanoTime();
        assertThrows(IllegalStateException.class, p::prestar);

        assertTrue(Thread.interrupted(), "al interrumpirse conserva la marca de interrupción");
        assertTrue(Duration.ofNanos(System.nanoTime() - inicio).toMillis() >= 250, "esperó, no falló de inmediato");
        assertEquals(0, p.disponibles());
        p.devolver(unica);
        assertEquals(1, p.disponibles());
    }

    @Test
    void elPrestamoQueSobraFallaAlAgotarseElPlazoYDevolverReabre() throws Exception {
        Pool p = crear(3, Duration.ofMillis(400));
        Connection a = p.prestar();
        Connection b = p.prestar();
        Connection c = p.prestar();
        assertEquals(0, p.disponibles());

        long inicio = System.nanoTime();
        IllegalStateException e = assertThrows(IllegalStateException.class, p::prestar);
        long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
        assertTrue(ms >= 350 && ms < 5_000, "falló tras ~400 ms, tardó " + ms);
        assertFalse(e.getMessage().contains("jdbc"), "el mensaje no lleva la URL");
        assertEquals(0, p.disponibles());

        p.devolver(b);
        assertEquals(1, p.disponibles());
        Connection otra = p.prestar();
        assertTrue(otra.isValid(2));
        p.devolver(otra);
        p.devolver(a);
        p.devolver(c);
        assertEquals(3, p.disponibles());
    }

    @Test
    void unaConexionRotaNoVuelveAlPool() throws Exception {
        Pool p = crear(1, Duration.ofSeconds(2));
        Connection rota = p.prestar();
        rota.close();

        p.devolver(rota);

        assertEquals(1, p.disponibles(), "el permiso se libera aunque la conexión se descarte");
        Connection nueva = p.prestar();
        assertNotSame(rota, nueva);
        assertFalse(nueva.isClosed());
        assertTrue(nueva.isValid(2));
        p.devolver(nueva);
    }

    /** Una conexión real cuyo rollback falla como si la sesión hubiera muerto sin que {@code isClosed()} lo sepa. */
    private static Connection conRollbackRoto(Connection real, AtomicInteger cierres) {
        return (Connection) Proxy.newProxyInstance(PoolTest.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, metodo, argumentos) -> {
                    try {
                        if (metodo.getName().equals("rollback")) {
                            throw new SQLException("conexión rota", "08S01", 0);
                        }
                        if (metodo.getName().equals("close")) {
                            cierres.incrementAndGet();
                        }
                        return metodo.invoke(real, argumentos);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Test
    void unaConexionQueNoSePuedeRestablecerSeDescartaYSeCierra() throws Exception {
        AtomicInteger abiertas = new AtomicInteger();
        AtomicInteger cierres = new AtomicInteger();
        Pool p = new Pool(() -> {
            abiertas.incrementAndGet();
            return conRollbackRoto(DriverManager.getConnection(System.getenv("DATEMANAGER_TEST_DB_URL"),
                    System.getenv("DATEMANAGER_TEST_DB_USUARIO"), System.getenv("DATEMANAGER_TEST_DB_CLAVE")), cierres);
        }, 1, Duration.ofSeconds(2));
        propio = p;
        Connection rota = p.prestar();
        rota.setAutoCommit(false);

        p.devolver(rota);

        assertEquals(1, p.disponibles(), "el permiso se libera aunque la conexión se descarte");
        assertEquals(1, cierres.get(), "la descartada se cerró");
        Connection nueva = p.prestar();
        assertNotSame(rota, nueva);
        assertEquals(2, abiertas.get(), "se abrió otra en su lugar");
        assertTrue(nueva.isValid(2));
        p.devolver(nueva);
    }

    @Test
    void alDevolverSeRevierteLoPendienteYSeRestableceElEstado() throws Exception {
        Pool p = crear(1, Duration.ofSeconds(2));
        Connection c = p.prestar();
        c.setAutoCommit(false);
        c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        try (var s = c.prepareStatement("INSERT INTO rol (id, nombre, descripcion) VALUES (?, N'Pendiente', N'')")) {
            s.setObject(1, nuevoId());
            s.executeUpdate();
        }

        p.devolver(c);

        assertEquals(0, consultarInt("SELECT COUNT(*) FROM rol"), "lo pendiente se revirtió, no se confirmó");
        Connection otra = p.prestar();
        assertTrue(otra.getAutoCommit());
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, otra.getTransactionIsolation());
        p.devolver(otra);
    }

    @Test
    void devolverDosVecesOUnaConexionAjenaNoInflaLosPermisos() throws Exception {
        Pool p = crear(2, Duration.ofMillis(300));
        Connection c = p.prestar();

        p.devolver(c);
        p.devolver(c);
        p.devolver(null);

        assertEquals(2, p.disponibles());
        Connection a = p.prestar();
        Connection b = p.prestar();
        assertThrows(IllegalStateException.class, p::prestar);
        p.devolver(a);
        p.devolver(b);
        assertEquals(2, p.disponibles());
    }

    @Test
    void conVariosHilosNuncaSePasaDelTamanoYAlFinalEstaEntero() throws Exception {
        Pool p = crear(3, Duration.ofSeconds(10));
        AtomicInteger enUso = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<?>> futuros = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                futuros.add(hilos.submit(() -> {
                    Connection c = p.prestar();
                    try {
                        maximo.accumulateAndGet(enUso.incrementAndGet(), Math::max);
                        try (var s = c.createStatement()) {
                            s.execute("SELECT 1");
                        }
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    } finally {
                        enUso.decrementAndGet();
                        p.devolver(c);
                    }
                }));
            }
            for (Future<?> f : futuros) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }
        assertTrue(maximo.get() <= 3, "máximo simultáneo " + maximo.get());
        assertEquals(3, p.disponibles());
    }

    @Test
    void cerrarCierraLasInactivasYNoDejaPrestarMas() throws Exception {
        Pool p = crear(2, Duration.ofMillis(300));
        Connection c = p.prestar();
        Connection d = p.prestar();
        p.devolver(c);

        p.cerrar();

        assertTrue(c.isClosed(), "la inactiva se cerró");
        assertThrows(IllegalStateException.class, p::prestar);
        p.devolver(d);
        assertTrue(d.isClosed(), "la que volvió después de cerrar también se cierra");
        p.cerrar();
    }

    @Test
    void conUnaUrlInalcanzableFallaConUnMensajeSinSecretosYNoPierdePermisos() {
        Pool p = new Pool("jdbc:sqlserver://127.0.0.1:1;loginTimeout=2;databaseName=x", "usuario-x", "clave-secreta-x",
                2, Duration.ofSeconds(1));
        propio = p;

        IllegalStateException e = assertThrows(IllegalStateException.class, p::prestar);

        assertFalse(e.getMessage().contains("clave-secreta-x"));
        assertEquals(2, p.disponibles());
    }

    @Test
    void unTamanoInvalidoSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> new Pool("jdbc:x", "u", "c", 0));
        assertThrows(IllegalArgumentException.class, () -> new Pool("jdbc:x", "u", "c", -1));
    }

    @Test
    void hilosEsperandoSeDespiertanCuandoSeDevuelve() throws Exception {
        Pool p = crear(1, Duration.ofSeconds(10));
        Connection c = p.prestar();
        CountDownLatch esperando = new CountDownLatch(1);
        ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<Boolean> futuro = hilos.submit(() -> {
                esperando.countDown();
                Connection otra = p.prestar();
                try {
                    return otra.isValid(2);
                } finally {
                    p.devolver(otra);
                }
            });
            esperando.await();
            Thread.sleep(200);
            p.devolver(c);
            assertTrue(futuro.get(5, TimeUnit.SECONDS));
        } finally {
            hilos.shutdownNow();
        }
        assertEquals(1, p.disponibles());
    }
}
