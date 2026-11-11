package com.datemanager.infraestructura.persistencia;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Reconoce los errores propios de la base mirando los números de error de SQL Server de las {@link SQLException} de
 * la cadena de causas (la excepción llega envuelta en otras: {@link ErrorSql}, {@code IllegalStateException}...):
 * <ul>
 *   <li>restricciones: 2627 (clave primaria o única), 2601 (índice único) y 547 (clave foránea o CHECK), más los de
 *       datos que la aplicación original (el traductor de Spring) también trataba como conflicto: 515 (NULL en columna
 *       no nula), 8152 y 2628 (texto demasiado largo), 8114 y 8115 (conversión o desbordamiento) y 544; y cualquier
 *       {@link SQLException} con SQLState de clase 22, 23, 27 o 44. (El driver informa el truncamiento con SQLState
 *       {@code S0001}, por eso se reconoce también por número.);</li>
 *   <li>bloqueos: 1222 (tiempo de espera agotado) y 1205 (víctima de un interbloqueo);</li>
 *   <li>disparadores: los {@code THROW} de 50000 a 50099 (p. ej. 50010, citas solapadas).</li>
 * </ul>
 */
public final class ErroresBase {

    private static final int PRIMER_ERROR_DE_DISPARADOR = 50000;
    private static final int ULTIMO_ERROR_DE_DISPARADOR = 50099;

    private static final Set<Integer> CODIGOS_DE_RESTRICCION = Set.of(2627, 2601, 547, 515, 8152, 2628, 8114, 8115, 544);

    private ErroresBase() {
    }

    /** Si en la cadena de causas hay una {@link SQLException} con un número de error de disparador (50000–50099). */
    public static boolean esViolacionDeDisparador(Throwable e) {
        return hayCodigo(e, codigo -> codigo >= PRIMER_ERROR_DE_DISPARADOR && codigo <= ULTIMO_ERROR_DE_DISPARADOR);
    }

    /**
     * Si en la cadena de causas hay una violación de integridad de datos: restricción única, de clave o de CHECK
     * (2627, 2601, 547) o cualquier {@link SQLException} con SQLState de clase 22, 23, 27 o 44 (NULL en una columna no
     * nula, texto demasiado largo...).
     */
    public static boolean esViolacionDeRestriccion(Throwable e) {
        return hayError(e, sql -> {
            int codigo = sql.getErrorCode();
            if (CODIGOS_DE_RESTRICCION.contains(codigo)) {
                return true;
            }
            String estado = sql.getSQLState();
            return estado != null && (estado.startsWith("22") || estado.startsWith("23")
                    || estado.startsWith("27") || estado.startsWith("44"));
        });
    }

    /** Si en la cadena de causas hay un fallo al tomar un bloqueo: tiempo agotado (1222) o interbloqueo (1205). */
    public static boolean esFalloDeBloqueo(Throwable e) {
        return hayCodigo(e, codigo -> codigo == 1222 || codigo == 1205);
    }

    private static boolean hayCodigo(Throwable e, IntPredicate coincide) {
        return hayError(e, sql -> coincide.test(sql.getErrorCode()));
    }

    private static boolean hayError(Throwable e, Predicate<SQLException> coincide) {
        Set<Throwable> vistas = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = e; t != null && vistas.add(t); t = t.getCause()) {
            if (t instanceof SQLException sql && coincide.test(sql)) {
                return true;
            }
        }
        return false;
    }
}
