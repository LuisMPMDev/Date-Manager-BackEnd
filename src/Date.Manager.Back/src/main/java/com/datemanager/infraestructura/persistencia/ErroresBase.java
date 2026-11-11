package com.datemanager.infraestructura.persistencia;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Reconoce los errores propios de la base mirando los números de error de SQL Server de las {@link SQLException} de
 * la cadena de causas (la excepción llega envuelta en otras: {@link ErrorSql}, {@code IllegalStateException}...):
 * <ul>
 *   <li>restricciones: 2627 (clave primaria o única), 2601 (índice único) y 547 (clave foránea o CHECK);</li>
 *   <li>bloqueos: 1222 (tiempo de espera agotado) y 1205 (víctima de un interbloqueo);</li>
 *   <li>disparadores: los {@code THROW} de 50000 a 50099 (p. ej. 50010, citas solapadas).</li>
 * </ul>
 */
public final class ErroresBase {

    private static final int PRIMER_ERROR_DE_DISPARADOR = 50000;
    private static final int ULTIMO_ERROR_DE_DISPARADOR = 50099;

    private ErroresBase() {
    }

    /** Si en la cadena de causas hay una {@link SQLException} con un número de error de disparador (50000–50099). */
    public static boolean esViolacionDeDisparador(Throwable e) {
        return hayCodigo(e, codigo -> codigo >= PRIMER_ERROR_DE_DISPARADOR && codigo <= ULTIMO_ERROR_DE_DISPARADOR);
    }

    /** Si en la cadena de causas hay una violación de restricción única, de clave o de CHECK (2627, 2601, 547). */
    public static boolean esViolacionDeRestriccion(Throwable e) {
        return hayCodigo(e, codigo -> codigo == 2627 || codigo == 2601 || codigo == 547);
    }

    /** Si en la cadena de causas hay un fallo al tomar un bloqueo: tiempo agotado (1222) o interbloqueo (1205). */
    public static boolean esFalloDeBloqueo(Throwable e) {
        return hayCodigo(e, codigo -> codigo == 1222 || codigo == 1205);
    }

    private static boolean hayCodigo(Throwable e, IntPredicate coincide) {
        Set<Throwable> vistas = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = e; t != null && vistas.add(t); t = t.getCause()) {
            if (t instanceof SQLException sql && coincide.test(sql.getErrorCode())) {
                return true;
            }
        }
        return false;
    }
}
