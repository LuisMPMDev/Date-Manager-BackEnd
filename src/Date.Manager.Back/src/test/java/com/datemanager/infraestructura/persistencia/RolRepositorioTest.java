package com.datemanager.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.datemanager.PruebaAdministracion;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;
import org.junit.jupiter.api.Test;

class RolRepositorioTest extends PruebaAdministracion {

    @Test
    void guardarYBuscarConservanDatosYPermisos() {
        UUID id = nuevoId();
        Set<Permiso> permisos = EnumSet.of(Permiso.VER_CITAS, Permiso.GESTIONAR_CLIENTES);

        tx.escribir(() -> roles.guardar(new Rol(id, "Agenda", "Maneja citas", permisos)));

        Rol leido = tx.leer(() -> roles.buscar(id)).orElseThrow();
        assertEquals(new Rol(id, "Agenda", "Maneja citas", permisos), leido);
        assertEquals(2, consultarInt("SELECT COUNT(*) FROM rol_permiso WHERE rol_id = ?", id));
    }

    @Test
    void textoConTildesEnyeYEmojiHaceElViajeCompleto() {
        UUID id = nuevoId();
        String nombre = "Recepción 😀";
        String descripcion = "Atención al público: ñandú, café, 日本 y 👩‍⚕️";

        tx.escribir(() -> roles.guardar(new Rol(id, nombre, descripcion, Set.of(Permiso.VER_PANEL))));

        Rol leido = tx.leer(() -> roles.buscar(id)).orElseThrow();
        assertEquals(nombre, leido.nombre());
        assertEquals(descripcion, leido.descripcion());
        // Y tal como quedó en la base (no solo lo que devuelve el repositorio).
        assertEquals(nombre, consultarTexto("SELECT nombre FROM rol WHERE id = ?", id));
        assertEquals(descripcion, consultarTexto("SELECT descripcion FROM rol WHERE id = ?", id));
        assertEquals(nombre, tx.leer(roles::todos).get(0).nombre());
    }

    @Test
    void guardarUnRolExistenteReemplazaDatosYPermisos() {
        UUID id = nuevoId();
        tx.escribir(() -> roles.guardar(new Rol(id, "Uno", "a", Set.of(Permiso.VER_CITAS, Permiso.VER_PANEL))));

        tx.escribir(() -> roles.guardar(new Rol(id, "Dos", "b", Set.of(Permiso.GESTIONAR_ROLES))));

        Rol leido = tx.leer(() -> roles.buscar(id)).orElseThrow();
        assertEquals("Dos", leido.nombre());
        assertEquals("b", leido.descripcion());
        assertEquals(Set.of(Permiso.GESTIONAR_ROLES), leido.permisos());
        assertEquals(1, consultarInt("SELECT COUNT(*) FROM rol"));
    }

    @Test
    void unRolSinPermisosSeGuardaVacio() {
        UUID id = nuevoId();
        tx.escribir(() -> roles.guardar(new Rol(id, "Vacío", "", Set.of(Permiso.VER_PANEL))));

        tx.escribir(() -> roles.guardar(new Rol(id, "Vacío", "", Set.of())));

        assertEquals(Set.of(), tx.leer(() -> roles.buscar(id)).orElseThrow().permisos());
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM rol_permiso"));
    }

    @Test
    void buscarUnRolInexistenteDevuelveVacio() {
        assertTrue(tx.leer(() -> roles.buscar(nuevoId())).isEmpty());
    }

    @Test
    void todosVienenPorNombreYLuegoPorIdConSusPermisos() {
        UUID bravo = crearRol("bravo", Permiso.VER_PANEL);
        UUID agenda = crearRol("Agenda", Permiso.VER_CITAS, Permiso.GESTIONAR_CITAS);
        UUID cobro = crearRol("Cobro");

        List<Rol> todos = tx.leer(roles::todos);

        // El orden de la base (insensible a mayúsculas), igual que el findAll ordenado de la aplicación original.
        assertEquals(List.of(agenda, bravo, cobro), todos.stream().map(Rol::id).toList());
        assertEquals(Set.of(Permiso.VER_CITAS, Permiso.GESTIONAR_CITAS), todos.get(0).permisos());
        assertEquals(Set.of(), todos.get(2).permisos());
    }

    @Test
    void mismoNombreSeOrdenaPorId() {
        UUID a = crearRol("Igual");
        UUID b = crearRol("Igual");

        List<UUID> ids = tx.leer(roles::todos).stream().map(Rol::id).toList();

        // Mismo desempate que el ORDER BY nombre, id de la base (el de un uniqueidentifier no es el del texto).
        List<UUID> segunLaBase = columna("SELECT CAST(id AS NVARCHAR(36)) FROM rol ORDER BY nombre, id").stream()
                .map(UUID::fromString).toList();
        assertEquals(segunLaBase, ids);
        assertEquals(Set.of(a, b), Set.copyOf(ids));
    }

    @Test
    void eliminarBorraElRolYSusPermisos() {
        UUID id = crearRol("Temporal", Permiso.VER_PANEL, Permiso.VER_CITAS);

        tx.escribir(() -> roles.eliminar(id));

        assertTrue(tx.leer(() -> roles.buscar(id)).isEmpty());
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM rol_permiso"));
    }

    @Test
    void eliminarUnRolConUsuariosLoRechazaLaBase() {
        UUID rol = crearRol("Con gente");
        crearUsuario("ana", rol);

        ErrorSql error = assertThrows(ErrorSql.class, () -> tx.escribir(() -> roles.eliminar(rol)));

        assertTrue(ErroresBase.esViolacionDeRestriccion(error));
        assertTrue(tx.leer(() -> roles.buscar(rol)).isPresent());
    }

    @Test
    void sinTransaccionLanzaEnVezDeUsarOtraConexion() {
        assertFalse(tx.activa());
        assertThrows(IllegalStateException.class, () -> roles.buscar(nuevoId()));
    }
}
