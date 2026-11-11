package com.datemanager.infraestructura.persistencia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.datemanager.dominio.Usuario;
import com.datemanager.dominio.Validaciones;

/**
 * Usuarios del panel ({@code usuario}). Se usa dentro de una {@link Transaccion}. El {@link Usuario} no lleva la
 * clave: el hash solo entra por {@link #guardar} y solo sale por {@link #buscarPorUsuario} (para iniciar sesión), igual
 * que el {@code getClaveHash()} de la entidad original. El nombre de usuario se guarda siempre normalizado.
 */
public class UsuarioRepositorio {

    private static final String COLUMNAS = "id, nombre, apellido, correo, telefono, usuario, rol_id";

    /** Un usuario con el hash de su clave. {@code toString} no lo muestra. */
    public record UsuarioConClave(Usuario usuario, String claveHash) {

        @Override
        public String toString() {
            return "UsuarioConClave[usuario=" + usuario.usuario() + "]";
        }
    }

    private final Transaccion transaccion;

    public UsuarioRepositorio(Transaccion transaccion) {
        this.transaccion = Objects.requireNonNull(transaccion);
    }

    public Optional<Usuario> buscar(UUID id) {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement("SELECT " + COLUMNAS + " FROM usuario WHERE id = ?")) {
                Jdbc.enlazar(s, id);
                try (ResultSet r = s.executeQuery()) {
                    return r.next() ? Optional.of(usuario(r)) : Optional.<Usuario>empty();
                }
            }
        });
    }

    /** {@code usuario} debe llegar ya normalizado ({@code Validaciones.normalizarUsuario}). */
    public Optional<UsuarioConClave> buscarPorUsuario(String usuario) {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT " + COLUMNAS + ", clave_hash FROM usuario WHERE usuario = ?")) {
                Jdbc.enlazar(s, usuario);
                try (ResultSet r = s.executeQuery()) {
                    return r.next() ? Optional.of(new UsuarioConClave(usuario(r), r.getNString("clave_hash")))
                            : Optional.<UsuarioConClave>empty();
                }
            }
        });
    }

    public List<Usuario> porRol(UUID rolId) {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement("SELECT " + COLUMNAS + " FROM usuario WHERE rol_id = ?")) {
                Jdbc.enlazar(s, rolId);
                return lista(s);
            }
        });
    }

    /** Todos los usuarios, por nombre de usuario y luego por id (como el {@code findAll} ordenado de la original). */
    public List<Usuario> todos() {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT " + COLUMNAS + " FROM usuario ORDER BY usuario, id")) {
                return lista(s);
            }
        });
    }

    /** Usuarios cuyo rol está en {@code rolIds}. */
    public long contarPorRolIn(Collection<UUID> rolIds) {
        if (rolIds.isEmpty()) {
            return 0;
        }
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT COUNT(*) FROM usuario WHERE rol_id IN (" + Jdbc.marcadores(rolIds.size()) + ")")) {
                Jdbc.enlazar(s, Jdbc.conColeccion(rolIds));
                return contar(s);
            }
        });
    }

    /** Usuarios distintos de {@code id} cuyo rol está en {@code rolIds}. */
    public long contarOtrosPorRolIn(UUID id, Collection<UUID> rolIds) {
        if (rolIds.isEmpty()) {
            return 0;
        }
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM usuario WHERE id <> ? AND rol_id IN ("
                    + Jdbc.marcadores(rolIds.size()) + ")")) {
                Jdbc.enlazar(s, Jdbc.conColeccion(rolIds, id));
                return contar(s);
            }
        });
    }

    /**
     * Inserta el usuario o, si ya existe, lo reemplaza. {@code claveHash} {@code null} conserva el hash actual (solo
     * vale al editar: al crear la clave es obligatoria). El nombre de usuario se normaliza aquí, de modo que ningún
     * camino guarde uno sin normalizar. Devuelve el usuario tal como quedó guardado.
     */
    public Usuario guardar(Usuario usuario, String claveHash) {
        String nombreUsuario = Validaciones.normalizarUsuario(usuario.usuario());
        Jdbc.con(transaccion, c -> {
            int cambiadas;
            if (claveHash == null) {
                try (PreparedStatement s = c.prepareStatement("UPDATE usuario SET nombre = ?, apellido = ?, "
                        + "correo = ?, telefono = ?, usuario = ?, rol_id = ? WHERE id = ?")) {
                    Jdbc.enlazar(s, usuario.nombre(), usuario.apellido(), usuario.correo(), usuario.telefono(),
                            nombreUsuario, usuario.rolId(), usuario.id());
                    cambiadas = s.executeUpdate();
                }
            } else {
                try (PreparedStatement s = c.prepareStatement("UPDATE usuario SET nombre = ?, apellido = ?, "
                        + "correo = ?, telefono = ?, usuario = ?, rol_id = ?, clave_hash = ? WHERE id = ?")) {
                    Jdbc.enlazar(s, usuario.nombre(), usuario.apellido(), usuario.correo(), usuario.telefono(),
                            nombreUsuario, usuario.rolId(), claveHash, usuario.id());
                    cambiadas = s.executeUpdate();
                }
            }
            if (cambiadas == 0) {
                if (claveHash == null) {
                    throw new IllegalArgumentException("Un usuario nuevo necesita el hash de su clave.");
                }
                try (PreparedStatement s = c.prepareStatement("INSERT INTO usuario (" + COLUMNAS
                        + ", clave_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    Jdbc.enlazar(s, usuario.id(), usuario.nombre(), usuario.apellido(), usuario.correo(),
                            usuario.telefono(), nombreUsuario, usuario.rolId(), claveHash);
                    s.executeUpdate();
                }
            }
            return null;
        });
        return new Usuario(usuario.id(), usuario.nombre(), usuario.apellido(), usuario.correo(), usuario.telefono(),
                nombreUsuario, usuario.rolId());
    }

    public void eliminar(UUID id) {
        Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement("DELETE FROM usuario WHERE id = ?")) {
                Jdbc.enlazar(s, id);
                s.executeUpdate();
            }
            return null;
        });
    }

    private static List<Usuario> lista(PreparedStatement sentencia) throws SQLException {
        List<Usuario> usuarios = new ArrayList<>();
        try (ResultSet r = sentencia.executeQuery()) {
            while (r.next()) {
                usuarios.add(usuario(r));
            }
        }
        return usuarios;
    }

    private static long contar(PreparedStatement sentencia) throws SQLException {
        try (ResultSet r = sentencia.executeQuery()) {
            r.next();
            return r.getLong(1);
        }
    }

    private static Usuario usuario(ResultSet r) throws SQLException {
        return new Usuario(Jdbc.uuid(r, "id"), r.getNString("nombre"), r.getNString("apellido"),
                r.getNString("correo"), r.getNString("telefono"), r.getNString("usuario"), Jdbc.uuid(r, "rol_id"));
    }
}
