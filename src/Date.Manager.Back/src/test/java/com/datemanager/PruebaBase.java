package com.datemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.datemanager.infraestructura.persistencia.ErrorSql;
import com.datemanager.infraestructura.persistencia.Migrador;
import com.datemanager.infraestructura.persistencia.Pool;
import com.datemanager.infraestructura.persistencia.Transaccion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base de las pruebas con la base real ({@code DateManagerTest}, variables {@code DATEMANAGER_TEST_DB_*}). Una vez por
 * JVM crea el {@link Pool}, la {@link Transaccion} y el {@link Migrador}, deja la base limpia y migrada, y antes de
 * cada prueba vacía todas las tablas de datos. Trae un pequeño ayudante JDBC para no repetir código en cada prueba.
 *
 * <p>Las clases que cambian el esquema (migrador, migración de datos) deben dejarlo en la última versión al terminar.
 */
public abstract class PruebaBase {

    protected static Pool pool;
    protected static Transaccion tx;
    protected static Migrador migrador;

    /** Tablas del historial de migraciones: no son datos, no se vacían. */
    private static final String TABLAS_DE_HISTORIAL = "'flyway_schema_history', 'historial_migraciones'";

    private static final class Compartido {
        static final Pool POOL;
        static final Transaccion TX;
        static final Migrador MIGRADOR;

        static {
            POOL = new Pool(variable("DATEMANAGER_TEST_DB_URL"), variable("DATEMANAGER_TEST_DB_USUARIO"),
                    variable("DATEMANAGER_TEST_DB_CLAVE"), 10);
            TX = new Transaccion(POOL);
            MIGRADOR = new Migrador(POOL);
            Runtime.getRuntime().addShutdownHook(new Thread(POOL::cerrar));
            MIGRADOR.limpiar();
            MIGRADOR.migrar();
        }

        private static String variable(String nombre) {
            String valor = System.getenv(nombre);
            if (valor == null || valor.isBlank()) {
                throw new IllegalStateException("Falta la variable " + nombre + ": las pruebas con base la necesitan "
                        + "(ver README, sección «Cargar .env.local»).");
            }
            return valor;
        }
    }

    @BeforeAll
    static void iniciarBase() {
        pool = Compartido.POOL;
        tx = Compartido.TX;
        migrador = Compartido.MIGRADOR;
    }

    @BeforeEach
    void prepararPrueba() {
        limpiarDatos();
    }

    /**
     * Vacía todas las tablas de usuario (todas menos el historial de migraciones) sin depender de una lista fija ni
     * del orden de las claves foráneas: desactiva las restricciones, borra y las vuelve a validar.
     */
    protected void limpiarDatos() {
        List<String> tablas = columna("SELECT QUOTENAME(TABLE_SCHEMA) + '.' + QUOTENAME(TABLE_NAME) "
                + "FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' "
                + "AND TABLE_NAME NOT IN (" + TABLAS_DE_HISTORIAL + ")");
        for (String tabla : tablas) {
            ejecutar("ALTER TABLE " + tabla + " NOCHECK CONSTRAINT ALL");
        }
        for (String tabla : tablas) {
            ejecutar("DELETE FROM " + tabla);
        }
        for (String tabla : tablas) {
            ejecutar("ALTER TABLE " + tabla + " WITH CHECK CHECK CONSTRAINT ALL");
        }
    }

    // ---------------------------------------------------------------- Ayudante JDBC

    /** Un trabajo con la conexión; la {@link SQLException} que lance se envuelve en {@link ErrorSql}. */
    @FunctionalInterface
    protected interface ConConexion<T> {
        T aplicar(Connection conexion) throws SQLException;
    }

    /**
     * Ejecuta con la conexión de la transacción en curso si la hay y, si no, con una prestada del pool (en
     * confirmación automática, que se devuelve siempre).
     */
    protected static <T> T conConexion(ConConexion<T> trabajo) {
        if (tx.activa()) {
            return aplicarEnvolviendo(trabajo, tx.conexion());
        }
        Connection conexion = pool.prestar();
        try {
            return aplicarEnvolviendo(trabajo, conexion);
        } finally {
            pool.devolver(conexion);
        }
    }

    private static <T> T aplicarEnvolviendo(ConConexion<T> trabajo, Connection conexion) {
        try {
            return trabajo.aplicar(conexion);
        } catch (SQLException e) {
            throw new ErrorSql(e);
        }
    }

    /** Textos como NVARCHAR, el resto con {@code setObject} (UUID incluido). */
    protected static void enlazar(PreparedStatement sentencia, Object... parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            Object p = parametros[i];
            if (p instanceof String texto) {
                sentencia.setNString(i + 1, texto);
            } else {
                sentencia.setObject(i + 1, p);
            }
        }
    }

    protected static int ejecutar(String sql, Object... parametros) {
        return conConexion(c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                enlazar(s, parametros);
                return s.executeUpdate();
            }
        });
    }

    protected static long consultarLong(String sql, Object... parametros) {
        return conConexion(c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                enlazar(s, parametros);
                try (ResultSet r = s.executeQuery()) {
                    if (!r.next()) {
                        throw new IllegalStateException("La consulta no devolvió filas.");
                    }
                    return r.getLong(1);
                }
            }
        });
    }

    protected static int consultarInt(String sql, Object... parametros) {
        return Math.toIntExact(consultarLong(sql, parametros));
    }

    protected static String consultarTexto(String sql, Object... parametros) {
        return conConexion(c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                enlazar(s, parametros);
                try (ResultSet r = s.executeQuery()) {
                    return r.next() ? r.getNString(1) : null;
                }
            }
        });
    }

    /** La primera columna de todas las filas, como texto. */
    protected static List<String> columna(String sql, Object... parametros) {
        return conConexion(c -> {
            List<String> valores = new ArrayList<>();
            try (PreparedStatement s = c.prepareStatement(sql)) {
                enlazar(s, parametros);
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        valores.add(r.getNString(1));
                    }
                }
            }
            return valores;
        });
    }

    /** Todas las filas, cada una como mapa ordenado de columna a valor. */
    protected static List<Map<String, Object>> filas(String sql, Object... parametros) {
        return conConexion(c -> {
            List<Map<String, Object>> filas = new ArrayList<>();
            try (PreparedStatement s = c.prepareStatement(sql)) {
                enlazar(s, parametros);
                try (ResultSet r = s.executeQuery()) {
                    ResultSetMetaData meta = r.getMetaData();
                    while (r.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            fila.put(meta.getColumnLabel(i), r.getObject(i));
                        }
                        filas.add(fila);
                    }
                }
            }
            return filas;
        });
    }

    protected static UUID nuevoId() {
        return UUID.randomUUID();
    }

    /** Código de error de SQL Server de la primera {@link SQLException} en la cadena de causas, o {@code null}. */
    protected static Integer codigoSql(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getErrorCode();
            }
        }
        return null;
    }

    /** Mensaje de la primera {@link SQLException} de la cadena de causas, o {@code null}. */
    protected static String mensajeSql(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getMessage();
            }
        }
        return null;
    }
}
