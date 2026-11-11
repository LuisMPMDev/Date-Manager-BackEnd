package com.datemanager.infraestructura.persistencia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;

/**
 * Roles ({@code rol}) con sus permisos ({@code rol_permiso}, un id snake_case por fila). Se usa dentro de una
 * {@link Transaccion}. El id lo asigna quien llama: el panel crea roles con el id que elige el cliente (upsert).
 */
public final class RolRepositorio {

    private final Transaccion transaccion;

    public RolRepositorio(Transaccion transaccion) {
        this.transaccion = Objects.requireNonNull(transaccion);
    }

    public Optional<Rol> buscar(UUID id) {
        return Jdbc.con(transaccion, c -> {
            String nombre;
            String descripcion;
            try (PreparedStatement s = c.prepareStatement("SELECT nombre, descripcion FROM rol WHERE id = ?")) {
                Jdbc.enlazar(s, id);
                try (ResultSet r = s.executeQuery()) {
                    if (!r.next()) {
                        return Optional.<Rol>empty();
                    }
                    nombre = r.getNString("nombre");
                    descripcion = r.getNString("descripcion");
                }
            }
            Set<Permiso> permisos = EnumSet.noneOf(Permiso.class);
            try (PreparedStatement s = c.prepareStatement("SELECT permiso FROM rol_permiso WHERE rol_id = ?")) {
                Jdbc.enlazar(s, id);
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        permisos.add(permiso(r.getNString("permiso")));
                    }
                }
            }
            return Optional.of(new Rol(id, nombre, descripcion, permisos));
        });
    }

    /** Todos los roles, por nombre y luego por id (como el {@code findAll} ordenado de la aplicación original). */
    public List<Rol> todos() {
        return Jdbc.con(transaccion, c -> {
            Map<UUID, Set<Permiso>> permisos = new HashMap<>();
            try (PreparedStatement s = c.prepareStatement("SELECT rol_id, permiso FROM rol_permiso");
                    ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    permisos.computeIfAbsent(Jdbc.uuid(r, "rol_id"), k -> EnumSet.noneOf(Permiso.class))
                            .add(permiso(r.getNString("permiso")));
                }
            }
            List<Rol> roles = new ArrayList<>();
            try (PreparedStatement s = c.prepareStatement("SELECT id, nombre, descripcion FROM rol ORDER BY nombre, id");
                    ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    UUID id = Jdbc.uuid(r, "id");
                    roles.add(new Rol(id, r.getNString("nombre"), r.getNString("descripcion"),
                            permisos.getOrDefault(id, Set.of())));
                }
            }
            return roles;
        });
    }

    /** Inserta el rol o, si ya existe, lo reemplaza (datos y permisos). Devuelve el rol guardado. */
    public Rol guardar(Rol rol) {
        Jdbc.con(transaccion, c -> {
            int cambiadas;
            try (PreparedStatement s = c.prepareStatement("UPDATE rol SET nombre = ?, descripcion = ? WHERE id = ?")) {
                Jdbc.enlazar(s, rol.nombre(), rol.descripcion(), rol.id());
                cambiadas = s.executeUpdate();
            }
            if (cambiadas == 0) {
                try (PreparedStatement s = c.prepareStatement(
                        "INSERT INTO rol (id, nombre, descripcion) VALUES (?, ?, ?)")) {
                    Jdbc.enlazar(s, rol.id(), rol.nombre(), rol.descripcion());
                    s.executeUpdate();
                }
            }
            try (PreparedStatement s = c.prepareStatement("DELETE FROM rol_permiso WHERE rol_id = ?")) {
                Jdbc.enlazar(s, rol.id());
                s.executeUpdate();
            }
            if (!rol.permisos().isEmpty()) {
                try (PreparedStatement s = c.prepareStatement(
                        "INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, ?)")) {
                    for (Permiso permiso : rol.permisos()) {
                        Jdbc.enlazar(s, rol.id(), permiso.id());
                        s.addBatch();
                    }
                    s.executeBatch();
                }
            }
            return null;
        });
        return rol;
    }

    /** Borra el rol y, por la cascada de la base, sus permisos. Si hay usuarios con ese rol, la base lo rechaza. */
    public void eliminar(UUID id) {
        Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement("DELETE FROM rol WHERE id = ?")) {
                Jdbc.enlazar(s, id);
                s.executeUpdate();
            }
            return null;
        });
    }

    /** Un id desconocido en la base es un error interno (la restricción CHECK lo impide). */
    private static Permiso permiso(String id) {
        for (Permiso permiso : Permiso.values()) {
            if (permiso.id().equals(id)) {
                return permiso;
            }
        }
        throw new IllegalStateException("Permiso desconocido guardado en la base: " + id);
    }
}
