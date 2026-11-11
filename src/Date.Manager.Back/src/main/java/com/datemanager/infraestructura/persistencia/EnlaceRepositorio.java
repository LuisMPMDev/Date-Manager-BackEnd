package com.datemanager.infraestructura.persistencia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.datemanager.dominio.Enlace;

/**
 * Enlaces de invitación ({@code enlace_invitacion}). Los instantes se guardan como hora UTC sin zona
 * ({@code DATETIME2}). {@code usos} y {@code desactivado_en} solo cambian con los UPDATE condicionales de
 * {@link #consumirUso} y {@link #desactivar}: ningún guardado del enlace entero puede pisar un uso consumido por otra
 * transacción.
 */
public final class EnlaceRepositorio {

    private static final String COLUMNAS =
            "id, codigo, creado_por, creado_en, vence_en, max_usos, usos, desactivado_en";

    private final Transaccion transaccion;

    public EnlaceRepositorio(Transaccion transaccion) {
        this.transaccion = Objects.requireNonNull(transaccion);
    }

    /** Por el índice único de {@code codigo} (intercalación binaria: distingue mayúsculas). */
    public Optional<Enlace> porCodigo(String codigo) {
        return uno("SELECT " + COLUMNAS + " FROM enlace_invitacion WHERE codigo = ?", codigo);
    }

    public Optional<Enlace> buscar(UUID id) {
        return uno("SELECT " + COLUMNAS + " FROM enlace_invitacion WHERE id = ?", id);
    }

    /** Los más nuevos primero (por fecha de creación y luego por id, ambos descendentes). */
    public List<Enlace> todosRecientesPrimero() {
        return lista("SELECT " + COLUMNAS + " FROM enlace_invitacion ORDER BY creado_en DESC, id DESC");
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

    /** Inserta el enlace nuevo y ejecuta ya. */
    public Enlace insertar(Enlace enlace) {
        Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement("INSERT INTO enlace_invitacion (" + COLUMNAS
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                s.setString(1, enlace.id().toString());
                s.setNString(2, enlace.codigo());
                s.setString(3, enlace.creadoPor().toString());
                s.setObject(4, aUtc(enlace.creadoEn()));
                s.setObject(5, aUtc(enlace.venceEn()));
                s.setObject(6, enlace.maxUsos(), Types.INTEGER);
                s.setInt(7, enlace.usos());
                s.setObject(8, aUtc(enlace.desactivadoEn()), Types.TIMESTAMP);
                s.executeUpdate();
            }
            return null;
        });
        return enlace;
    }

    /**
     * Gasta un uso solo si el enlace sigue activo en {@code ahoraUtc}, en una única sentencia: la fila queda
     * bloqueada hasta el fin de la transacción, así que otra reserva simultánea espera y ya ve el uso gastado.
     * Devuelve las filas cambiadas (0 = el enlace ya no vale).
     */
    public int consumirUso(UUID id, LocalDateTime ahoraUtc) {
        return actualizar("""
                UPDATE enlace_invitacion SET usos = usos + 1
                WHERE id = ? AND desactivado_en IS NULL AND vence_en > ?
                  AND (max_usos IS NULL OR usos < max_usos)
                """, id, ahoraUtc);
    }

    /** Desactiva si no lo estaba (idempotente: conserva la primera fecha). Devuelve las filas cambiadas. */
    public int desactivar(UUID id, LocalDateTime ahoraUtc) {
        return actualizar("""
                UPDATE enlace_invitacion SET desactivado_en = ?
                WHERE id = ? AND desactivado_en IS NULL
                """, ahoraUtc, id);
    }

    private int actualizar(String sql, Object... parametros) {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                Jdbc.enlazar(s, parametros);
                return s.executeUpdate();
            }
        });
    }

    private Optional<Enlace> uno(String sql, Object parametro) {
        List<Enlace> enlaces = lista(sql, parametro);
        return enlaces.isEmpty() ? Optional.empty() : Optional.of(enlaces.get(0));
    }

    private List<Enlace> lista(String sql, Object... parametros) {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                Jdbc.enlazar(s, parametros);
                List<Enlace> enlaces = new ArrayList<>();
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        enlaces.add(enlace(r));
                    }
                }
                return enlaces;
            }
        });
    }

    private static Enlace enlace(ResultSet r) throws SQLException {
        int maxUsos = r.getInt("max_usos");
        Integer max = r.wasNull() ? null : maxUsos;
        return new Enlace(Jdbc.uuid(r, "id"), r.getNString("codigo"), Jdbc.uuid(r, "creado_por"),
                deUtc(r.getObject("creado_en", LocalDateTime.class)), deUtc(r.getObject("vence_en", LocalDateTime.class)),
                max, r.getInt("usos"), deUtc(r.getObject("desactivado_en", LocalDateTime.class)));
    }

    private static LocalDateTime aUtc(Instant instante) {
        return instante == null ? null : LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static Instant deUtc(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
