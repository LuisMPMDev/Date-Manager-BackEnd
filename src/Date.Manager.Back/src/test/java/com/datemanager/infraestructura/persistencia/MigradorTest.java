package com.datemanager.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.datemanager.PruebaBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * El migrador contra la base de pruebas real. Cada prueba parte de una base limpia (con la guarda de
 * {@link Migrador#limpiar()}) y, al terminar la clase, la deja en la última versión para las demás pruebas.
 */
class MigradorTest extends PruebaBase {

    /** Checksums que Flyway guardó en su historial para las migraciones V1 a V10 reales: son los que hay que igualar. */
    private static final Map<Integer, Integer> CHECKSUMS_DE_FLYWAY = Map.ofEntries(
            Map.entry(1, 2131899645), Map.entry(2, -104475106), Map.entry(3, -978334752),
            Map.entry(4, -1799514279), Map.entry(5, 1136650878), Map.entry(6, 311236963),
            Map.entry(7, -560801525), Map.entry(8, -1351500152), Map.entry(9, 25954324),
            Map.entry(10, -1442203667));

    private static final String[] ARCHIVOS = {"V1__base.sql", "V2__roles_usuarios.sql", "V3__servicios.sql",
            "V4__clientes_citas.sql", "V5__enlaces.sql", "V6__indices_tablero.sql",
            "V7__restricciones_de_datos.sql", "V8__citas_sin_solape.sql", "V9__ventanas_sin_cruce.sql",
            "V10__disparador_citas_sin_bloqueos_de_mas.sql"};

    @AfterAll
    static void dejarElEsquemaMasReciente() {
        migrador.limpiar();
        migrador.migrar();
    }

    private static Migrador con(String carpeta) {
        return new Migrador(pool, carpeta);
    }

    private static String contenido(String recurso) {
        try (InputStream in = MigradorTest.class.getClassLoader().getResourceAsStream(recurso)) {
            assertNotNull(in, recurso);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean existe(String nombre) {
        return consultarInt("SELECT COUNT(*) FROM sys.objects WHERE name = ?", nombre) > 0;
    }

    private static int objetosDeUsuario() {
        return consultarInt("SELECT (SELECT COUNT(*) FROM sys.objects WHERE is_ms_shipped = 0) "
                + "+ (SELECT COUNT(*) FROM sys.triggers WHERE is_ms_shipped = 0)");
    }

    /** La tabla de historial con el diseño de Flyway (columnas y tipos), vacía. */
    private static void crearTablaDeFlyway() {
        ejecutar("CREATE TABLE flyway_schema_history (installed_rank INT NOT NULL PRIMARY KEY, "
                + "version NVARCHAR(50), description NVARCHAR(200), type NVARCHAR(20) NOT NULL, "
                + "script NVARCHAR(1000) NOT NULL, checksum INT, installed_by NVARCHAR(100) NOT NULL, "
                + "installed_on DATETIME NOT NULL DEFAULT GETDATE(), execution_time INT NOT NULL, success BIT NOT NULL)");
    }

    private static void insertarEnFlyway(int rango, int version, String descripcion, String script, int checksum) {
        ejecutar("INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, "
                + "installed_by, execution_time, success) VALUES (?, ?, ?, 'SQL', ?, ?, N'flyway', 5, 1)",
                rango, String.valueOf(version), descripcion, script, checksum);
    }

    /** Un historial de Flyway con las primeras {@code cuantas} migraciones reales, con el checksum que les toca. */
    private static void crearHistorialDeFlyway(int cuantas) {
        crearTablaDeFlyway();
        for (int i = 0; i < cuantas; i++) {
            String archivo = ARCHIVOS[i];
            String descripcion = archivo.substring(archivo.indexOf("__") + 2, archivo.length() - 4).replace('_', ' ');
            insertarEnFlyway(i + 1, i + 1, descripcion, archivo, CHECKSUMS_DE_FLYWAY.get(i + 1));
        }
    }

    // ---------------------------------------------------------------- Migración real V1 a V10

    @Test
    void sobreUnaBaseLimpiaAplicaDeLaV1ALaV10YUnaSegundaLlamadaNoReaplica() {
        migrador.limpiar();
        assertEquals(Optional.empty(), migrador.versionActual());

        migrador.migrar();

        assertEquals(Optional.of(10), migrador.versionActual());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), migrador.versionesAplicadas());
        assertTrue(existe("tr_cita_sin_solape"));
        assertTrue(existe("tr_ventana_sin_cruce"));
        assertTrue(existe("historial_migraciones"));
        assertFalse(existe("flyway_schema_history"));
        List<Map<String, Object>> historial = filas("SELECT * FROM historial_migraciones ORDER BY version");
        assertEquals(10, historial.size());
        assertTrue(historial.stream().allMatch(f -> Boolean.TRUE.equals(f.get("success"))));
        assertEquals("restricciones de datos", historial.get(6).get("description"));

        // Un dato que sobreviva a la segunda llamada prueba que no se reaplicó nada (V2 haría CREATE TABLE y fallaría).
        ejecutar("INSERT INTO rol (id, nombre, descripcion) VALUES (?, N'Recepción', N'')", nuevoId());
        migrador.migrar();

        assertEquals(1, consultarInt("SELECT COUNT(*) FROM rol"));
        assertEquals(historial, filas("SELECT * FROM historial_migraciones ORDER BY version"));
    }

    @Test
    void elChecksumCoincideConElQueGuardabaFlyway() {
        for (int i = 0; i < ARCHIVOS.length; i++) {
            assertEquals(CHECKSUMS_DE_FLYWAY.get(i + 1), Migrador.checksum(contenido("db/migration/" + ARCHIVOS[i])),
                    ARCHIVOS[i]);
        }
        // Sin BOM, sin distinguir fin de línea y sin contar los saltos: igual que Flyway.
        assertEquals(Migrador.checksum("a\nb"), Migrador.checksum("a\r\nb"));
        assertEquals(Migrador.checksum("a\nb"), Migrador.checksum(String.valueOf((char) 0xFEFF) + "a\nb\n"));
    }

    @Test
    void unChecksumRegistradoDistintoHaceFallarSinReaplicarNada() {
        migrador.limpiar();
        migrador.migrar();
        ejecutar("UPDATE historial_migraciones SET checksum = checksum + 1 WHERE version = 5");
        ejecutar("INSERT INTO rol (id, nombre, descripcion) VALUES (?, N'Sigue', N'')", nuevoId());

        IllegalStateException e = assertThrows(IllegalStateException.class, migrador::migrar);

        assertTrue(e.getMessage().contains("V5"), e.getMessage());
        assertTrue(e.getMessage().toLowerCase().contains("checksum"), e.getMessage());
        assertEquals(1, consultarInt("SELECT COUNT(*) FROM rol"), "no tocó los datos");
    }

    @Test
    void conElHistorialDeFlywayYaCreadoNoReaplicaNadaYLoUsa() {
        migrador.limpiar();
        migrador.migrar();
        // Como una base que creó Flyway: su tabla con V1 a V10 y sin la propia.
        crearHistorialDeFlyway(10);
        ejecutar("DROP TABLE historial_migraciones");
        ejecutar("INSERT INTO rol (id, nombre, descripcion) VALUES (?, N'Existente', N'')", nuevoId());

        migrador.migrar();

        assertFalse(existe("historial_migraciones"), "no crea la suya si ya hay una de Flyway");
        assertEquals(10, consultarInt("SELECT COUNT(*) FROM flyway_schema_history"));
        assertEquals(1, consultarInt("SELECT COUNT(*) FROM rol"), "no reaplicó nada");
        assertEquals(Optional.of(10), migrador.versionActual());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), migrador.versionesAplicadas());
    }

    @Test
    void conElHistorialDeFlywayUnChecksumDistintoOUnaMigracionFallidaHaceFallar() {
        migrador.limpiar();
        migrador.migrar();
        crearHistorialDeFlyway(10);
        ejecutar("DROP TABLE historial_migraciones");

        ejecutar("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '7'");
        IllegalStateException cambiado = assertThrows(IllegalStateException.class, migrador::migrar);
        assertTrue(cambiado.getMessage().contains("V7"), cambiado.getMessage());

        ejecutar("UPDATE flyway_schema_history SET checksum = checksum - 1 WHERE version = '7'");
        ejecutar("UPDATE flyway_schema_history SET success = 0 WHERE version = '9'");
        IllegalStateException fallida = assertThrows(IllegalStateException.class, migrador::migrar);
        assertTrue(fallida.getMessage().contains("V9"), fallida.getMessage());
    }

    @Test
    void unaMigracionNuevaSobreFlywayQuedaEnLaTablaDeFlyway() {
        migrador.limpiar();
        Migrador orden = con("db/migracion-orden");
        // Una base que Flyway dejó en V2 (con su tabla) y a la que ahora le llegan V9 y V10.
        ejecutar("CREATE TABLE orden (seq INT IDENTITY(1,1) PRIMARY KEY, n INT NOT NULL)");
        ejecutar("INSERT INTO orden (n) VALUES (2)");
        crearTablaDeFlyway();
        insertarEnFlyway(1, 2, "crea orden", "V2__crea_orden.sql",
                Migrador.checksum(contenido("db/migracion-orden/V2__crea_orden.sql")));

        orden.migrar();

        assertEquals(List.of("2", "9", "10"), columna("SELECT CAST(n AS NVARCHAR(10)) FROM orden ORDER BY seq"));
        List<Map<String, Object>> historial = filas("SELECT installed_rank, version, description, type, script, "
                + "success FROM flyway_schema_history ORDER BY installed_rank");
        assertEquals(3, historial.size());
        assertEquals(List.of(1, 2, 3), historial.stream().map(f -> f.get("installed_rank")).toList());
        assertEquals(List.of("2", "9", "10"), historial.stream().map(f -> f.get("version")).toList());
        assertEquals("nueve", historial.get(1).get("description"));
        assertEquals("SQL", historial.get(2).get("type"));
        assertEquals("V10__diez.sql", historial.get(2).get("script"));
        assertTrue(historial.stream().allMatch(f -> Boolean.TRUE.equals(f.get("success"))));
        assertFalse(existe("historial_migraciones"));
    }

    @Test
    void migrarHastaAplicaSoloLasPendientesDeEsaVersionYMigrarLasDemas() {
        migrador.limpiar();

        migrador.migrarHasta(6);

        assertEquals(Optional.of(6), migrador.versionActual());
        assertFalse(existe("tr_cita_sin_solape"));
        assertEquals(List.of(1, 2, 3, 4, 5, 6), migrador.versionesAplicadas());

        migrador.migrar();

        assertEquals(Optional.of(10), migrador.versionActual());
        assertTrue(existe("tr_cita_sin_solape"));
    }

    // ---------------------------------------------------------------- Mecánica con migraciones de prueba

    @Test
    void ordenaPorNumeroDeVersionYNoPorNombreDelArchivo() {
        migrador.limpiar();

        con("db/migracion-orden").migrar();

        // El índice lista V10, V2, V9; el orden de aplicación es 2, 9, 10 (por texto V10 iría antes que V2).
        assertEquals(List.of("2", "9", "10"), columna("SELECT CAST(n AS NVARCHAR(10)) FROM orden ORDER BY seq"));
    }

    @Test
    void separaPorLineasGoYUnaMigracionQueFallaNoDejaNadaDeSiMisma() {
        migrador.limpiar();
        Migrador prueba = con("db/migracion-prueba");

        IllegalStateException e = assertThrows(IllegalStateException.class, prueba::migrar);

        assertTrue(e.getMessage().contains("V2"), e.getMessage());
        assertTrue(e.getMessage().contains("Falla a propósito"), e.getMessage());
        assertEquals(50001, codigoSql(e));
        // V1 (con sus lotes separados por «go» y «  Go  », y un CREATE VIEW que exige lote propio) quedó aplicada…
        assertEquals(Optional.of(1), prueba.versionActual());
        assertEquals("año ñandú 😀", consultarTexto("SELECT texto FROM prueba_uno WHERE id = 1"));
        assertTrue(existe("vista_uno"));
        // …y V2 no dejó nada: ni su tabla, ni su fila, ni su registro.
        assertFalse(existe("prueba_dos"));
        assertEquals(1, consultarInt("SELECT COUNT(*) FROM prueba_uno"));
        assertEquals(1, consultarInt("SELECT COUNT(*) FROM historial_migraciones"));
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM historial_migraciones WHERE success = 0"));
        assertEquals(pool.disponibles(), 10, "la conexión de la migración volvió al pool");

        // Reintentar da el mismo error limpio (no «tabla ya existe» por un resto de la anterior).
        IllegalStateException otra = assertThrows(IllegalStateException.class, prueba::migrar);
        assertEquals(50001, codigoSql(otra));
    }

    /**
     * Un {@link Error} de Java (memoria, pila, una aserción del driver) a mitad de una migración no es una
     * excepción del migrador, pero tampoco puede dejar lotes confirmados sin su registro: pasar a confirmación
     * automática con la transacción abierta la confirmaría. Se inyecta el fallo tras el primer lote de V1, que ya
     * creó su tabla.
     */
    @Test
    void unErrorDeJavaATresMediasNoDejaNadaAplicadoNiConfirmado() {
        migrador.limpiar();
        Migrador prueba = con("db/migracion-prueba").conGancho((version, lote) -> {
            if (version == 1 && lote == 0) {
                throw new AssertionError("fallo grave simulado");
            }
        });

        AssertionError e = assertThrows(AssertionError.class, prueba::migrar);

        assertEquals("fallo grave simulado", e.getMessage(), "el Error sale tal cual");
        assertFalse(existe("prueba_uno"), "la tabla del primer lote no quedó confirmada");
        assertEquals(Optional.empty(), prueba.versionActual());
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM historial_migraciones"));
        assertEquals(10, pool.disponibles(), "la conexión volvió al pool");

        // Y la base sigue sirviendo: sin el fallo, la misma migración se aplica (V2 falla a propósito, V1 queda).
        IllegalStateException v2 = assertThrows(IllegalStateException.class, con("db/migracion-prueba")::migrar);
        assertEquals(50001, codigoSql(v2));
        assertEquals(Optional.of(1), con("db/migracion-prueba").versionActual());
    }

    @Test
    void unaExcepcionInesperadaATresMediasSeEnvuelveYNoDejaNada() {
        migrador.limpiar();
        Migrador prueba = con("db/migracion-prueba").conGancho((version, lote) -> {
            if (version == 1 && lote == 1) {
                throw new IllegalArgumentException("fallo simulado");
            }
        });

        IllegalStateException e = assertThrows(IllegalStateException.class, prueba::migrar);

        assertTrue(e.getMessage().contains("V1"), e.getMessage());
        assertFalse(existe("prueba_uno"));
        assertEquals(Optional.empty(), prueba.versionActual());
        assertEquals(10, pool.disponibles());
    }

    @Test
    void dosMigradoresALaVezSobreUnaBaseLimpiaNoDuplicanNiFallan() throws Exception {
        migrador.limpiar();
        CountDownLatch salida = new CountDownLatch(2);
        ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<?>> futuros = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futuros.add(hilos.submit(() -> {
                    salida.countDown();
                    salida.await(5, TimeUnit.SECONDS);
                    new Migrador(pool).migrar();
                    return null;
                }));
            }
            for (Future<?> f : futuros) {
                f.get(120, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertEquals(10, consultarInt("SELECT COUNT(*) FROM historial_migraciones"));
        assertEquals(Optional.of(10), migrador.versionActual());
    }

    // ---------------------------------------------------------------- limpiar

    @Test
    void limpiarBorraTodosLosObjetosDeUsuarioYElHistorial() {
        migrador.limpiar();
        migrador.migrar();
        assertTrue(objetosDeUsuario() > 10);

        migrador.limpiar();

        assertEquals(0, objetosDeUsuario());
        assertEquals(Optional.empty(), migrador.versionActual());
        // Y vuelve a migrar desde cero.
        migrador.migrar();
        assertEquals(Optional.of(10), migrador.versionActual());
    }

    @Test
    void limpiarTambienBorraLaTablaDeHistorialDeFlyway() {
        migrador.limpiar();
        crearTablaDeFlyway();

        migrador.limpiar();

        assertFalse(existe("flyway_schema_history"));
    }

    @Test
    void limpiarSeNiegaSobreUnaBaseQueNoEsDePruebasYNoTocaNada() {
        // Misma instancia y login, pero conectada a master: la guarda debe negarse antes de borrar nada.
        String url = System.getenv("DATEMANAGER_TEST_DB_URL").replaceAll("(?i)databaseName=[^;]*", "databaseName=master");
        assumeTrue(url.toLowerCase().contains("databasename=master"), "la URL de pruebas no lleva databaseName");
        Pool enMaster = new Pool(url, System.getenv("DATEMANAGER_TEST_DB_USUARIO"),
                System.getenv("DATEMANAGER_TEST_DB_CLAVE"), 1, Duration.ofSeconds(5));
        try {
            try {
                enMaster.devolver(enMaster.prestar());
            } catch (IllegalStateException e) {
                assumeTrue(false, "el login de pruebas no puede entrar en master");
            }

            IllegalStateException e = assertThrows(IllegalStateException.class, () -> new Migrador(enMaster).limpiar());

            assertTrue(e.getMessage().contains("master"), e.getMessage());
        } finally {
            enMaster.cerrar();
        }
    }
}
