package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RolesControladorTest extends PruebaPanel {

    private static final String ROLES = "/api/panel/roles/";

    private UUID rolAdmin;
    private UUID admin;

    @BeforeEach
    void crearAdministrador() {
        rolAdmin = crearRol("Administrador", "ver_panel", "gestionar_usuarios", "gestionar_roles");
        admin = crearUsuario("admin", rolAdmin);
    }

    private static List<String> permisosGuardados(UUID rolId) {
        return columna("SELECT permiso FROM rol_permiso WHERE rol_id = ? ORDER BY permiso", rolId);
    }

    @Test
    void guardarUnRolConGestionarCitasIncluyeVerCitas() {
        UUID id = UUID.randomUUID();

        Resp r = exigir(200, guardar(ROLES + id, admin, Map.of("nombre", "Agenda", "descripcion", "Maneja citas",
                "permisos", List.of("gestionar_citas"))));
        assertEquals(id.toString(), r.en("id"));
        assertEquals("Agenda", r.en("nombre"));
        assertEquals("Maneja citas", r.en("descripcion"));
        assertEquals(List.of("ver_citas", "gestionar_citas"), r.en("permisos"));

        assertEquals(List.of("gestionar_citas", "ver_citas"), permisosGuardados(id));
    }

    @Test
    void atenderCitasTambienExigeVerCitas() {
        Resp r = exigir(200, guardar(ROLES + UUID.randomUUID(), admin,
                Map.of("nombre", "Médico", "permisos", List.of("atender_citas"))));
        assertEquals(List.of("ver_citas", "atender_citas"), r.en("permisos"));
    }

    @Test
    void putCreaSiNoExisteConElIdDeLaRutaYLuegoActualiza() {
        UUID id = UUID.randomUUID();

        exigir(200, guardar(ROLES + id, admin, Map.of("nombre", "Recepción", "descripcion", "Agenda",
                "permisos", List.of("ver_citas"))));
        assertEquals(2, contar("rol"));

        Resp r = exigir(200, guardar(ROLES + id, admin, Map.of("nombre", "Recepción 2", "descripcion", "Otra",
                "permisos", List.of("ver_panel", "ver_citas"))));
        assertEquals(id.toString(), r.en("id"));
        assertEquals("Recepción 2", r.en("nombre"));
        assertEquals(List.of("ver_panel", "ver_citas"), r.en("permisos"));

        assertEquals(2, contar("rol"));
        assertEquals("Recepción 2", consultarTexto("SELECT nombre FROM rol WHERE id = ?", id));
        assertEquals(List.of("ver_citas", "ver_panel"), permisosGuardados(id));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarDevuelveLosRolesConPermisosEnElOrdenDelCatalogo() {
        crearRol("Recepción", "ver_citas", "ver_panel");

        Resp r = exigir(200, obtener("/api/panel/roles", admin));
        List<Object> roles = r.lista("$");
        assertEquals(2, roles.size());
        Map<String, Object> recepcion = roles.stream().map(o -> (Map<String, Object>) o)
                .filter(m -> "Recepción".equals(m.get("nombre"))).findFirst().orElseThrow();
        assertEquals(List.of("ver_panel", "ver_citas"), recepcion.get("permisos"));
    }

    @Test
    void elAdministradorNoPuedeQuitarseGestionarRolesDeSuPropioRol() {
        Resp r = exigir(409, "CONFLICTO", guardar(ROLES + rolAdmin, admin, Map.of("nombre", "Administrador",
                "permisos", List.of("ver_panel", "gestionar_usuarios"))));
        assertFalse(r.texto("mensaje").isEmpty());

        assertTrue(permisosGuardados(rolAdmin).contains("gestionar_roles"));
    }

    @Test
    void sePuedeEditarElPropioRolSiConservaGestionarRoles() {
        Resp r = exigir(200, guardar(ROLES + rolAdmin, admin, Map.of("nombre", "Admin", "descripcion", "x",
                "permisos", List.of("gestionar_roles"))));
        assertEquals("Admin", r.en("nombre"));
    }

    @Test
    void quitarGestionarRolesAOtroRolSiEsPosible() {
        UUID otro = crearRol("Otro", "gestionar_roles");
        crearUsuario("otro", otro);

        exigir(200, guardar(ROLES + otro, admin, Map.of("nombre", "Otro", "permisos", List.of("ver_panel"))));
    }

    @Test
    void unRolConUsuariosNoSeElimina() {
        UUID recepcion = crearRol("Recepción", "ver_citas");
        crearUsuario("ana", recepcion);

        exigir(409, "CONFLICTO", eliminar(ROLES + recepcion, admin));

        assertEquals(2, contar("rol"));
    }

    @Test
    void elPropioRolNoSePuedeEliminar() {
        exigir(409, "CONFLICTO", eliminar(ROLES + rolAdmin, admin));
    }

    @Test
    void unRolSinUsuariosSeEliminaConSusPermisos() {
        UUID vacio = crearRol("Vacío", "ver_citas", "ver_panel");

        exigir(204, eliminar(ROLES + vacio, admin));

        assertEquals(1, contar("rol"));
        assertTrue(permisosGuardados(vacio).isEmpty());
    }

    @Test
    void eliminarUnRolInexistenteEs404() {
        exigir(404, "NO_ENCONTRADO", eliminar(ROLES + UUID.randomUUID(), admin));
    }

    @Test
    void elCatalogoDePermisosTraeIdYEtiquetaEnEspanol() {
        Resp r = exigir(200, obtener("/api/panel/permisos", admin));
        assertEquals(9, r.lista("$").size());
        assertEquals("ver_panel", r.en("$[0].id"));
        assertEquals("Ver el panel", r.en("$[0].etiqueta"));
        assertEquals("ver_citas", r.en("$[1].id"));
        assertEquals("gestionar_citas", r.en("$[2].id"));
        assertEquals("Cancelar y mover citas", r.en("$[2].etiqueta"));
        assertEquals("atender_citas", r.en("$[3].id"));
        assertEquals("gestionar_roles", r.en("$[7].id"));
        assertEquals("gestionar_enlaces", r.en("$[8].id"));
        assertFalse(r.texto("$[8].etiqueta").isEmpty());
    }

    @Test
    void nombreEnBlancoEs400ConElCampo() {
        Resp r = exigir(400, "VALIDACION", guardar(ROLES + UUID.randomUUID(), admin,
                Map.of("nombre", "  ", "permisos", List.of())));
        assertEquals("Escribe el nombre del rol.", r.en("campos.nombre"));

        assertEquals(1, contar("rol"));
    }

    @Test
    void nombreYDescripcionDemasiadoLargosSon400ConSusMensajes() {
        Resp r = exigir(400, "VALIDACION", guardar(ROLES + UUID.randomUUID(), admin,
                Map.of("nombre", "n".repeat(101), "descripcion", "d".repeat(301), "permisos", List.of())));
        assertEquals("Hay datos inválidos en la petición.", r.en("mensaje"));
        assertEquals("El nombre es demasiado largo.", r.en("campos.nombre"));
        assertEquals("La descripción es demasiado larga.", r.en("campos.descripcion"));
    }

    @Test
    void unPermisoDesconocidoEs400() {
        exigir(400, "VALIDACION", guardar(ROLES + UUID.randomUUID(), admin,
                Map.of("nombre", "X", "permisos", List.of("volar"))));
        assertEquals(1, contar("rol"));
    }

    @Test
    void unPermisoNuloEnLaListaEs400NoUnError500() {
        Resp r = exigir(400, "VALIDACION", put(ROLES + UUID.randomUUID()).conToken(tokenDe(admin))
                .cuerpoCrudo("{\"nombre\":\"X\",\"permisos\":[\"ver_panel\",null]}").enviar());
        assertNotNull(r.en("mensaje"));
        assertEquals(1, contar("rol"));
    }

    @Test
    void unIdQueNoEsUuidEs400() {
        exigir(400, "VALIDACION", guardar(ROLES + "no-es-uuid", admin, Map.of("nombre", "X", "permisos", List.of())));
    }

    @Test
    void sinTokenEs401() {
        exigir(401, "NO_AUTENTICADO", get("/api/panel/roles").enviar());
        exigir(401, put(ROLES + UUID.randomUUID()).cuerpoCrudo("{\"nombre\":\"x\"}").enviar());
        exigir(401, delete(ROLES + UUID.randomUUID()).enviar());
        exigir(401, get("/api/panel/permisos").enviar());
    }

    @Test
    void sinGestionarRolesNiGestionarUsuariosTodoEs403() {
        UUID soloVer = crearUsuario("vera", crearRol("Vista", "ver_panel", "ver_citas"));
        UUID id = UUID.randomUUID();

        exigir(403, "SIN_PERMISO", obtener("/api/panel/roles", soloVer));
        exigir(403, guardar(ROLES + id, soloVer, Map.of("nombre", "X", "permisos", List.of())));
        exigir(403, eliminar(ROLES + rolAdmin, soloVer));
        exigir(403, obtener("/api/panel/permisos", soloVer));

        assertEquals(2, contar("rol"));
    }

    @Test
    void gestionarUsuariosPermiteListarRolesPeroNoModificarlosNiVerElCatalogo() {
        UUID gestor = crearUsuario("gabi", crearRol("Gestor de usuarios", "gestionar_usuarios"));

        exigir(200, obtener("/api/panel/roles", gestor));
        exigir(403, guardar(ROLES + UUID.randomUUID(), gestor, Map.of("nombre", "X", "permisos", List.of())));
        exigir(403, eliminar(ROLES + rolAdmin, gestor));
        exigir(403, obtener("/api/panel/permisos", gestor));
    }

    @Test
    void dosGestoresQuitandoseElPermisoMutuamenteALosRolesDelOtroNoDejanAlSistemaSinGestorDeRoles()
            throws Exception {
        // Solo A (rol X) y B (rol Y) gestionan roles. A edita Y y B edita X a la vez: ninguno toca su propio rol,
        // así que ninguna petición sola viola la regla del propio rol, pero juntas dejarían a cero gestores.
        ejecutar("DELETE FROM usuario WHERE id = ?", admin);
        UUID rolX = crearRol("X", "gestionar_roles");
        UUID rolY = crearRol("Y", "gestionar_roles");
        UUID a = crearUsuario("a", rolX);
        UUID b = crearUsuario("b", rolY);
        Map<String, Object> sinPermiso = Map.of("nombre", "Sin", "permisos", List.of("ver_panel"));

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Integer> estados = new ArrayList<>();
        try {
            List<Callable<Integer>> tareas = List.of(
                    () -> guardar(ROLES + rolY, a, sinPermiso).estado(),
                    () -> guardar(ROLES + rolX, b, sinPermiso).estado());
            for (Future<Integer> futuro : hilos.invokeAll(tareas)) {
                estados.add(futuro.get());
            }
        } finally {
            hilos.shutdownNow();
        }

        assertEquals(1, estados.stream().filter(e -> e == 200).count(), estados::toString);
        assertTrue(estados.stream().filter(e -> e != 200).allMatch(e -> e == 403 || e == 409), estados::toString);
        assertEquals(1, consultarInt("""
                SELECT COUNT(*) FROM usuario u
                WHERE EXISTS (SELECT 1 FROM rol_permiso rp WHERE rp.rol_id = u.rol_id AND rp.permiso = 'gestionar_roles')
                """));
    }
}
