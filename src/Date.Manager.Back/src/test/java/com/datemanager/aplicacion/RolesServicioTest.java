package com.datemanager.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.datemanager.PruebaAdministracion;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RolesServicioTest extends PruebaAdministracion {

    private UUID rolAdmin;
    private UUID admin;

    @BeforeEach
    void crearAdministrador() {
        rolAdmin = crearRol("Administrador", Permiso.VER_PANEL, Permiso.GESTIONAR_USUARIOS, Permiso.GESTIONAR_ROLES);
        admin = crearUsuario("admin", rolAdmin);
    }

    private List<String> permisosGuardados(UUID rolId) {
        return columna("SELECT permiso FROM rol_permiso WHERE rol_id = ? ORDER BY permiso", rolId);
    }

    // ---------- Guardar ----------

    @Test
    void guardarUnRolConGestionarCitasIncluyeVerCitas() {
        UUID id = nuevoId();

        Rol rol = rolesServicio.guardar(id, "Agenda", "Maneja citas", Set.of(Permiso.GESTIONAR_CITAS), admin);

        assertEquals(id, rol.id());
        assertEquals(Set.of(Permiso.GESTIONAR_CITAS, Permiso.VER_CITAS), rol.permisos());
        assertEquals(List.of("gestionar_citas", "ver_citas"), permisosGuardados(id));
    }

    @Test
    void atenderCitasTambienExigeVerCitas() {
        UUID id = nuevoId();

        rolesServicio.guardar(id, "Salón", "", Set.of(Permiso.ATENDER_CITAS), admin);

        assertEquals(List.of("atender_citas", "ver_citas"), permisosGuardados(id));
    }

    @Test
    void guardarCreaSiNoExisteConElIdDadoYLuegoActualiza() {
        UUID id = nuevoId();

        rolesServicio.guardar(id, "  Agenda  ", null, Set.of(Permiso.VER_CITAS), admin);
        assertEquals("Agenda", consultarTexto("SELECT nombre FROM rol WHERE id = ?", id));
        assertEquals("", consultarTexto("SELECT descripcion FROM rol WHERE id = ?", id));

        Rol actualizado = rolesServicio.guardar(id, "Agenda 2", " otra ", Set.of(Permiso.VER_PANEL), admin);

        assertEquals(new Rol(id, "Agenda 2", "otra", Set.of(Permiso.VER_PANEL)), actualizado);
        assertEquals(2, consultarInt("SELECT COUNT(*) FROM rol"));
        assertEquals(List.of("ver_panel"), permisosGuardados(id));
    }

    @Test
    void unRolConTildesEnyeYEmojiSeGuardaYSeListaIntacto() {
        UUID id = nuevoId();

        Rol guardado = rolesServicio.guardar(id, "Recepción 😀", "Atención: ñandú, café y 👩‍⚕️",
                Set.of(Permiso.VER_CITAS), admin);

        assertEquals("Recepción 😀", guardado.nombre());
        assertEquals("Recepción 😀", consultarTexto("SELECT nombre FROM rol WHERE id = ?", id));
        Rol listado = rolesServicio.listar().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
        assertEquals("Recepción 😀", listado.nombre());
        assertEquals("Atención: ñandú, café y 👩‍⚕️", listado.descripcion());
    }

    @Test
    void listarVienePorNombreConSusPermisos() {
        rolesServicio.guardar(nuevoId(), "Bravo", "", Set.of(Permiso.VER_PANEL), admin);

        List<Rol> roles = rolesServicio.listar();

        assertEquals(List.of("Administrador", "Bravo"), roles.stream().map(Rol::nombre).toList());
        assertEquals(Set.of(Permiso.VER_PANEL, Permiso.GESTIONAR_USUARIOS, Permiso.GESTIONAR_ROLES),
                roles.get(0).permisos());
    }

    // ---------- Protecciones ----------

    @Test
    void elActorDebeGestionarRolesSegunSuRolActual() {
        UUID sinPermiso = crearRol("Sin", Permiso.GESTIONAR_USUARIOS);
        UUID actor = crearUsuario("actor", sinPermiso);

        exigirError(CodigoError.SIN_PERMISO,
                () -> rolesServicio.guardar(nuevoId(), "Nuevo", "", Set.of(Permiso.VER_PANEL), actor));

        assertEquals(2, consultarInt("SELECT COUNT(*) FROM rol"));
    }

    @Test
    void unActorQueNoExisteNoEstaAutenticado() {
        exigirError(CodigoError.NO_AUTENTICADO,
                () -> rolesServicio.guardar(nuevoId(), "Nuevo", "", Set.of(Permiso.VER_PANEL), nuevoId()));
    }

    @Test
    void elAdministradorNoPuedeQuitarseGestionarRolesDeSuPropioRol() {
        ErrorNegocio error = exigirError(CodigoError.CONFLICTO,
                () -> rolesServicio.guardar(rolAdmin, "Administrador", "", Set.of(Permiso.VER_PANEL), admin));

        assertEquals("No puedes quitarte el permiso de gestionar roles de tu propio rol.", error.getMessage());
        assertTrue(permisosGuardados(rolAdmin).contains("gestionar_roles"));
    }

    @Test
    void sePuedeEditarElPropioRolSiConservaGestionarRoles() {
        rolesServicio.guardar(rolAdmin, "Admin", "", Set.of(Permiso.GESTIONAR_ROLES), admin);

        assertEquals("Admin", consultarTexto("SELECT nombre FROM rol WHERE id = ?", rolAdmin));
        assertEquals(List.of("gestionar_roles"), permisosGuardados(rolAdmin));
    }

    @Test
    void quitarGestionarRolesAOtroRolSiEsPosibleSiQuedaUnGestor() {
        UUID otro = crearRol("Otro gestor", Permiso.GESTIONAR_ROLES);
        crearUsuario("otro", otro);

        rolesServicio.guardar(otro, "Otro gestor", "", Set.of(Permiso.VER_PANEL), admin);

        assertEquals(List.of("ver_panel"), permisosGuardados(otro));
        assertEquals(1, gestoresDeRoles());
    }

    @Test
    void unRolConUsuariosNoSeElimina() {
        UUID rol = crearRol("Con gente", Permiso.VER_CITAS);
        crearUsuario("ana", rol);

        ErrorNegocio error = exigirError(CodigoError.CONFLICTO, () -> rolesServicio.eliminar(rol));

        assertEquals("No se puede eliminar un rol que tiene usuarios asignados.", error.getMessage());
        assertEquals(1, consultarInt("SELECT COUNT(*) FROM rol WHERE id = ?", rol));
    }

    @Test
    void elRolDeQuienEliminaTampocoSePuedeEliminar() {
        exigirError(CodigoError.CONFLICTO, () -> rolesServicio.eliminar(rolAdmin));

        assertEquals(1, consultarInt("SELECT COUNT(*) FROM rol WHERE id = ?", rolAdmin));
    }

    @Test
    void unRolSinUsuariosSeEliminaConSusPermisos() {
        UUID rol = crearRol("Libre", Permiso.VER_CITAS, Permiso.VER_PANEL);

        rolesServicio.eliminar(rol);

        assertEquals(0, consultarInt("SELECT COUNT(*) FROM rol WHERE id = ?", rol));
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM rol_permiso WHERE rol_id = ?", rol));
    }

    @Test
    void eliminarUnRolInexistenteEsNoEncontrado() {
        ErrorNegocio error = exigirError(CodigoError.NO_ENCONTRADO, () -> rolesServicio.eliminar(nuevoId()));

        assertEquals("El rol no existe.", error.getMessage());
    }

    @Test
    void unCambioRechazadoNoDejaNadaGuardado() {
        UUID id = nuevoId();
        UUID sinPermiso = crearRol("Sin", Permiso.GESTIONAR_USUARIOS);
        UUID actor = crearUsuario("actor", sinPermiso);

        exigirError(CodigoError.SIN_PERMISO,
                () -> rolesServicio.guardar(id, "Fantasma", "", Set.of(Permiso.VER_PANEL), actor));

        assertEquals(0, consultarInt("SELECT COUNT(*) FROM rol WHERE id = ?", id));
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM rol_permiso WHERE rol_id = ?", id));
    }

    // ---------- Concurrencia ----------

    @Test
    void dosGestoresQuitandoseElPermisoMutuamenteALosRolesDelOtroNoDejanAlSistemaSinGestorDeRoles()
            throws Exception {
        // Solo A (rol X) y B (rol Y) gestionan roles. A edita Y y B edita X a la vez: ninguno toca su propio rol,
        // así que ninguna petición sola viola la regla del propio rol, pero juntas dejarían a cero gestores.
        tx.escribir(() -> usuarios.eliminar(admin));
        UUID rolX = crearRol("X", Permiso.GESTIONAR_ROLES);
        UUID rolY = crearRol("Y", Permiso.GESTIONAR_ROLES);
        UUID a = crearUsuario("a", rolX);
        UUID b = crearUsuario("b", rolY);
        Set<Permiso> sinPermiso = Set.of(Permiso.VER_PANEL);

        List<Object> resultados = enParalelo(
                () -> rolesServicio.guardar(rolY, "Y", "", sinPermiso, a),
                () -> rolesServicio.guardar(rolX, "X", "", sinPermiso, b));

        assertEquals(1, resultados.stream().filter(r -> r instanceof Rol).count(), "solo una sale bien: " + resultados);
        assertTrue(resultados.stream().filter(r -> !(r instanceof Rol)).allMatch(r -> r instanceof ErrorNegocio e
                && (e.codigo() == CodigoError.SIN_PERMISO || e.codigo() == CodigoError.CONFLICTO)),
                "la otra es 403 o 409: " + resultados);
        assertEquals(1, gestoresDeRoles());
    }
}
