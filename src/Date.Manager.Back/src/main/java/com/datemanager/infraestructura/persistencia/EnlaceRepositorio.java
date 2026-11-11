package com.datemanager.infraestructura.persistencia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.UUID;

/**
 * Enlaces de invitación ({@code enlace_invitacion}). Por ahora solo trae lo que necesita la administración de
 * usuarios; el resto de consultas (por código, uso concurrente...) las añade la tarea de enlaces.
 */
public final class EnlaceRepositorio {

    private final Transaccion transaccion;

    public EnlaceRepositorio(Transaccion transaccion) {
        this.transaccion = Objects.requireNonNull(transaccion);
    }

    /** Si el usuario creó algún enlace (la llave foránea {@code creado_por} no tiene cascada). */
    public boolean existeCreadoPor(UUID usuarioId) {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT CASE WHEN EXISTS (SELECT 1 FROM enlace_invitacion WHERE creado_por = ?) THEN 1 ELSE 0 END")) {
                Jdbc.enlazar(s, usuarioId);
                try (ResultSet r = s.executeQuery()) {
                    return r.next() && r.getInt(1) == 1;
                }
            }
        });
    }
}
