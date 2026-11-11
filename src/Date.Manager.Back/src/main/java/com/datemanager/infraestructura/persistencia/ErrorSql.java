package com.datemanager.infraestructura.persistencia;

import java.sql.SQLException;

/**
 * Una {@link SQLException} sin marcar, para que el código que usa JDBC dentro de una {@link Transaccion} (un
 * {@code Supplier}) no tenga que declararla. Su mensaje solo lleva el código de error y el estado SQL: el texto
 * original de la base (que puede traer valores de las columnas) queda en la causa y no se copia aquí.
 * {@link ErroresBase} busca el número de error en la cadena de causas.
 */
public class ErrorSql extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ErrorSql(SQLException causa) {
        super("Falló una operación de la base de datos (código " + causa.getErrorCode() + ", estado "
                + causa.getSQLState() + ").", causa);
    }

    public ErrorSql(String mensaje, SQLException causa) {
        super(mensaje, causa);
    }
}
