package com.datemanager.infraestructura.persistencia;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Transacciones sobre una conexión del {@link Pool}, una por hilo. {@link #escribir} abre la transacción, ejecuta el
 * trabajo y confirma; ante cualquier {@link RuntimeException} o {@link Error} revierte y relanza la misma excepción.
 * {@link #leer} es igual pero nunca confirma datos: al terminar revierte (no hay nada que guardar y se sueltan los
 * bloqueos). La conexión vuelve siempre al pool.
 *
 * <p>Es reentrante: una llamada anidada (en el mismo hilo) reutiliza la transacción en curso, como el
 * {@code REQUIRED} de Spring; solo la llamada más externa confirma o revierte. Una anidada de lectura dentro de una
 * escritura es normal; una escritura dentro de una lectura se rechaza ({@link IllegalStateException}), porque la
 * lectura revertiría lo escrito sin avisar.
 *
 * <p>La transacción es del hilo (también de un hilo virtual): el trabajo no debe pasar la conexión a otros hilos.
 */
public final class Transaccion {

    private record Contexto(Connection conexion, boolean soloLectura) {
    }

    private final Pool pool;
    private final ThreadLocal<Contexto> actual = new ThreadLocal<>();

    public Transaccion(Pool pool) {
        this.pool = Objects.requireNonNull(pool);
    }

    /** Ejecuta {@code trabajo} en una transacción de escritura (o en la ya abierta del hilo) y devuelve su resultado. */
    public <T> T escribir(Supplier<T> trabajo) {
        return ejecutar(trabajo, false);
    }

    public void escribir(Runnable trabajo) {
        Objects.requireNonNull(trabajo);
        ejecutar(() -> {
            trabajo.run();
            return null;
        }, false);
    }

    /** Ejecuta {@code trabajo} en una transacción de solo lectura: mismas reglas, pero termina con rollback. */
    public <T> T leer(Supplier<T> trabajo) {
        return ejecutar(trabajo, true);
    }

    /** Equivale a {@code Propagation.MANDATORY}: lanza {@link IllegalStateException} si el hilo no tiene transacción. */
    public void exigirActiva() {
        if (actual.get() == null) {
            throw new IllegalStateException("Esta operación debe ejecutarse dentro de una transacción.");
        }
    }

    /** Si el hilo tiene una transacción abierta. */
    public boolean activa() {
        return actual.get() != null;
    }

    /** La conexión de la transacción del hilo; {@link IllegalStateException} fuera de una transacción. */
    public Connection conexion() {
        Contexto contexto = actual.get();
        if (contexto == null) {
            throw new IllegalStateException("No hay una transacción abierta en este hilo.");
        }
        return contexto.conexion();
    }

    private <T> T ejecutar(Supplier<T> trabajo, boolean soloLectura) {
        Objects.requireNonNull(trabajo);
        Contexto existente = actual.get();
        if (existente != null) {
            if (existente.soloLectura() && !soloLectura) {
                throw new IllegalStateException("No se puede escribir dentro de una transacción de solo lectura.");
            }
            return trabajo.get();
        }
        Connection conexion = pool.prestar();
        try {
            conexion.setAutoCommit(false);
            actual.set(new Contexto(conexion, soloLectura));
            T resultado;
            try {
                resultado = trabajo.get();
            } catch (RuntimeException | Error e) {
                revertir(conexion, e);
                throw e;
            }
            if (soloLectura) {
                conexion.rollback();
            } else {
                conexion.commit();
            }
            return resultado;
        } catch (SQLException e) {
            // setAutoCommit, commit o rollback fallaron: el estado de la conexión es dudoso.
            RuntimeException error = new ErrorSql("No se pudo completar la transacción (código " + e.getErrorCode()
                    + ").", e);
            revertir(conexion, error);
            throw error;
        } finally {
            actual.remove();
            pool.devolver(conexion);
        }
    }

    /** Revierte; si ni eso se puede, cierra la conexión para que el pool la descarte. El fallo se suma al original. */
    private static void revertir(Connection conexion, Throwable original) {
        try {
            conexion.rollback();
        } catch (SQLException | RuntimeException e) {
            original.addSuppressed(e);
            try {
                conexion.close();
            } catch (SQLException | RuntimeException cierre) {
                original.addSuppressed(cierre);
            }
        }
    }
}
