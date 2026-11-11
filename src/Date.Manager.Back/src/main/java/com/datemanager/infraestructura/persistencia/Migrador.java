package com.datemanager.infraestructura.persistencia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Aplica las migraciones SQL de {@code db/migration} (del classpath) a la base, en orden de versión y cada una en su
 * propia transacción: si una falla no queda nada de ella (SQL Server revierte también el DDL) y el error sube con su
 * causa. Las migraciones se listan en {@code db/migration/indice.txt}, porque un jar no se puede recorrer;
 * {@code IndiceMigracionesTest} comprueba que la lista y los archivos coinciden.
 *
 * <p>Cada archivo {@code V<n>__<descripcion>.sql} se separa en lotes por las líneas que son solo {@code GO} (sin
 * distinguir mayúsculas), como hacía Flyway. Se registra versión, descripción y el {@code CRC32} del contenido,
 * calculado igual que Flyway: así una base que Flyway ya migró se reconoce sin reaplicar nada.
 *
 * <p>Historial: si existe {@code flyway_schema_history} se usa tal cual (columnas {@code version}, {@code description},
 * {@code checksum}, {@code success}, y las demás de Flyway al añadir filas); si no, se usa {@code historial_migraciones}
 * y se crea si falta. Si el checksum de una migración ya aplicada cambió, o hay una marcada como fallida, se lanza
 * {@link IllegalStateException}: la aplicación no arranca sobre un esquema que no sabe cuál es. Dos migradores a la vez
 * (dos instancias arrancando) se turnan con un bloqueo de aplicación de SQL Server.
 */
public final class Migrador {

    private static final Logger LOG = Logger.getLogger(Migrador.class.getName());

    private static final String CARPETA_POR_DEFECTO = "db/migration";
    private static final String TABLA_PROPIA = "historial_migraciones";
    private static final String TABLA_DE_FLYWAY = "flyway_schema_history";
    private static final String RECURSO_BLOQUEO = "datemanager.migraciones";
    private static final int ESPERA_DEL_BLOQUEO_MS = 120_000;
    private static final String SUFIJO_BASE_DE_PRUEBA = "test";
    /** Marca de orden de bytes que algunos editores ponen al inicio de un archivo UTF-8. */
    private static final char BOM = (char) 0xFEFF;
    private static final Pattern LINEA_GO = Pattern.compile("^\\s*GO\\s*$", Pattern.CASE_INSENSITIVE);

    private final Pool pool;
    private final String carpeta;

    public Migrador(Pool pool) {
        this(pool, CARPETA_POR_DEFECTO);
    }

    /** Con otra carpeta del classpath (para pruebas del propio migrador). */
    Migrador(Pool pool, String carpeta) {
        this.pool = pool;
        this.carpeta = carpeta;
    }

    // ---------------------------------------------------------------- API

    /** Aplica todas las migraciones pendientes. */
    public void migrar() {
        migrarHasta(Integer.MAX_VALUE);
    }

    /** Aplica las pendientes de versión menor o igual a {@code version} (para probar migraciones intermedias). */
    public void migrarHasta(int version) {
        List<Migracion> todas = cargar();
        Connection conexion = pool.prestar();
        try {
            tomarBloqueo(conexion);
            try {
                aplicar(conexion, todas, version);
            } finally {
                soltarBloqueo(conexion);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo migrar la base de datos (código " + e.getErrorCode() + ").", e);
        } finally {
            pool.devolver(conexion);
        }
    }

    /** La versión más alta aplicada con éxito, o vacío si la base no tiene migraciones. */
    public Optional<Integer> versionActual() {
        List<Integer> aplicadas = versionesAplicadas();
        return aplicadas.isEmpty() ? Optional.empty() : Optional.of(aplicadas.get(aplicadas.size() - 1));
    }

    /** Las versiones aplicadas con éxito, de menor a mayor. */
    public List<Integer> versionesAplicadas() {
        Connection conexion = pool.prestar();
        try {
            Historial historial = historialExistente(conexion);
            if (historial == null) {
                return List.of();
            }
            return leerHistorial(conexion, historial).entrySet().stream().filter(e -> e.getValue().exito())
                    .map(Map.Entry::getKey).toList();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer el historial de migraciones (código " + e.getErrorCode()
                    + ").", e);
        } finally {
            pool.devolver(conexion);
        }
    }

    /** La versión más alta que traen los archivos de migración. */
    public int ultimaVersionDisponible() {
        return cargar().stream().mapToInt(Migracion::version).max().orElse(0);
    }

    /**
     * Solo para pruebas: borra todos los objetos de usuario (claves foráneas, disparadores, vistas, rutinas, tablas,
     * secuencias) y con ellos el historial. Antes comprueba que la base conectada tenga nombre de pruebas; si no,
     * lanza {@link IllegalStateException} sin borrar nada (el mismo login es dueño de la base real y de la de pruebas).
     */
    public void limpiar() {
        Connection conexion = pool.prestar();
        try {
            exigirBaseDePrueba(consultarTexto(conexion, "SELECT DB_NAME()"));
            try (Statement s = conexion.createStatement()) {
                for (String sentencia : sentenciasDeLimpieza(conexion)) {
                    s.execute(sentencia);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo limpiar la base de datos (código " + e.getErrorCode() + ").", e);
        } finally {
            pool.devolver(conexion);
        }
    }

    /** Lanza {@link IllegalStateException} salvo que {@code nombre} termine en «Test» (sin distinguir mayúsculas). */
    static void exigirBaseDePrueba(String nombre) {
        if (nombre == null || !nombre.strip().toLowerCase(Locale.ROOT).endsWith(SUFIJO_BASE_DE_PRUEBA)) {
            throw new IllegalStateException("La limpieza se rechaza: la base conectada es «" + nombre
                    + "» y su nombre no termina en «Test». Revisa DATEMANAGER_TEST_DB_URL: las pruebas "
                    + "solo pueden borrar una base de pruebas.");
        }
    }

    // ---------------------------------------------------------------- Archivos de migración

    /** Una migración leída del classpath, ya separada en lotes. */
    private record Migracion(int version, String descripcion, String archivo, List<String> lotes, int checksum) {
    }

    /** {@code V<versión>__<descripción>.sql}: la versión es un entero y los guiones bajos de la descripción son espacios. */
    record NombreMigracion(int version, String descripcion, String archivo) {

        private static final Pattern FORMA = Pattern.compile("^V(\\d+)__(.+)\\.sql$");

        static NombreMigracion de(String archivo) {
            Matcher m = FORMA.matcher(archivo);
            if (!m.matches()) {
                throw new IllegalStateException("«" + archivo + "» no es un nombre de migración válido "
                        + "(se espera V<número>__<descripción>.sql).");
            }
            try {
                return new NombreMigracion(Integer.parseInt(m.group(1)), m.group(2).replace('_', ' '), archivo);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("«" + archivo + "» tiene un número de versión demasiado grande.");
            }
        }
    }

    /** Los nombres de archivo del índice: uno por línea; las líneas en blanco y las que empiezan por {@code #} se ignoran. */
    static List<String> leerIndice(String texto) {
        List<String> nombres = new ArrayList<>();
        for (String linea : texto.split("\\R")) {
            String nombre = linea.strip();
            if (!nombre.isEmpty() && !nombre.startsWith("#")) {
                nombres.add(nombre);
            }
        }
        return nombres;
    }

    /**
     * CRC32 del contenido igual que Flyway: línea a línea (sin contar los saltos), sin el BOM inicial y en UTF-8, y
     * como entero con signo.
     */
    static int checksum(String contenido) {
        CRC32 crc = new CRC32();
        try (BufferedReader lector = new BufferedReader(new StringReader(contenido))) {
            String linea = lector.readLine();
            if (linea != null) {
                linea = quitarBom(linea);
                do {
                    crc.update(linea.getBytes(StandardCharsets.UTF_8));
                } while ((linea = lector.readLine()) != null);
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo calcular el checksum.", e);
        }
        return (int) crc.getValue();
    }

    private static String quitarBom(String texto) {
        return !texto.isEmpty() && texto.charAt(0) == BOM ? texto.substring(1) : texto;
    }

    /** Parte el script en lotes por las líneas que son solo GO; los lotes en blanco se descartan. */
    static List<String> separarLotes(String contenido) {
        List<String> lotes = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String linea : quitarBom(contenido).split("\\R", -1)) {
            if (LINEA_GO.matcher(linea).matches()) {
                agregarLote(lotes, actual);
            } else {
                actual.append(linea).append('\n');
            }
        }
        agregarLote(lotes, actual);
        return lotes;
    }

    private static void agregarLote(List<String> lotes, StringBuilder lote) {
        if (!lote.toString().isBlank()) {
            lotes.add(lote.toString());
        }
        lote.setLength(0);
    }

    private List<Migracion> cargar() {
        List<Migracion> migraciones = new ArrayList<>();
        Set<Integer> versiones = new HashSet<>();
        for (String archivo : leerIndice(leerRecurso(carpeta + "/indice.txt"))) {
            NombreMigracion nombre = NombreMigracion.de(archivo);
            if (!versiones.add(nombre.version())) {
                throw new IllegalStateException("La versión " + nombre.version() + " está en más de una migración.");
            }
            String contenido = leerRecurso(carpeta + "/" + archivo);
            migraciones.add(new Migracion(nombre.version(), nombre.descripcion(), archivo, separarLotes(contenido),
                    checksum(contenido)));
        }
        migraciones.sort((a, b) -> Integer.compare(a.version(), b.version()));
        return migraciones;
    }

    private static String leerRecurso(String ruta) {
        try (InputStream in = Migrador.class.getClassLoader().getResourceAsStream(ruta)) {
            if (in == null) {
                throw new IllegalStateException("Falta el recurso de migración " + ruta + ".");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el recurso de migración " + ruta + ".", e);
        }
    }

    // ---------------------------------------------------------------- Historial

    private enum Historial {
        PROPIO(TABLA_PROPIA), FLYWAY(TABLA_DE_FLYWAY);

        final String tabla;

        Historial(String tabla) {
            this.tabla = tabla;
        }
    }

    private record Aplicada(Integer checksum, boolean exito) {
    }

    /** La tabla de historial que ya hay (la de Flyway tiene prioridad), o {@code null} si no hay ninguna. */
    private static Historial historialExistente(Connection conexion) throws SQLException {
        if (existeTabla(conexion, TABLA_DE_FLYWAY)) {
            return Historial.FLYWAY;
        }
        return existeTabla(conexion, TABLA_PROPIA) ? Historial.PROPIO : null;
    }

    private static boolean existeTabla(Connection conexion, String tabla) throws SQLException {
        return consultarTexto(conexion, "SELECT CASE WHEN OBJECT_ID(N'" + tabla + "', N'U') IS NULL THEN '0' ELSE '1' END")
                .equals("1");
    }

    private static Historial historialOCrear(Connection conexion) throws SQLException {
        Historial existente = historialExistente(conexion);
        if (existente != null) {
            return existente;
        }
        try (Statement s = conexion.createStatement()) {
            s.execute("CREATE TABLE " + TABLA_PROPIA + " (version INT NOT NULL CONSTRAINT pk_" + TABLA_PROPIA
                    + " PRIMARY KEY, description NVARCHAR(200) NOT NULL, checksum INT NOT NULL, "
                    + "installed_on DATETIME2(3) NOT NULL, success BIT NOT NULL)");
        }
        return Historial.PROPIO;
    }

    /** Versión a datos de lo aplicado, de menor a mayor; ignora las filas sin versión entera (no son nuestras). */
    private static TreeMap<Integer, Aplicada> leerHistorial(Connection conexion, Historial historial)
            throws SQLException {
        TreeMap<Integer, Aplicada> aplicadas = new TreeMap<>();
        try (Statement s = conexion.createStatement();
                ResultSet filas = s.executeQuery("SELECT version, checksum, success FROM " + historial.tabla)) {
            while (filas.next()) {
                String texto = filas.getString(1);
                if (texto == null) {
                    continue;
                }
                int version;
                try {
                    version = Integer.parseInt(texto.trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                int checksum = filas.getInt(2);
                aplicadas.put(version, new Aplicada(filas.wasNull() ? null : checksum, filas.getBoolean(3)));
            }
        }
        return aplicadas;
    }

    private static void registrar(Connection conexion, Historial historial, Migracion m, long milisegundos)
            throws SQLException {
        String sql = historial == Historial.PROPIO
                ? "INSERT INTO " + TABLA_PROPIA + " (version, description, checksum, installed_on, success) "
                        + "VALUES (?, ?, ?, SYSUTCDATETIME(), 1)"
                : "INSERT INTO " + TABLA_DE_FLYWAY + " (installed_rank, version, description, type, script, "
                        + "checksum, installed_by, installed_on, execution_time, success) VALUES ((SELECT "
                        + "ISNULL(MAX(installed_rank), 0) + 1 FROM " + TABLA_DE_FLYWAY + "), ?, ?, 'SQL', ?, ?, "
                        + "SUSER_SNAME(), GETDATE(), ?, 1)";
        try (PreparedStatement s = conexion.prepareStatement(sql)) {
            int i = 1;
            if (historial == Historial.PROPIO) {
                s.setInt(i++, m.version());
                s.setNString(i++, m.descripcion());
                s.setInt(i, m.checksum());
            } else {
                s.setNString(i++, String.valueOf(m.version()));
                s.setNString(i++, m.descripcion());
                s.setNString(i++, m.archivo());
                s.setInt(i++, m.checksum());
                s.setInt(i, (int) Math.min(milisegundos, Integer.MAX_VALUE));
            }
            s.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- Aplicación

    private void aplicar(Connection conexion, List<Migracion> todas, int hasta) throws SQLException {
        Historial historial = historialOCrear(conexion);
        TreeMap<Integer, Aplicada> aplicadas = leerHistorial(conexion, historial);
        validar(todas, aplicadas);

        int mayorAplicada = aplicadas.isEmpty() ? Integer.MIN_VALUE : aplicadas.lastKey();
        for (Migracion m : todas) {
            if (aplicadas.containsKey(m.version()) || m.version() > hasta) {
                continue;
            }
            if (m.version() < mayorAplicada) {
                throw new IllegalStateException("La migración V" + m.version() + " (" + m.descripcion()
                        + ") está pendiente pero la base ya tiene aplicada la V" + mayorAplicada
                        + ": no se aplican migraciones fuera de orden.");
            }
            ejecutar(conexion, historial, m);
            mayorAplicada = m.version();
        }
    }

    /** Lo ya aplicado debe ser exitoso y no haber cambiado en disco. Las versiones aplicadas sin archivo se ignoran. */
    private static void validar(List<Migracion> todas, Map<Integer, Aplicada> aplicadas) {
        for (Map.Entry<Integer, Aplicada> e : aplicadas.entrySet()) {
            if (!e.getValue().exito()) {
                throw new IllegalStateException("El historial de migraciones marca la V" + e.getKey()
                        + " como fallida: hay que revisar la base a mano antes de migrar.");
            }
        }
        for (Migracion m : todas) {
            Aplicada aplicada = aplicadas.get(m.version());
            if (aplicada != null && aplicada.checksum() != null && aplicada.checksum() != m.checksum()) {
                throw new IllegalStateException("La migración V" + m.version() + " (" + m.archivo()
                        + ") ya está aplicada y su checksum cambió (registrado " + aplicada.checksum() + ", actual "
                        + m.checksum() + "): una migración aplicada no se edita, se añade otra.");
            }
        }
    }

    private static void ejecutar(Connection conexion, Historial historial, Migracion m) throws SQLException {
        long inicio = System.nanoTime();
        conexion.setAutoCommit(false);
        try {
            for (String lote : m.lotes()) {
                ejecutarLote(conexion, lote);
            }
            registrar(conexion, historial, m, (System.nanoTime() - inicio) / 1_000_000);
            conexion.commit();
            LOG.log(Level.INFO, "Migración V{0} aplicada: {1}", new Object[] {m.version(), m.descripcion()});
        } catch (SQLException | RuntimeException e) {
            try {
                conexion.rollback();
            } catch (SQLException | RuntimeException r) {
                e.addSuppressed(r);
            }
            // El mensaje de la base (p. ej. el de la auditoría de V7) dice qué corregir; la causa conserva el código.
            String detalle = e instanceof SQLException ? e.getMessage() : e.getClass().getSimpleName();
            throw new IllegalStateException("La migración V" + m.version() + " (" + m.descripcion() + ") falló y se "
                    + "revirtió: " + detalle, e);
        } finally {
            try {
                conexion.setAutoCommit(true);
            } catch (SQLException e) {
                // La conexión está rota; el pool la descartará al devolverla.
            }
        }
    }

    /** Ejecuta un lote y recorre todos sus resultados: un error después de un conteo de filas solo sale al recorrerlos. */
    private static void ejecutarLote(Connection conexion, String lote) throws SQLException {
        try (Statement s = conexion.createStatement()) {
            boolean hayResultado = s.execute(lote);
            while (true) {
                if (hayResultado) {
                    try (ResultSet filas = s.getResultSet()) {
                        while (filas.next()) {
                            // Se descartan; solo importa que el servidor termine el lote sin error.
                        }
                    }
                } else if (s.getUpdateCount() == -1) {
                    break;
                }
                hayResultado = s.getMoreResults();
            }
        }
    }

    // ---------------------------------------------------------------- Bloqueo entre instancias

    private static void tomarBloqueo(Connection conexion) throws SQLException {
        String resultado = consultarTexto(conexion, "DECLARE @r INT; EXEC @r = sp_getapplock @Resource = N'"
                + RECURSO_BLOQUEO + "', @LockMode = N'Exclusive', @LockOwner = N'Session', @LockTimeout = "
                + ESPERA_DEL_BLOQUEO_MS + "; SELECT CAST(@r AS NVARCHAR(10));");
        if (Integer.parseInt(resultado) < 0) {
            throw new IllegalStateException("Otra instancia está migrando la base y no terminó a tiempo.");
        }
    }

    private static void soltarBloqueo(Connection conexion) {
        try (Statement s = conexion.createStatement()) {
            s.execute("EXEC sp_releaseapplock @Resource = N'" + RECURSO_BLOQUEO + "', @LockOwner = N'Session'");
        } catch (SQLException | RuntimeException e) {
            // Si la conexión murió, el servidor ya soltó el bloqueo con la sesión.
        }
    }

    // ---------------------------------------------------------------- Limpieza

    private static List<String> sentenciasDeLimpieza(Connection conexion) throws SQLException {
        List<String> sentencias = new ArrayList<>();
        agregar(conexion, sentencias, "SELECT 'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(parent_object_id)) + '.' "
                + "+ QUOTENAME(OBJECT_NAME(parent_object_id)) + ' DROP CONSTRAINT ' + QUOTENAME(name) "
                + "FROM sys.foreign_keys");
        agregar(conexion, sentencias, "SELECT 'DROP TRIGGER ' + QUOTENAME(name) + ' ON DATABASE' FROM sys.triggers "
                + "WHERE parent_class = 0 AND is_ms_shipped = 0");
        // Orden: lo que depende de las tablas primero (vistas, rutinas), luego tablas, luego el resto.
        for (String[] tipo : new String[][] {{"'V'", "VIEW"}, {"'P', 'PC'", "PROCEDURE"},
                {"'FN', 'IF', 'TF', 'FS', 'FT'", "FUNCTION"}, {"'U'", "TABLE"}, {"'SO'", "SEQUENCE"},
                {"'SN'", "SYNONYM"}}) {
            agregar(conexion, sentencias, "SELECT 'DROP " + tipo[1] + " ' + QUOTENAME(SCHEMA_NAME(schema_id)) + '.' "
                    + "+ QUOTENAME(name) FROM sys.objects WHERE is_ms_shipped = 0 AND type IN (" + tipo[0] + ")");
        }
        return sentencias;
    }

    private static void agregar(Connection conexion, List<String> sentencias, String consulta) throws SQLException {
        try (Statement s = conexion.createStatement(); ResultSet filas = s.executeQuery(consulta)) {
            while (filas.next()) {
                sentencias.add(filas.getString(1));
            }
        }
    }

    private static String consultarTexto(Connection conexion, String sql) throws SQLException {
        try (Statement s = conexion.createStatement(); ResultSet filas = s.executeQuery(sql)) {
            return filas.next() ? filas.getString(1) : null;
        }
    }
}
