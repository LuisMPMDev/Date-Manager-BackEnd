package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class RolesControladorTest extends PruebaPanel {

    private static final String ROLES = "/api/panel/roles/";

    private UUID rolAdmin;
    private UUID admin;

    @BeforeEach
    void crearAdministrador() {
        rolAdmin = crearRol("Administrador", "ver_panel", "gestionar_usuarios", "gestionar_roles");
        admin = crearUsuario("admin", rolAdmin);
    }

    private List<String> permisosGuardados(UUID rolId) {
        return jdbc.queryForList("SELECT permiso FROM rol_permiso WHERE rol_id = ? ORDER BY permiso",
                String.class, rolId);
    }

    @Test
    void guardarUnRolConGestionarCitasIncluyeVerCitas() throws Exception {
        UUID id = UUID.randomUUID();

        guardar(ROLES + id, admin, Map.of("nombre", "Agenda", "descripcion", "Maneja citas",
                "permisos", List.of("gestionar_citas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nombre").value("Agenda"))
                .andExpect(jsonPath("$.descripcion").value("Maneja citas"))
                .andExpect(jsonPath("$.permisos").value(contains("ver_citas", "gestionar_citas")));

        assertThat(permisosGuardados(id)).containsExactly("gestionar_citas", "ver_citas");
    }

    @Test
    void atenderCitasTambienExigeVerCitas() throws Exception {
        UUID id = UUID.randomUUID();

        guardar(ROLES + id, admin, Map.of("nombre", "Médico", "permisos", List.of("atender_citas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos").value(contains("ver_citas", "atender_citas")));
    }

    @Test
    void putCreaSiNoExisteConElIdDeLaRutaYLuegoActualiza() throws Exception {
        UUID id = UUID.randomUUID();

        guardar(ROLES + id, admin, Map.of("nombre", "Recepción", "descripcion", "Agenda",
                "permisos", List.of("ver_citas")))
                .andExpect(status().isOk());
        assertThat(contar("rol")).isEqualTo(2);

        guardar(ROLES + id, admin, Map.of("nombre", "Recepción 2", "descripcion", "Otra",
                "permisos", List.of("ver_panel", "ver_citas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nombre").value("Recepción 2"))
                .andExpect(jsonPath("$.permisos").value(contains("ver_panel", "ver_citas")));

        assertThat(contar("rol")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT nombre FROM rol WHERE id = ?", String.class, id))
                .isEqualTo("Recepción 2");
        assertThat(permisosGuardados(id)).containsExactly("ver_citas", "ver_panel");
    }

    @Test
    void listarDevuelveLosRolesConPermisosEnElOrdenDelCatalogo() throws Exception {
        crearRol("Recepción", "ver_citas", "ver_panel");

        obtener("/api/panel/roles", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.nombre == 'Recepción')].permisos")
                        .value(contains(List.of("ver_panel", "ver_citas"))));
    }

    @Test
    void elAdministradorNoPuedeQuitarseGestionarRolesDeSuPropioRol() throws Exception {
        guardar(ROLES + rolAdmin, admin, Map.of("nombre", "Administrador",
                "permisos", List.of("ver_panel", "gestionar_usuarios")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());

        assertThat(permisosGuardados(rolAdmin)).contains("gestionar_roles");
    }

    @Test
    void sePuedeEditarElPropioRolSiConservaGestionarRoles() throws Exception {
        guardar(ROLES + rolAdmin, admin, Map.of("nombre", "Admin", "descripcion", "x",
                "permisos", List.of("gestionar_roles")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Admin"));
    }

    @Test
    void quitarGestionarRolesAOtroRolSiEsPosible() throws Exception {
        UUID otro = crearRol("Otro", "gestionar_roles");
        crearUsuario("otro", otro);

        guardar(ROLES + otro, admin, Map.of("nombre", "Otro", "permisos", List.of("ver_panel")))
                .andExpect(status().isOk());
    }

    @Test
    void unRolConUsuariosNoSeElimina() throws Exception {
        UUID recepcion = crearRol("Recepción", "ver_citas");
        crearUsuario("ana", recepcion);

        eliminar(ROLES + recepcion, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));

        assertThat(contar("rol")).isEqualTo(2);
    }

    @Test
    void elPropioRolNoSePuedeEliminar() throws Exception {
        eliminar(ROLES + rolAdmin, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

    @Test
    void unRolSinUsuariosSeEliminaConSusPermisos() throws Exception {
        UUID vacio = crearRol("Vacío", "ver_citas", "ver_panel");

        eliminar(ROLES + vacio, admin).andExpect(status().isNoContent());

        assertThat(contar("rol")).isEqualTo(1);
        assertThat(permisosGuardados(vacio)).isEmpty();
    }

    @Test
    void eliminarUnRolInexistenteEs404() throws Exception {
        eliminar(ROLES + UUID.randomUUID(), admin)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void elCatalogoDePermisosTraeIdYEtiquetaEnEspanol() throws Exception {
        obtener("/api/panel/permisos", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].id").value("ver_panel"))
                .andExpect(jsonPath("$[0].etiqueta").value("Ver el panel"))
                .andExpect(jsonPath("$[1].id").value("ver_citas"))
                .andExpect(jsonPath("$[2].id").value("gestionar_citas"))
                .andExpect(jsonPath("$[2].etiqueta").value("Cancelar y mover citas"))
                .andExpect(jsonPath("$[3].id").value("atender_citas"))
                .andExpect(jsonPath("$[7].id").value("gestionar_roles"))
                .andExpect(jsonPath("$[8].id").value("gestionar_enlaces"))
                .andExpect(jsonPath("$[8].etiqueta").isNotEmpty());
    }

    @Test
    void nombreEnBlancoEs400ConElCampo() throws Exception {
        guardar(ROLES + UUID.randomUUID(), admin, Map.of("nombre", "  ", "permisos", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.nombre").isNotEmpty());

        assertThat(contar("rol")).isEqualTo(1);
    }

    @Test
    void unPermisoDesconocidoEs400() throws Exception {
        guardar(ROLES + UUID.randomUUID(), admin, Map.of("nombre", "X", "permisos", List.of("volar")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void unIdQueNoEsUuidEs400() throws Exception {
        guardar(ROLES + "no-es-uuid", admin, Map.of("nombre", "X", "permisos", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void sinTokenEs401() throws Exception {
        mvc.perform(get("/api/panel/roles")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(put(ROLES + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete(ROLES + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/panel/permisos")).andExpect(status().isUnauthorized());
    }

    @Test
    void sinGestionarRolesNiGestionarUsuariosTodoEs403() throws Exception {
        UUID soloVer = crearUsuario("vera", crearRol("Vista", "ver_panel", "ver_citas"));
        UUID id = UUID.randomUUID();

        obtener("/api/panel/roles", soloVer).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        guardar(ROLES + id, soloVer, Map.of("nombre", "X", "permisos", List.of()))
                .andExpect(status().isForbidden());
        eliminar(ROLES + rolAdmin, soloVer).andExpect(status().isForbidden());
        obtener("/api/panel/permisos", soloVer).andExpect(status().isForbidden());

        assertThat(contar("rol")).isEqualTo(2);
    }

    @Test
    void gestionarUsuariosPermiteListarRolesPeroNoModificarlosNiVerElCatalogo() throws Exception {
        UUID gestor = crearUsuario("gabi", crearRol("Gestor de usuarios", "gestionar_usuarios"));

        obtener("/api/panel/roles", gestor).andExpect(status().isOk());
        guardar(ROLES + UUID.randomUUID(), gestor, Map.of("nombre", "X", "permisos", List.of()))
                .andExpect(status().isForbidden());
        eliminar(ROLES + rolAdmin, gestor).andExpect(status().isForbidden());
        obtener("/api/panel/permisos", gestor).andExpect(status().isForbidden());
    }
}
