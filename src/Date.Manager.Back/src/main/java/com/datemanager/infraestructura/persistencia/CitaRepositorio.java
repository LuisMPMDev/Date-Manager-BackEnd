package com.datemanager.infraestructura.persistencia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.datemanager.dominio.Cita;
import com.datemanager.dominio.EstadoCita;

/**
 * Citas ({@code cita}). Se usa dentro de una {@link Transaccion}. Los instantes se guardan como hora UTC sin zona
 * ({@code DATETIME2}); así no dependen de la zona de la JVM ni del driver. {@link #insertar} y {@link #actualizar}
 * ejecutan el SQL de inmediato para que quien guarda pueda traducir el choque con el índice filtrado o con el
 * disparador de citas solapadas (véase {@link ErroresBase}).
 */
public class CitaRepositorio {

    private static final String COLUMNAS =
            "id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en, cancelada_en";
    private static final String ORDEN = " ORDER BY fecha, hora_inicio, id";

    private final Transaccion transaccion;

    public CitaRepositorio(Transaccion transaccion) {
        this.transaccion = Objects.requireNonNull(transaccion);
    }

    /** Citas (de cualquier estado) de un servicio en un día. */
    public List<Cita> delServicioEnFecha(UUID servicioId, LocalDate fecha) {
        return lista("SELECT " + COLUMNAS + " FROM cita WHERE servicio_id = ? AND fecha = ?", servicioId, fecha);
    }

    /** Citas entre dos fechas (incluidas) por fecha, hora de inicio e id. */
    public List<Cita> entreFechas(LocalDate desde, LocalDate hasta) {
        return lista("SELECT " + COLUMNAS + " FROM cita WHERE fecha BETWEEN ? AND ?" + ORDEN, desde, hasta);
    }

    /** Como {@link #entreFechas}, solo las de ese estado. */
    public List<Cita> entreFechasConEstado(LocalDate desde, LocalDate hasta, EstadoCita estado) {
        return lista("SELECT " + COLUMNAS + " FROM cita WHERE fecha BETWEEN ? AND ? AND estado = ?" + ORDEN, desde,
                hasta, estado.name());
    }

    /** Citas del rango cuyo estado no es {@code estado} (el tablero ignora las canceladas). Sin orden. */
    public List<Cita> entreFechasSinEstado(LocalDate desde, LocalDate hasta, EstadoCita estado) {
        return lista("SELECT " + COLUMNAS + " FROM cita WHERE fecha BETWEEN ? AND ? AND estado <> ?", desde, hasta,
                estado.name());
    }

    /** Cuántas citas se crearon en {@code (desde, hasta]} (hora UTC sin zona, como se guardan). */
    public long contarCreadas(LocalDateTime desde, LocalDateTime hasta) {
        return contar("SELECT COUNT(*) FROM cita WHERE creada_en > ? AND creada_en <= ?", desde, hasta);
    }

    /** Cuántas citas se cancelaron en {@code (desde, hasta]} (hora UTC sin zona). */
    public long contarCanceladas(LocalDateTime desde, LocalDateTime hasta) {
        return contar("SELECT COUNT(*) FROM cita WHERE cancelada_en > ? AND cancelada_en <= ?", desde, hasta);
    }

    /** Citas creadas o canceladas en {@code (desde, hasta]}: lo único que puede dar una alerta de cita. */
    public List<Cita> creadasOCanceladasEntre(LocalDateTime desde, LocalDateTime hasta) {
        return lista("SELECT " + COLUMNAS + " FROM cita WHERE (creada_en > ? AND creada_en <= ?) "
                + "OR (cancelada_en > ? AND cancelada_en <= ?)", desde, hasta, desde, hasta);
    }

    /** Citas de un estado en un día (el tablero las filtra por hora en el dominio: la hora no viaja como parámetro). */
    public List<Cita> delDiaConEstado(LocalDate fecha, EstadoCita estado) {
        return lista("SELECT " + COLUMNAS + " FROM cita WHERE fecha = ? AND estado = ?", fecha, estado.name());
    }

    /** Las primeras {@code limite} citas de un estado después de un día, de la más cercana a la más lejana. */
    public List<Cita> primerasDespuesDe(LocalDate fecha, EstadoCita estado, int limite) {
        if (limite < 1) {
            return List.of();
        }
        return lista("SELECT TOP (?) " + COLUMNAS + " FROM cita WHERE fecha > ? AND estado = ?" + ORDEN, limite,
                fecha, estado.name());
    }

    /** Servicio de una cita, sin leerla (no cambia nunca: sirve para bloquear el servicio antes que la cita). */
    public Optional<UUID> servicioDe(UUID id) {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement("SELECT servicio_id FROM cita WHERE id = ?")) {
                Jdbc.enlazar(s, id);
                try (ResultSet r = s.executeQuery()) {
                    return r.next() ? Optional.of(Jdbc.uuid(r, "servicio_id")) : Optional.<UUID>empty();
                }
            }
        });
    }

    /**
     * La cita, bloqueada para escritura hasta el fin de la transacción: el mismo SQL que Hibernate emitía en SQL
     * Server para {@code PESSIMISTIC_WRITE} ({@code WITH (UPDLOCK, HOLDLOCK, ROWLOCK)}).
     */
    public Optional<Cita> bloquear(UUID id) {
        List<Cita> citas = lista("SELECT " + COLUMNAS + " FROM cita WITH (UPDLOCK, HOLDLOCK, ROWLOCK) WHERE id = ?",
                id);
        return citas.isEmpty() ? Optional.empty() : Optional.of(citas.get(0));
    }

    /** Inserta la cita (con el enlace con que se reservó, o {@code null}) y ejecuta ya. */
    public Cita insertar(Cita cita, UUID enlaceId) {
        Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement("INSERT INTO cita (" + COLUMNAS
                    + ", enlace_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                s.setString(1, cita.id().toString());
                s.setString(2, cita.servicioId().toString());
                s.setString(3, cita.clienteId().toString());
                s.setObject(4, cita.fecha());
                s.setObject(5, cita.inicio());
                s.setObject(6, cita.fin());
                s.setNString(7, cita.estado().name());
                s.setObject(8, aUtc(cita.creadaEn()));
                s.setObject(9, aUtc(cita.canceladaEn()), Types.TIMESTAMP);
                s.setString(10, enlaceId == null ? null : enlaceId.toString());
                s.executeUpdate();
            }
            return null;
        });
        return cita;
    }

    /** Guarda lo que puede cambiar de una cita (horario, estado, cancelación) y ejecuta ya. */
    public Cita actualizar(Cita cita) {
        int cambiadas = Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement("UPDATE cita SET fecha = ?, hora_inicio = ?, hora_fin = ?, "
                    + "estado = ?, creada_en = ?, cancelada_en = ? WHERE id = ?")) {
                s.setObject(1, cita.fecha());
                s.setObject(2, cita.inicio());
                s.setObject(3, cita.fin());
                s.setNString(4, cita.estado().name());
                s.setObject(5, aUtc(cita.creadaEn()));
                s.setObject(6, aUtc(cita.canceladaEn()), Types.TIMESTAMP);
                s.setString(7, cita.id().toString());
                return s.executeUpdate();
            }
        });
        if (cambiadas == 0) {
            throw new IllegalStateException("La cita " + cita.id() + " no existe.");
        }
        return cita;
    }

    private List<Cita> lista(String sql, Object... parametros) {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                Jdbc.enlazar(s, parametros);
                List<Cita> citas = new ArrayList<>();
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        citas.add(cita(r));
                    }
                }
                return citas;
            }
        });
    }

    private long contar(String sql, Object... parametros) {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                Jdbc.enlazar(s, parametros);
                try (ResultSet r = s.executeQuery()) {
                    r.next();
                    return r.getLong(1);
                }
            }
        });
    }

    private static Cita cita(ResultSet r) throws SQLException {
        return new Cita(Jdbc.uuid(r, "id"), Jdbc.uuid(r, "servicio_id"), Jdbc.uuid(r, "cliente_id"),
                r.getObject("fecha", LocalDate.class), r.getObject("hora_inicio", LocalTime.class),
                r.getObject("hora_fin", LocalTime.class), EstadoCita.valueOf(r.getNString("estado")),
                deUtc(r.getObject("creada_en", LocalDateTime.class)),
                deUtc(r.getObject("cancelada_en", LocalDateTime.class)));
    }

    private static LocalDateTime aUtc(Instant instante) {
        return instante == null ? null : LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static Instant deUtc(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
