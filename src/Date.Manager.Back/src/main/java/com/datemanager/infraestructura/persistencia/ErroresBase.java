package com.datemanager.infraestructura.persistencia;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Reconoce los errores propios de la base. Los disparadores rechazan una escritura con {@code THROW} y un número de
 * error de 50000 a 50099 (p. ej. 50010, citas solapadas); Spring no los reconoce como violación de integridad y
 * llegan envueltos en otra excepción ({@code UncategorizedSQLException}, {@code JpaSystemException}...).
 */
public final class ErroresBase {

    private static final int PRIMER_ERROR_DE_DISPARADOR = 50000;
    private static final int ULTIMO_ERROR_DE_DISPARADOR = 50099;

    private ErroresBase() {
    }

    /** Si en la cadena de causas hay una {@link SQLException} con un número de error de disparador (50000–50099). */
    public static boolean esViolacionDeDisparador(Throwable e) {
        Set<Throwable> vistas = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = e; t != null && vistas.add(t); t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() >= PRIMER_ERROR_DE_DISPARADOR
                    && sql.getErrorCode() <= ULTIMO_ERROR_DE_DISPARADOR) {
                return true;
            }
        }
        return false;
    }
}
