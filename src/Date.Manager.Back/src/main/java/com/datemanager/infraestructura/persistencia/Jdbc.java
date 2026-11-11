package com.datemanager.infraestructura.persistencia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * Ayudante JDBC de los repositorios: trabaja con la conexión de la {@link Transaccion} del hilo (los repositorios solo
 * se usan dentro de {@code escribir}/{@code leer}) y envuelve la {@link SQLException} en {@link ErrorSql}.
 * Los {@link UUID} viajan como texto (columnas {@code uniqueidentifier}) y los textos como NVARCHAR.
 */
final class Jdbc {

    private Jdbc() {
    }

    @FunctionalInterface
    interface ConConexion<T> {
        T aplicar(Connection conexion) throws SQLException;
    }

    static <T> T con(Transaccion transaccion, ConConexion<T> trabajo) {
        Connection conexion = transaccion.conexion();
        try {
            return trabajo.aplicar(conexion);
        } catch (SQLException e) {
            throw new ErrorSql(e);
        }
    }

    /** {@link UUID} con {@code setString}, {@link String} con {@code setNString}, el resto con {@code setObject}. */
    static void enlazar(PreparedStatement sentencia, Object... parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            Object p = parametros[i];
            if (p instanceof UUID id) {
                sentencia.setString(i + 1, id.toString());
            } else if (p instanceof String texto) {
                sentencia.setNString(i + 1, texto);
            } else {
                sentencia.setObject(i + 1, p);
            }
        }
    }

    static UUID uuid(ResultSet resultado, String columna) throws SQLException {
        return UUID.fromString(resultado.getString(columna));
    }

    /** Tantos marcadores {@code ?} como {@code cantidad}, separados por comas (para {@code IN (...)}). */
    static String marcadores(int cantidad) {
        return String.join(", ", Collections.nCopies(cantidad, "?"));
    }

    /** Los parámetros {@code prefijo} seguidos de los elementos de {@code coleccion}. */
    static Object[] conColeccion(Collection<UUID> coleccion, Object... prefijo) {
        Object[] todos = new Object[prefijo.length + coleccion.size()];
        System.arraycopy(prefijo, 0, todos, 0, prefijo.length);
        int i = prefijo.length;
        for (UUID id : coleccion) {
            todos[i++] = id;
        }
        return todos;
    }
}
