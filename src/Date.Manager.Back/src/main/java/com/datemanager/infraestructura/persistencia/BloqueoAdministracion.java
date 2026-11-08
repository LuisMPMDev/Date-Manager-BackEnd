package com.datemanager.infraestructura.persistencia;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exclusión mutua, durante la transacción en curso, para los cambios de roles y usuarios. Las protecciones
 * («queda otro usuario con gestionar_roles», «el usuario no está repetido») leen y luego escriben; sin esto,
 * dos peticiones simultáneas podrían pasar ambas la lectura y violar la regla. Usa un bloqueo de aplicación de
 * SQL Server que se libera solo al terminar (confirmar o revertir) la transacción.
 */
@Component
public class BloqueoAdministracion {

    private static final int ESPERA_MAXIMA_MS = 10_000;

    private final JdbcTemplate jdbc;

    public BloqueoAdministracion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Debe llamarse dentro de una transacción, antes de leer lo que se va a proteger. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bloquear() {
        Integer resultado = jdbc.queryForObject("""
                -- El driver abre la transacción de forma implícita (con la primera consulta); sp_getapplock con
                -- dueño «Transaction» exige que ya exista una, así que se abre aquí si aún no hay.
                IF @@TRANCOUNT = 0 BEGIN TRANSACTION;
                DECLARE @r INT;
                EXEC @r = sp_getapplock @Resource = N'datemanager.administracion', @LockMode = N'Exclusive',
                     @LockOwner = N'Transaction', @LockTimeout = %d;
                SELECT @r;
                """.formatted(ESPERA_MAXIMA_MS), Integer.class);
        if (resultado == null || resultado < 0) {
            throw new ErrorNegocio(CodigoError.CONFLICTO,
                    "Hay otro cambio de usuarios o roles en curso. Inténtalo de nuevo en unos segundos.");
        }
    }
}
