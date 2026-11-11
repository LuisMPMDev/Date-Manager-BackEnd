package com.datemanager.infraestructura.persistencia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.datemanager.dominio.VentanaDisponibilidad;

/**
 * Ventanas de disponibilidad ({@code ventana_disponibilidad}). Su llave es (servicio, id): el id lo elige el cliente y
 * puede repetirse entre servicios. Se usa dentro de una {@link Transaccion}; las ventanas de un servicio siempre se
 * reemplazan en bloque ({@link #reemplazar}).
 */
public final class VentanaRepositorio {

    private static final String COLUMNAS = "servicio_id, id, dia, hora_inicio, hora_fin";
    private static final String ORDEN = " ORDER BY dia, hora_inicio, id";

    private final Transaccion transaccion;

    public VentanaRepositorio(Transaccion transaccion) {
        this.transaccion = Objects.requireNonNull(transaccion);
    }

    /** Ventanas del servicio por día y luego hora de inicio. */
    public List<VentanaDisponibilidad> porServicio(UUID servicioId) {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT " + COLUMNAS + " FROM ventana_disponibilidad WHERE servicio_id = ?" + ORDEN)) {
                Jdbc.enlazar(s, servicioId);
                List<VentanaDisponibilidad> ventanas = new ArrayList<>();
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        ventanas.add(ventana(r));
                    }
                }
                return ventanas;
            }
        });
    }

    /** Ventanas de varios servicios, agrupadas por servicio y ya ordenadas por día e inicio. */
    public Map<UUID, List<VentanaDisponibilidad>> porServicios(Collection<UUID> servicioIds) {
        if (servicioIds.isEmpty()) {
            return Map.of();
        }
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement("SELECT " + COLUMNAS
                    + " FROM ventana_disponibilidad WHERE servicio_id IN (" + Jdbc.marcadores(servicioIds.size())
                    + ")" + ORDEN)) {
                Jdbc.enlazar(s, Jdbc.conColeccion(servicioIds));
                Map<UUID, List<VentanaDisponibilidad>> porServicio = new HashMap<>();
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        porServicio.computeIfAbsent(Jdbc.uuid(r, "servicio_id"), k -> new ArrayList<>())
                                .add(ventana(r));
                    }
                }
                return porServicio;
            }
        });
    }

    /**
     * Deja como ventanas del servicio exactamente {@code ventanas}: borra todas las que había y, ya ejecutado el
     * borrado, inserta las recibidas. Así ningún paso intermedio deja dos ventanas cruzadas (el disparador
     * {@code tr_ventana_sin_cruce} las rechazaría) y reenviar los mismos ids no choca con la llave.
     */
    public void reemplazar(UUID servicioId, List<VentanaDisponibilidad> ventanas) {
        Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement("DELETE FROM ventana_disponibilidad WHERE servicio_id = ?")) {
                Jdbc.enlazar(s, servicioId);
                s.executeUpdate();
            }
            if (!ventanas.isEmpty()) {
                try (PreparedStatement s = c.prepareStatement("INSERT INTO ventana_disponibilidad (" + COLUMNAS
                        + ") VALUES (?, ?, ?, ?, ?)")) {
                    for (VentanaDisponibilidad v : ventanas) {
                        Jdbc.enlazar(s, servicioId, v.id(), v.dia(), v.inicio(), v.fin());
                        s.addBatch();
                    }
                    s.executeBatch();
                }
            }
            return null;
        });
    }

    private static VentanaDisponibilidad ventana(ResultSet r) throws SQLException {
        return new VentanaDisponibilidad(Jdbc.uuid(r, "id"), r.getInt("dia"),
                r.getObject("hora_inicio", LocalTime.class), r.getObject("hora_fin", LocalTime.class));
    }
}
