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

import com.datemanager.dominio.Cliente;
import com.datemanager.dominio.Validaciones;

/**
 * Clientes ({@code cliente}). Se usa dentro de una {@link Transaccion}. El correo se guarda siempre normalizado y
 * la base garantiza que es único: ese choque llega como {@link ErrorSql} (véase {@link ErroresBase}).
 */
public class ClienteRepositorio {

    private static final String COLUMNAS = "id, nombre, apellido, telefono, correo";

    private final Transaccion transaccion;

    public ClienteRepositorio(Transaccion transaccion) {
        this.transaccion = Objects.requireNonNull(transaccion);
    }

    public Optional<Cliente> buscar(UUID id) {
        return uno("SELECT " + COLUMNAS + " FROM cliente WHERE id = ?", id);
    }

    /** {@code correo} debe llegar ya normalizado. */
    public Optional<Cliente> porCorreo(String correo) {
        return uno("SELECT " + COLUMNAS + " FROM cliente WHERE correo = ?", correo);
    }

    /**
     * Como {@link #porCorreo}, pero bloquea hasta el fin de la transacción el correo, exista o no ({@code WITH
     * (UPDLOCK, HOLDLOCK, ROWLOCK)}: bloquea también el hueco del índice). Dos «buscar o crear» simultáneos con el
     * mismo correo nuevo se ponen en fila: el segundo ve al cliente que creó el primero en vez de chocar con el único.
     */
    public Optional<Cliente> porCorreoBloqueando(String correo) {
        return uno("SELECT " + COLUMNAS + " FROM cliente WITH (UPDLOCK, HOLDLOCK, ROWLOCK) WHERE correo = ?", correo);
    }

    /** Los clientes con esos ids (los que no existan no aparecen). */
    public List<Cliente> porIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT " + COLUMNAS + " FROM cliente WHERE id IN (" + Jdbc.marcadores(ids.size()) + ")")) {
                Jdbc.enlazar(s, Jdbc.conColeccion(ids));
                return lista(s);
            }
        });
    }

    /** Todos los clientes por nombre, apellido y luego id. */
    public List<Cliente> todos() {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT " + COLUMNAS + " FROM cliente ORDER BY nombre, apellido, id")) {
                return lista(s);
            }
        });
    }

    /**
     * Inserta el cliente o, si ya existe, lo reemplaza. El correo se normaliza aquí, de modo que ningún camino guarde
     * uno sin normalizar. Ejecuta ya; devuelve el cliente tal como quedó guardado.
     */
    public Cliente guardar(Cliente cliente) {
        String correo = Validaciones.normalizarCorreo(cliente.correo());
        Jdbc.con(transaccion, c -> {
            int cambiadas;
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE cliente SET nombre = ?, apellido = ?, telefono = ?, correo = ? WHERE id = ?")) {
                Jdbc.enlazar(s, cliente.nombre(), cliente.apellido(), cliente.telefono(), correo, cliente.id());
                cambiadas = s.executeUpdate();
            }
            if (cambiadas == 0) {
                try (PreparedStatement s = c.prepareStatement("INSERT INTO cliente (" + COLUMNAS
                        + ") VALUES (?, ?, ?, ?, ?)")) {
                    Jdbc.enlazar(s, cliente.id(), cliente.nombre(), cliente.apellido(), cliente.telefono(), correo);
                    s.executeUpdate();
                }
            }
            return null;
        });
        return new Cliente(cliente.id(), cliente.nombre(), cliente.apellido(), cliente.telefono(), correo);
    }

    private Optional<Cliente> uno(String sql, Object parametro) {
        return Jdbc.con(transaccion, c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                Jdbc.enlazar(s, parametro);
                try (ResultSet r = s.executeQuery()) {
                    return r.next() ? Optional.of(cliente(r)) : Optional.<Cliente>empty();
                }
            }
        });
    }

    private static List<Cliente> lista(PreparedStatement sentencia) throws SQLException {
        List<Cliente> clientes = new ArrayList<>();
        try (ResultSet r = sentencia.executeQuery()) {
            while (r.next()) {
                clientes.add(cliente(r));
            }
        }
        return clientes;
    }

    private static Cliente cliente(ResultSet r) throws SQLException {
        return new Cliente(Jdbc.uuid(r, "id"), r.getNString("nombre"), r.getNString("apellido"),
                r.getNString("telefono"), r.getNString("correo"));
    }
}
