package com.datemanager.infraestructura.persistencia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;

/**
 * Exclusión mutua, durante la transacción en curso, para los cambios de roles y usuarios. Las protecciones
 * («queda otro usuario con gestionar_roles», «el usuario no está repetido») leen y luego escriben; sin esto,
 * dos peticiones simultáneas podrían pasar ambas la lectura y violar la regla. Usa un bloqueo de aplicación de
 * SQL Server que se libera solo al terminar (confirmar o revertir) la transacción.
 *
 * <p>Seguridad con el pool: la transacción de {@link Transaccion} usa {@code autoCommit=false} y siempre termina con
 * {@code commit} o {@code rollback}, que cierran también la transacción que abre el truco de {@code @@TRANCOUNT} de
 * abajo (el contador queda en 1, nunca más). Aun así, si la conexión estuviera en confirmación automática (un
 * {@code BEGIN TRAN} explícito que nadie cerraría y que el pool no revertiría), se rechaza antes de enviar nada.
 */
public final class BloqueoAdministracion {

    static final int ESPERA_MAXIMA_MS = 10_000;

    private final Transaccion transaccion;
    private final int esperaMs;

    public BloqueoAdministracion(Transaccion transaccion) {
        this(transaccion, ESPERA_MAXIMA_MS);
    }

    /** Con otra espera máxima, en milisegundos (para pruebas). */
    BloqueoAdministracion(Transaccion transaccion, int esperaMs) {
        this.transaccion = Objects.requireNonNull(transaccion);
        this.esperaMs = esperaMs;
    }

    /**
     * Debe llamarse dentro de una transacción de escritura, antes de leer lo que se va a proteger. Si otra
     * transacción tiene el bloqueo, espera hasta la espera máxima; si no lo logra, {@link ErrorNegocio} {@code CONFLICTO}.
     *
     * @throws IllegalStateException si el hilo no tiene transacción
     */
    public void bloquear() {
        transaccion.exigirActiva();
        Integer resultado = Jdbc.con(transaccion, c -> {
            exigirSinConfirmacionAutomatica(c);
            try (PreparedStatement s = c.prepareStatement("""
                    -- sp_getapplock con dueño «Transaction» exige una transacción abierta. El driver trabaja con
                    -- IMPLICIT_TRANSACTIONS ON: ahí BEGIN TRAN con contador 0 lo sube a 2 y su único COMMIT no cerraría
                    -- nada. Si quedó en 2, se baja a 1 para que un solo commit lo cierre (la misma jugada que el driver).
                    IF @@TRANCOUNT = 0 BEGIN
                        BEGIN TRANSACTION;
                        IF @@TRANCOUNT = 2 COMMIT TRANSACTION;
                    END;
                    DECLARE @r INT;
                    EXEC @r = sp_getapplock @Resource = N'datemanager.administracion', @LockMode = N'Exclusive',
                         @LockOwner = N'Transaction', @LockTimeout = %d;
                    SELECT @r;
                    """.formatted(esperaMs));
                    ResultSet r = s.executeQuery()) {
                return r.next() ? r.getInt(1) : null;
            }
        });
        if (resultado == null || resultado < 0) {
            throw new ErrorNegocio(CodigoError.CONFLICTO,
                    "Hay otro cambio de usuarios o roles en curso. Inténtalo de nuevo en unos segundos.");
        }
    }

    private static void exigirSinConfirmacionAutomatica(Connection conexion) throws SQLException {
        if (conexion.getAutoCommit()) {
            throw new IllegalStateException("El bloqueo de administración exige una transacción con confirmación "
                    + "manual; con confirmación automática dejaría una transacción abierta en la conexión.");
        }
    }
}
