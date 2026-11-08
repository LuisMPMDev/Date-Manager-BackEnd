package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

@ExtendWith(OutputCaptureExtension.class)
class UsuariosControladorTest extends PruebaPanel {

    private static final String USUARIOS = "/api/panel/usuarios/";

    private UUID rolAdmin;
    private UUID rolRecepcion;
    private UUID admin;

    @BeforeEach
    void crearAdministrador() {
        rolAdmin = crearRol("Administrador", "ver_panel", "ver_citas", "gestionar_usuarios", "gestionar_roles");
        rolRecepcion = crearRol("Recepción", "ver_citas");
        admin = crearUsuario("admin", rolAdmin);
    }

    /** Cuerpo completo de un usuario; {@code clave} nula omite el campo. */
    private Map<String, Object> cuerpo(String usuario, UUID rolId, String clave) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", "Lucía");
        cuerpo.put("apellido", "Rivas");
        cuerpo.put("correo", usuario.strip() + "@example.com");
        cuerpo.put("telefono", "300 555 0202");
        cuerpo.put("usuario", usuario);
        if (clave != null) {
            cuerpo.put("clave", clave);
        }
        cuerpo.put("rolId", rolId.toString());
        return cuerpo;
    }

    private int filasConUsuario(String usuario) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE usuario = ?", Integer.class, usuario);
    }

    private UUID actorSinGestionarRoles(String... permisosDelRol) {
        return crearUsuario("gestor", crearRol("Gestor", permisosDelRol));
    }

    // --- Guardar -----------------------------------------------------------------------------------------

    @Test
    void crearUnUsuarioNuevoGuardaElHashBcryptYNoDevuelveLaClave() throws Exception {
        UUID id = UUID.randomUUID();

        guardar(USUARIOS + id, admin, cuerpo("lucia", rolRecepcion, "Secreta-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nombre").value("Lucía"))
                .andExpect(jsonPath("$.usuario").value("lucia"))
                .andExpect(jsonPath("$.rolId").value(rolRecepcion.toString()))
                .andExpect(jsonPath("$.clave").doesNotExist())
                .andExpect(jsonPath("$.claveHash").doesNotExist())
                .andExpect(content().string(not(containsString("Secreta-123"))))
                .andExpect(content().string(not(containsString("$2"))));

        String hash = hashDe(id);
        assertThat(hash).startsWith("$2").doesNotContain("Secreta-123");
        assertThat(codificador.matches("Secreta-123", hash)).isTrue();
    }

    @Test
    void crearSinClaveEs400ConElCampo() throws Exception {
        for (String clave : new String[] { null, "", "   " }) {
            guardar(USUARIOS + UUID.randomUUID(), admin, cuerpo("lucia", rolRecepcion, clave))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.campos.clave").isNotEmpty());
        }
        assertThat(filasConUsuario("lucia")).isZero();
    }

    @Test
    void editarConClaveVaciaOAusenteConservaElHash() throws Exception {
        UUID ana = crearUsuario("ana", rolRecepcion, "clave-de-ana");
        String hashAntes = hashDe(ana);

        Map<String, Object> cambios = cuerpo("ana", rolRecepcion, "");
        cambios.put("nombre", "Ana María");
        guardar(USUARIOS + ana, admin, cambios)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana María"));
        assertThat(hashDe(ana)).isEqualTo(hashAntes);

        guardar(USUARIOS + ana, admin, cuerpo("ana", rolRecepcion, null)).andExpect(status().isOk());
        assertThat(hashDe(ana)).isEqualTo(hashAntes);
        assertThat(codificador.matches("clave-de-ana", hashDe(ana))).isTrue();
    }

    @Test
    void editarConClaveNuevaCambiaElHash() throws Exception {
        UUID ana = crearUsuario("ana", rolRecepcion, "clave-de-ana");
        String hashAntes = hashDe(ana);

        guardar(USUARIOS + ana, admin, cuerpo("ana", rolRecepcion, "otra-clave-nueva"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("otra-clave-nueva"))));

        assertThat(hashDe(ana)).isNotEqualTo(hashAntes).startsWith("$2");
        assertThat(codificador.matches("otra-clave-nueva", hashDe(ana))).isTrue();
        assertThat(codificador.matches("clave-de-ana", hashDe(ana))).isFalse();
    }

    @Test
    void usuarioYCorreoSeNormalizanAlGuardar() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> datos = cuerpo(" Lucia ", rolRecepcion, "abc12345");
        datos.put("correo", "  Lucia@Example.COM ");

        guardar(USUARIOS + id, admin, datos)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("lucia"))
                .andExpect(jsonPath("$.correo").value("lucia@example.com"));

        assertThat(jdbc.queryForObject("SELECT usuario FROM usuario WHERE id = ?", String.class, id))
                .isEqualTo("lucia");
    }

    @Test
    void usuarioDuplicadoSinImportarEspaciosNiMayusculasEs409YNuncaHayDosFilas() throws Exception {
        crearUsuario("marta", rolRecepcion);

        guardar(USUARIOS + UUID.randomUUID(), admin, cuerpo("Marta ", rolRecepcion, "abc12345"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        guardar(USUARIOS + UUID.randomUUID(), admin, cuerpo(" MARTA", rolRecepcion, "abc12345"))
                .andExpect(status().isConflict());

        assertThat(filasConUsuario("marta")).isEqualTo(1);
        assertThat(contar("usuario")).isEqualTo(2);
    }

    @Test
    void cambiarElUsuarioDeOtroAUnoQueYaExisteEs409() throws Exception {
        crearUsuario("marta", rolRecepcion);
        UUID ana = crearUsuario("ana", rolRecepcion);

        guardar(USUARIOS + ana, admin, cuerpo("Marta", rolRecepcion, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));

        assertThat(jdbc.queryForObject("SELECT usuario FROM usuario WHERE id = ?", String.class, ana))
                .isEqualTo("ana");
    }

    @Test
    void conservarElPropioUsuarioConOtraMayusculaNoEsDuplicado() throws Exception {
        UUID ana = crearUsuario("ana", rolRecepcion);

        guardar(USUARIOS + ana, admin, cuerpo("Ana ", rolRecepcion, null)).andExpect(status().isOk());
    }

    @Test
    void dosPeticionesSimultaneasConElMismoUsuarioDejanUnaSolaFila() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tareas = new ArrayList<>();
            for (String variante : new String[] { "carla", " Carla " }) {
                tareas.add(() -> guardar(USUARIOS + UUID.randomUUID(), admin, cuerpo(variante, rolRecepcion, "abc12345"))
                        .andReturn().getResponse().getStatus());
            }
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> futuro : hilos.invokeAll(tareas)) {
                estados.add(futuro.get());
            }
            assertThat(estados).containsExactlyInAnyOrder(200, 409);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(filasConUsuario("carla")).isEqualTo(1);
    }

    @Test
    void correoInvalidoEs400() throws Exception {
        for (String correo : new String[] { "lucia", "lucia@correo", "lucia@@example.com", "a b@example.com" }) {
            Map<String, Object> datos = cuerpo("lucia", rolRecepcion, "abc12345");
            datos.put("correo", correo);
            guardar(USUARIOS + UUID.randomUUID(), admin, datos)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.campos.correo").isNotEmpty());
        }
        assertThat(filasConUsuario("lucia")).isZero();
    }

    @Test
    void losCamposObligatoriosEnBlancoSon400ConSusCampos() throws Exception {
        Map<String, Object> datos = cuerpo("lucia", rolRecepcion, "abc12345");
        datos.put("nombre", " ");
        datos.put("apellido", "");
        datos.put("usuario", "  ");
        datos.put("correo", "");

        guardar(USUARIOS + UUID.randomUUID(), admin, datos)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.nombre").isNotEmpty())
                .andExpect(jsonPath("$.campos.apellido").isNotEmpty())
                .andExpect(jsonPath("$.campos.usuario").isNotEmpty())
                .andExpect(jsonPath("$.campos.correo").isNotEmpty());
    }

    @Test
    void elTelefonoEsOpcional() throws Exception {
        Map<String, Object> datos = cuerpo("lucia", rolRecepcion, "abc12345");
        datos.remove("telefono");

        guardar(USUARIOS + UUID.randomUUID(), admin, datos).andExpect(status().isOk());
    }

    @Test
    void unRolInexistenteOAusenteEs400ConElCampoRolId() throws Exception {
        Map<String, Object> inexistente = cuerpo("lucia", rolRecepcion, "abc12345");
        inexistente.put("rolId", UUID.randomUUID().toString());
        guardar(USUARIOS + UUID.randomUUID(), admin, inexistente)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.rolId").isNotEmpty());

        Map<String, Object> ausente = cuerpo("lucia", rolRecepcion, "abc12345");
        ausente.remove("rolId");
        guardar(USUARIOS + UUID.randomUUID(), admin, ausente)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.rolId").isNotEmpty());

        assertThat(filasConUsuario("lucia")).isZero();
    }

    @Test
    void unaClaveDeMasDe72BytesEs400PorqueBcryptNoLaSoporta() throws Exception {
        guardar(USUARIOS + UUID.randomUUID(), admin, cuerpo("lucia", rolRecepcion, "x".repeat(73)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.clave").isNotEmpty());
    }

    // --- Listar ------------------------------------------------------------------------------------------

    @Test
    void listarNoIncluyeLaClaveNiElHash() throws Exception {
        crearUsuario("ana", rolRecepcion, "clave-de-ana");

        obtener("/api/panel/usuarios", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].usuario").value("admin"))
                .andExpect(jsonPath("$[1].usuario").value("ana"))
                .andExpect(jsonPath("$[0].clave").doesNotExist())
                .andExpect(jsonPath("$[0].claveHash").doesNotExist())
                .andExpect(content().string(not(containsString("$2"))))
                .andExpect(content().string(not(containsString("clave-de-ana"))));
    }

    // --- Escalada de privilegios --------------------------------------------------------------------------

    @Test
    void sinGestionarRolesNoSePuedeAsignarUnRolConPermisosDeMas() throws Exception {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas");
        UUID masPoderoso = crearRol("Con servicios", "ver_citas", "gestionar_servicios");

        guardar(USUARIOS + UUID.randomUUID(), gestor, cuerpo("lucia", masPoderoso, "abc12345"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        guardar(USUARIOS + UUID.randomUUID(), gestor, cuerpo("lucia", rolAdmin, "abc12345"))
                .andExpect(status().isForbidden());

        assertThat(filasConUsuario("lucia")).isZero();
    }

    @Test
    void sinGestionarRolesSiSePuedeAsignarUnRolConUnSubconjunto() throws Exception {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas", "ver_panel");

        guardar(USUARIOS + UUID.randomUUID(), gestor, cuerpo("lucia", rolRecepcion, "abc12345"))
                .andExpect(status().isOk());
        // Un rol idéntico al propio también es un subconjunto.
        UUID igual = crearRol("Igual", "gestionar_usuarios", "ver_citas", "ver_panel");
        guardar(USUARIOS + UUID.randomUUID(), gestor, cuerpo("lola", igual, "abc12345"))
                .andExpect(status().isOk());
    }

    @Test
    void sinGestionarRolesTampocoSePuedeSubirDeRolAUnUsuarioExistente() throws Exception {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas");
        UUID ana = crearUsuario("ana", rolRecepcion);

        guardar(USUARIOS + ana, gestor, cuerpo("ana", rolAdmin, null)).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT rol_id FROM usuario WHERE id = ?", UUID.class, ana))
                .isEqualTo(rolRecepcion);
    }

    @Test
    void sinGestionarRolesNoSePuedeEditarAUnUsuarioConUnRolMasPoderosoNiCambiarleLaClave() throws Exception {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas");
        String hashAntes = hashDe(admin);

        // El rol que quedaría es el del administrador: supera los permisos del actor.
        guardar(USUARIOS + admin, gestor, cuerpo("admin", rolAdmin, "toma-el-control"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));

        assertThat(hashDe(admin)).isEqualTo(hashAntes);
    }

    @Test
    void conGestionarRolesSePuedeAsignarCualquierRol() throws Exception {
        guardar(USUARIOS + UUID.randomUUID(), admin, cuerpo("lucia", rolAdmin, "abc12345"))
                .andExpect(status().isOk());
    }

    // --- Protecciones de último administrador y de uno mismo ---------------------------------------------

    @Test
    void nadieSeEliminaASiMismo() throws Exception {
        crearUsuario("otro-admin", rolAdmin);

        eliminar(USUARIOS + admin, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));

        assertThat(filasConUsuario("admin")).isEqualTo(1);
    }

    @Test
    void noSeEliminaAlUltimoUsuarioConGestionarRoles() throws Exception {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios");

        eliminar(USUARIOS + admin, gestor)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));

        assertThat(filasConUsuario("admin")).isEqualTo(1);
    }

    @Test
    void sePuedeEliminarAUnGestorDeRolesSiQuedaOtro() throws Exception {
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);

        eliminar(USUARIOS + otroAdmin, admin).andExpect(status().isNoContent());

        assertThat(contar("usuario")).isEqualTo(1);
    }

    @Test
    void sePuedeEliminarAUnUsuarioComun() throws Exception {
        UUID ana = crearUsuario("ana", rolRecepcion);

        eliminar(USUARIOS + ana, admin).andExpect(status().isNoContent());

        assertThat(filasConUsuario("ana")).isZero();
    }

    @Test
    void eliminarUnUsuarioInexistenteEs404() throws Exception {
        eliminar(USUARIOS + UUID.randomUUID(), admin)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void noSeDegradaAlUltimoUsuarioConGestionarRoles() throws Exception {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas");

        guardar(USUARIOS + admin, gestor, cuerpo("admin", rolRecepcion, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));

        assertThat(jdbc.queryForObject("SELECT rol_id FROM usuario WHERE id = ?", UUID.class, admin))
                .isEqualTo(rolAdmin);
    }

    @Test
    void elUnicoAdministradorNoPuedeDegradarseASiMismo() throws Exception {
        guardar(USUARIOS + admin, admin, cuerpo("admin", rolRecepcion, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

    @Test
    void sePuedeDegradarAUnGestorDeRolesSiQuedaOtro() throws Exception {
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);

        guardar(USUARIOS + otroAdmin, admin, cuerpo("otro-admin", rolRecepcion, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolId").value(rolRecepcion.toString()));
    }

    @Test
    void dosDegradacionesSimultaneasNoDejanAlSistemaSinGestorDeRoles() throws Exception {
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            // Cada uno se degrada a sí mismo a la vez: solo uno puede salir bien.
            List<Callable<Integer>> tareas = List.of(
                    () -> guardar(USUARIOS + admin, admin, cuerpo("admin", rolRecepcion, null))
                            .andReturn().getResponse().getStatus(),
                    () -> guardar(USUARIOS + otroAdmin, otroAdmin, cuerpo("otro-admin", rolRecepcion, null))
                            .andReturn().getResponse().getStatus());
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> futuro : hilos.invokeAll(tareas)) {
                estados.add(futuro.get());
            }
            assertThat(estados).containsExactlyInAnyOrder(200, 409);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE rol_id = ?", Integer.class, rolAdmin))
                .isEqualTo(1);
    }

    // --- Permisos de la ruta ------------------------------------------------------------------------------

    @Test
    void sinTokenEs401() throws Exception {
        mvc.perform(get("/api/panel/usuarios")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(put(USUARIOS + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinGestionarUsuariosTodoEs403AunqueSeaGestorDeRoles() throws Exception {
        UUID soloRoles = crearUsuario("rosa", crearRol("Solo roles", "gestionar_roles"));
        UUID ana = crearUsuario("ana", rolRecepcion);

        obtener("/api/panel/usuarios", soloRoles).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        guardar(USUARIOS + UUID.randomUUID(), soloRoles, cuerpo("lucia", rolRecepcion, "abc12345"))
                .andExpect(status().isForbidden());
        eliminar(USUARIOS + ana, soloRoles).andExpect(status().isForbidden());

        assertThat(contar("usuario")).isEqualTo(3);
        assertThat(filasConUsuario("lucia")).isZero();
    }

    // --- Ningún rastro de la clave ------------------------------------------------------------------------

    @Test
    void laClaveNuncaApareceEnRespuestasDeErrorNiEnElLog(CapturedOutput salida) throws Exception {
        crearUsuario("marta", rolRecepcion);
        String clave = "Clave-Ultra-Secreta-987";

        // Error de validación, de conflicto y de permiso, todos con la clave en el cuerpo de la petición.
        Map<String, Object> invalido = cuerpo("lucia", rolRecepcion, clave);
        invalido.put("correo", "malo");
        String respuestas = guardar(USUARIOS + UUID.randomUUID(), admin, invalido)
                .andReturn().getResponse().getContentAsString();
        respuestas += guardar(USUARIOS + UUID.randomUUID(), admin, cuerpo("marta", rolRecepcion, clave))
                .andReturn().getResponse().getContentAsString();
        respuestas += guardar(USUARIOS + UUID.randomUUID(), actorSinGestionarRoles("gestionar_usuarios"),
                cuerpo("lola", rolAdmin, clave)).andReturn().getResponse().getContentAsString();

        assertThat(respuestas).isNotEmpty().doesNotContain(clave);
        assertThat(salida.getAll()).doesNotContain(clave);
    }

    @Test
    void laPeticionYElDtoNoMuestranLaClaveEnToString() {
        var peticion = new UsuarioPeticion("A", "B", "a@b.co", "1", "ab", "Clave-Que-No-Debe-Salir", UUID.randomUUID());

        assertThat(peticion.toString()).doesNotContain("Clave-Que-No-Debe-Salir");
    }
}
