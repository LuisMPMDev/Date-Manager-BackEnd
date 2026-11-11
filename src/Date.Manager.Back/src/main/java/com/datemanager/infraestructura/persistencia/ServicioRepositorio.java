package com.datemanager.infraestructura.persistencia;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.VentanaDisponibilidad;

/**
 * Servicios ({@code servicio}) con sus ventanas (que guarda {@link VentanaRepositorio}). Se usa dentro de una
 * {@link Transaccion}. El id lo asigna quien llama: el panel crea servicios con el id que elige el cliente (upsert).
 */
public final class ServicioRepositorio {

    private static final String COLUMNAS = "id, nombre, duracion_min, activo, valor";

    private final Transaccion transaccion;
    private final VentanaRepositorio ventanas;

    public ServicioRepositorio(Transaccion transaccion, VentanaRepositorio ventanas) {
        this.transaccion = Objects.requireNonNull(transaccion);
        this.ventanas = Objects.requireNonNull(ventanas);
    }

    public Optional<Servicio> buscar(UUID id) {
        return leerUno("SELECT " + COLUMNAS + " FROM servicio WHERE id = ?", id);
    }

    /**
     * El servicio, bloqueado para escritura hasta el fin de la transacción: el mismo SQL que Hibernate emitía en SQL
     * Server para {@code PESSIMISTIC_WRITE} ({@code WITH (UPDLOCK, HOLDLOCK, ROWLOCK)}; si la fila no existe, bloquea
     * también el hueco del índice). Es el bloqueo de la agenda del servicio: reservar, mover y guardar el servicio lo
     * toman siempre primero.
     */
    public Optional<Servicio> bloquear(UUID id) {
        return leerUno("SELECT " + COLUMNAS + " FROM servicio WITH (UPDLOCK, HOLDLOCK, ROWLOCK) WHERE id = ?", id);
    }

    /** Todos los servicios por nombre y luego id, cada uno con sus ventanas por día e inicio. */
    public List<Servicio> todos() {
        List<Fila> filas = Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT " + COLUMNAS + " FROM servicio ORDER BY nombre, id")) {
                List<Fila> lista = new ArrayList<>();
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        lista.add(fila(r));
                    }
                }
                return lista;
            }
        });
        if (filas.isEmpty()) {
            return List.of();
        }
        var porServicio = ventanas.porServicios(filas.stream().map(Fila::id).toList());
        return filas.stream().map(f -> f.aDominio(porServicio.getOrDefault(f.id(), List.of()))).toList();
    }

    /**
     * Inserta el servicio o, si ya existe, lo reemplaza, ventanas incluidas (borra las guardadas y escribe las
     * recibidas). Todo se ejecuta ya. Devuelve el servicio tal como quedó guardado.
     */
    public Servicio guardar(Servicio servicio) {
        Jdbc.con(transaccion, c -> {
            int cambiadas;
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE servicio SET nombre = ?, duracion_min = ?, activo = ?, valor = ? WHERE id = ?")) {
                Jdbc.enlazar(s, servicio.nombre(), servicio.duracionMin(), servicio.activo(), servicio.valor(),
                        servicio.id());
                cambiadas = s.executeUpdate();
            }
            if (cambiadas == 0) {
                try (PreparedStatement s = c.prepareStatement("INSERT INTO servicio (" + COLUMNAS
                        + ") VALUES (?, ?, ?, ?, ?)")) {
                    Jdbc.enlazar(s, servicio.id(), servicio.nombre(), servicio.duracionMin(), servicio.activo(),
                            servicio.valor());
                    s.executeUpdate();
                }
            }
            return null;
        });
        ventanas.reemplazar(servicio.id(), servicio.ventanas());
        return buscar(servicio.id()).orElseThrow(() -> new IllegalStateException("El servicio guardado no aparece."));
    }

    private Optional<Servicio> leerUno(String sql, UUID id) {
        Fila fila = Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                Jdbc.enlazar(s, id);
                try (ResultSet r = s.executeQuery()) {
                    return r.next() ? fila(r) : null;
                }
            }
        });
        // Las ventanas se leen después del bloqueo de la fila: nadie las cambia mientras tanto.
        return Optional.ofNullable(fila).map(f -> f.aDominio(ventanas.porServicio(f.id())));
    }

    private static Fila fila(ResultSet r) throws SQLException {
        return new Fila(Jdbc.uuid(r, "id"), r.getNString("nombre"), r.getInt("duracion_min"), r.getBoolean("activo"),
                r.getBigDecimal("valor"));
    }

    private record Fila(UUID id, String nombre, int duracionMin, boolean activo, BigDecimal valor) {

        Servicio aDominio(List<VentanaDisponibilidad> ventanas) {
            return new Servicio(id, nombre, duracionMin, activo, valor, ventanas);
        }
    }
}
