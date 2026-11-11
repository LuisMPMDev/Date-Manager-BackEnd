package com.datemanager.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.datemanager.PruebaBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Transacciones sobre un pool propio de 5 conexiones (para contar fugas) contra la base de pruebas real. */
class TransaccionTest extends PruebaBase {

    private static final int TAMANO = 5;
    private static final String INSERTAR_ROL = "INSERT INTO rol (id, nombre, descripcion) VALUES (?, ?, N'')";

    private Pool propio;
    private Transaccion transaccion;

    @BeforeEach
    void crear() {
        propio = new Pool(System.getenv("DATEMANAGER_TEST_DB_URL"), System.getenv("DATEMANAGER_TEST_DB_USUARIO"),
                System.getenv("DATEMANAGER_TEST_DB_CLAVE"), TAMANO, Duration.ofMillis(500));
        transaccion = new Transaccion(propio);
    }

    @AfterEach
    void cerrar() {
        propio.cerrar();
    }

    private void insertarRol(Connection c, String nombre) {
        try (var s = c.prepareStatement(INSERTAR_ROL)) {
            s.setObject(1, UUID.randomUUID());
            s.setNString(2, nombre);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorSql(e);
        }
    }

    private int roles() {
        // Con otra sesión (la del pool compartido). READPAST salta las filas sin confirmar en vez de esperar a que
        // la transacción de la prueba termine, así se puede mirar desde dentro de ella sin bloquearse.
        return consultarInt("SELECT COUNT(*) FROM rol WITH (READPAST)");
    }

    @Test
    void elCommitEsVisibleDesdeOtraConexion() {
        String devuelto = transaccion.escribir(() -> {
            insertarRol(transaccion.conexion(), "Recepción");
            assertEquals(0, roles(), "dentro de la transacción, otra sesión aún no lo ve");
            return "listo";
        });

        assertEquals("listo", devuelto);
        assertEquals(1, roles());
        assertEquals("Recepción", consultarTexto("SELECT nombre FROM rol"));
        assertEquals(TAMANO, propio.disponibles());
    }

    @Test
    void escribirConRunnableTambienConfirma() {
        transaccion.escribir(() -> insertarRol(transaccion.conexion(), "Admin"));

        assertEquals(1, roles());
    }

    @Test
    void unaExcepcionRevierteTodoYSaleLaOriginalTalCual() {
        IllegalArgumentException original = new IllegalArgumentException("boom");

        IllegalArgumentException salida = assertThrows(IllegalArgumentException.class, () -> transaccion.escribir((Runnable) () -> {
            insertarRol(transaccion.conexion(), "A");
            insertarRol(transaccion.conexion(), "B");
            throw original;
        }));

        assertSame(original, salida);
        assertEquals(0, roles());
        assertEquals(TAMANO, propio.disponibles());
    }

    @Test
    void unErrorTambienRevierteYSeRelanza() {
        AssertionError original = new AssertionError("fallo grave");

        AssertionError salida = assertThrows(AssertionError.class, () -> transaccion.escribir((Runnable) () -> {
            insertarRol(transaccion.conexion(), "A");
            throw original;
        }));

        assertSame(original, salida);
        assertEquals(0, roles());
        assertEquals(TAMANO, propio.disponibles());
        assertFalse(transaccion.activa());
    }

    @Test
    void unaViolacionDeLaBaseRevierteYLaConexionSigueSirviendo() {
        UUID id = UUID.randomUUID();
        transaccion.escribir(() -> insertarRol(transaccion.conexion(), "Unico"));

        // Dos filas con la misma clave primaria: la segunda viola la restricción (2627).
        ErrorSql e = assertThrows(ErrorSql.class, () -> transaccion.escribir(() -> {
            for (int i = 0; i < 2; i++) {
                try (var s = transaccion.conexion().prepareStatement(INSERTAR_ROL)) {
                    s.setObject(1, id);
                    s.setNString(2, "Repetido" + i);
                    s.executeUpdate();
                } catch (SQLException ex) {
                    throw new ErrorSql(ex);
                }
            }
        }));

        assertTrue(ErroresBase.esViolacionDeRestriccion(e));
        assertEquals(1, roles(), "solo quedó el rol de antes");
        // La misma conexión (único permiso reutilizado) sigue sirviendo.
        transaccion.escribir(() -> insertarRol(transaccion.conexion(), "Otro"));
        assertEquals(2, roles());
    }

    @Test
    void unaLlamadaAnidadaReutilizaLaTransaccionYLaConexionDelHilo() {
        AtomicReference<Connection> externa = new AtomicReference<>();
        AtomicReference<Connection> interna = new AtomicReference<>();

        transaccion.escribir(() -> {
            externa.set(transaccion.conexion());
            insertarRol(transaccion.conexion(), "Externa");
            transaccion.escribir(() -> {
                interna.set(transaccion.conexion());
                insertarRol(transaccion.conexion(), "Interna");
                assertEquals(TAMANO - 1, propio.disponibles(), "una sola conexión en uso");
            });
            transaccion.leer(() -> {
                assertSame(externa.get(), transaccion.conexion());
                return null;
            });
            assertEquals(0, roles(), "la anidada no confirmó por su cuenta");
        });

        assertSame(externa.get(), interna.get());
        assertEquals(2, roles());
        assertEquals(TAMANO, propio.disponibles());
    }

    @Test
    void unaFalloEnLaAnidadaRevierteLaExterna() {
        assertThrows(IllegalStateException.class, () -> transaccion.escribir(() -> {
            insertarRol(transaccion.conexion(), "Externa");
            transaccion.escribir((Runnable) () -> {
                insertarRol(transaccion.conexion(), "Interna");
                throw new IllegalStateException("falla la anidada");
            });
        }));

        assertEquals(0, roles());
        assertEquals(TAMANO, propio.disponibles());
    }

    @Test
    void leerNoConfirmaNadaYSuConexionVuelveAlPool() {
        int vistas = transaccion.leer(() -> {
            insertarRol(transaccion.conexion(), "Solo lectura");
            try (var s = transaccion.conexion().createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM rol")) {
                r.next();
                return r.getInt(1);
            } catch (SQLException e) {
                throw new ErrorSql(e);
            }
        });

        assertEquals(1, vistas, "dentro de la lectura se ve lo suyo");
        assertEquals(0, roles(), "pero no se confirma");
        assertEquals(TAMANO, propio.disponibles());
    }

    @Test
    void escribirDentroDeUnaLecturaSeRechazaYNoPierdeLaConexion() {
        assertThrows(IllegalStateException.class, () -> transaccion.leer(() -> {
            transaccion.escribir(() -> insertarRol(transaccion.conexion(), "No"));
            return null;
        }));

        assertEquals(0, roles());
        assertEquals(TAMANO, propio.disponibles());
        assertFalse(transaccion.activa());
    }

    @Test
    void exigirActivaFueraDeUnaTransaccionLanzaYDentroNo() {
        assertThrows(IllegalStateException.class, transaccion::exigirActiva);
        assertThrows(IllegalStateException.class, transaccion::conexion);
        assertFalse(transaccion.activa());

        transaccion.escribir(() -> {
            transaccion.exigirActiva();
            assertTrue(transaccion.activa());
        });
        transaccion.leer(() -> {
            transaccion.exigirActiva();
            return null;
        });
        assertThrows(IllegalStateException.class, transaccion::exigirActiva);
    }

    @Test
    void cincuentaEjecucionesQueFallanDejanElPoolEntero() {
        for (int i = 0; i < 50; i++) {
            int n = i;
            Throwable e = assertThrows(Throwable.class, () -> transaccion.escribir(() -> {
                insertarRol(transaccion.conexion(), "Rol " + n);
                switch (n % 5) {
                    case 0 -> throw new IllegalStateException("negocio");
                    case 1 -> throw new AssertionError("error");
                    case 2 -> {
                        // Error de SQL Server a mitad de la transacción.
                        // (El error de una consulta llega al leer sus filas, no al ejecutarla.)
                        try (var s = transaccion.conexion().createStatement();
                                var r = s.executeQuery("SELECT CAST('abc' AS INT)")) {
                            r.next();
                        } catch (SQLException ex) {
                            throw new ErrorSql(ex);
                        }
                    }
                    case 3 -> transaccion.leer(() -> {
                        throw new UnsupportedOperationException("anidada");
                    });
                    default -> {
                        // Una anidada que viola una restricción de la base.
                        try (var s = transaccion.conexion().prepareStatement("INSERT INTO cita (id) VALUES (?)")) {
                            s.setObject(1, UUID.randomUUID());
                            s.executeUpdate();
                        } catch (SQLException ex) {
                            throw new ErrorSql(ex);
                        }
                    }
                }
                return null;
            }), "iteración " + n);
            assertNotNull(e);
            assertEquals(TAMANO, propio.disponibles(), "tras la ejecución " + i);
            assertFalse(transaccion.activa(), "el hilo no conserva transacción tras la ejecución " + i);
        }

        assertEquals(0, roles(), "ninguna se confirmó");
        // Y el pool sigue sirviendo cuando están todas las conexiones en uso a la vez.
        transaccion.escribir(() -> insertarRol(transaccion.conexion(), "Final"));
        assertEquals(1, roles());
    }

    @Test
    void unPoolAgotadoDentroDeUnaTransaccionFallaSinDejarEstadoEnElHilo() throws Exception {
        Pool uno = new Pool(System.getenv("DATEMANAGER_TEST_DB_URL"), System.getenv("DATEMANAGER_TEST_DB_USUARIO"),
                System.getenv("DATEMANAGER_TEST_DB_CLAVE"), 1, Duration.ofMillis(300));
        try {
            Transaccion t = new Transaccion(uno);
            CountDownLatch dentro = new CountDownLatch(1);
            CountDownLatch seguir = new CountDownLatch(1);
            ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();
            try {
                Future<?> ocupada = hilos.submit(() -> t.escribir(() -> {
                    dentro.countDown();
                    try {
                        seguir.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
                dentro.await();

                assertThrows(IllegalStateException.class, () -> t.escribir(() -> null));
                assertFalse(t.activa());

                seguir.countDown();
                ocupada.get(5, TimeUnit.SECONDS);
            } finally {
                hilos.shutdownNow();
            }
            assertEquals(1, uno.disponibles());
            assertEquals("ok", t.leer(() -> "ok"));
        } finally {
            uno.cerrar();
        }
    }

    // ---- Una anidada que falla y se captura deja la transacción solo para deshacer ----

    @Test
    void unaAnidadaQueFallaYSeCapturaNoDejaConfirmarLaExterna() {
        IllegalArgumentException anidada = new IllegalArgumentException("falla la anidada");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> transaccion.escribir(() -> {
            insertarRol(transaccion.conexion(), "Antes");
            try {
                transaccion.escribir((Runnable) () -> {
                    insertarRol(transaccion.conexion(), "Dentro");
                    throw anidada;
                });
            } catch (IllegalArgumentException capturada) {
                // El código externo se traga el error y sigue.
            }
            insertarRol(transaccion.conexion(), "Despues");
        }));

        assertTrue(e.getMessage().contains("solo para deshacer"), e.getMessage());
        assertSame(anidada, e.getCause(), "la causa es el primer fallo anidado");
        assertEquals(0, roles(), "no se guardó ni lo de antes ni lo de después");
        assertEquals(TAMANO, propio.disponibles());
        assertFalse(transaccion.activa());
        // La marca es de aquella transacción: la siguiente se confirma con normalidad.
        transaccion.escribir(() -> insertarRol(transaccion.conexion(), "Nueva"));
        assertEquals(1, roles());
    }

    @Test
    void unaViolacionDeLaBaseEnLaAnidadaCapturadaTambienImpideConfirmarYSeConservaComoCausa() {
        UUID id = UUID.randomUUID();

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> transaccion.escribir(() -> {
            insertarRol(transaccion.conexion(), "Antes");
            try {
                transaccion.escribir(() -> {
                    for (int i = 0; i < 2; i++) {
                        try (var s = transaccion.conexion().prepareStatement(INSERTAR_ROL)) {
                            s.setObject(1, id);
                            s.setNString(2, "Repetido" + i);
                            s.executeUpdate();
                        } catch (SQLException ex) {
                            throw new ErrorSql(ex);
                        }
                    }
                });
            } catch (ErrorSql capturada) {
                // Se traga el error de la base y sigue escribiendo.
            }
            insertarRol(transaccion.conexion(), "Despues");
        }));

        assertTrue(ErroresBase.esViolacionDeRestriccion(e), "la causa conserva el error de la base");
        assertFalse(e.getMessage().contains("Repetido"), "el mensaje propio no lleva datos");
        assertEquals(0, roles());
        assertEquals(TAMANO, propio.disponibles());
    }

    @Test
    void tambienMarcaUnaLecturaAnidadaQueFalla() {
        assertThrows(IllegalStateException.class, () -> transaccion.escribir(() -> {
            insertarRol(transaccion.conexion(), "Antes");
            try {
                transaccion.leer(() -> {
                    throw new UnsupportedOperationException("lectura");
                });
            } catch (UnsupportedOperationException capturada) {
                // Se ignora.
            }
        }));

        assertEquals(0, roles());
        assertEquals(TAMANO, propio.disponibles());
    }

    @Test
    void unErrorCapturadoPorElCodigoExternoSinLlamadaAnidadaNoMarcaNada() {
        transaccion.escribir(() -> {
            try {
                throw new IllegalArgumentException("propio del trabajo");
            } catch (IllegalArgumentException capturada) {
                // No hubo llamada anidada: la transacción sigue sana.
            }
            insertarRol(transaccion.conexion(), "Sigue");
        });

        assertEquals(1, roles());
    }

    @Test
    void unaAnidadaQueFuncionaNoMarcaNada() {
        transaccion.escribir(() -> {
            transaccion.escribir(() -> insertarRol(transaccion.conexion(), "A"));
            transaccion.leer(() -> null);
            insertarRol(transaccion.conexion(), "B");
        });

        assertEquals(2, roles());
    }

    @Test
    void laTransaccionEsDeCadaHiloYNoSeComparte() throws Exception {
        ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();
        try {
            transaccion.escribir(() -> {
                Connection mia = transaccion.conexion();
                Throwable e = assertThrowsEnOtroHilo(hilos);
                assertInstanceOf(IllegalStateException.class, e);
                assertSame(mia, transaccion.conexion());
            });
        } finally {
            hilos.shutdownNow();
        }
    }

    private Throwable assertThrowsEnOtroHilo(ExecutorService hilos) {
        try {
            hilos.submit(() -> transaccion.conexion()).get(5, TimeUnit.SECONDS);
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            return e.getCause();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void dosTransaccionesEnHilosDistintosUsanConexionesDistintas() throws Exception {
        CountDownLatch ambas = new CountDownLatch(2);
        ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<Connection> a = hilos.submit(() -> usarYEsperar(ambas));
            Future<Connection> b = hilos.submit(() -> usarYEsperar(ambas));
            assertNotSame(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally {
            hilos.shutdownNow();
        }
        assertEquals(TAMANO, propio.disponibles());
    }

    private Connection usarYEsperar(CountDownLatch ambas) {
        return transaccion.escribir(() -> {
            ambas.countDown();
            try {
                ambas.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return transaccion.conexion();
        });
    }
}
