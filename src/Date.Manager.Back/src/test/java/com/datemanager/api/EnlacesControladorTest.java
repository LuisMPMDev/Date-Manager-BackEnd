package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class EnlacesControladorTest extends PruebaPanel {

    private static final String ENLACES = "/api/panel/enlaces";
    /** Lunes 9 de noviembre de 2026, 08:00 en Bogotá = 13:00 UTC. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 8, 0);
    private static final String AHORA_UTC = "2026-11-09T13:00:00Z";

    private UUID rolAdmin;
    private UUID admin;

    @BeforeEach
    void preparar() {
        fijarAhora(AHORA);
        rolAdmin = crearRol("Administrador", "ver_panel", "ver_citas", "gestionar_usuarios", "gestionar_roles",
                "gestionar_enlaces");
        admin = crearUsuario("admin", rolAdmin);
        jdbc.update("UPDATE usuario SET nombre = 'Marta', apellido = 'Gómez' WHERE id = ?", admin);
    }

    // ---------- Utilidades ----------

    private ResultActions crear(UUID actor, Map<String, ?> cuerpo) throws Exception {
        return mvc.perform(post(ENLACES).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private JsonNode crearOk(Map<String, ?> cuerpo) throws Exception {
        String respuesta = crear(admin, cuerpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta);
    }

    private ResultActions desactivar(UUID actor, String id) throws Exception {
        return mvc.perform(post(ENLACES + "/" + id + "/desactivar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(actor)));
    }

    private static Map<String, Object> cuerpo(Integer duracion, Integer maxUsos) {
        Map<String, Object> c = new HashMap<>();
        if (duracion != null) {
            c.put("duracionMinutos", duracion);
        }
        if (maxUsos != null) {
            c.put("maxUsos", maxUsos);
        }
        return c;
    }

    // ---------- Crear ----------

    @Test
    void crearSinDatosDura24HorasSinLimiteDeUsos() throws Exception {
        JsonNode enlace = json.readTree(crear(admin, Map.of())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.estado").value("Activo"))
                .andExpect(jsonPath("$.usos").value(0))
                .andExpect(jsonPath("$.maxUsos").doesNotExist())
                .andExpect(jsonPath("$.creadoEn").value(AHORA_UTC))
                .andExpect(jsonPath("$.venceEn").value("2026-11-10T13:00:00Z"))
                .andExpect(jsonPath("$.creadoPor").value(admin.toString()))
                .andExpect(jsonPath("$.creadoPorNombre").value("Marta Gómez"))
                .andReturn().getResponse().getContentAsString());

        String codigo = enlace.get("codigo").asText();
        assertThat(codigo).hasSizeGreaterThanOrEqualTo(22).matches("[A-Za-z0-9_-]+");
        assertThat(enlace.get("ruta").asText()).isEqualTo("/agendar/" + codigo);
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM enlace_invitacion");
        assertThat(fila.get("codigo")).isEqualTo(codigo);
        assertThat(fila.get("usos")).isEqualTo(0);
        assertThat(fila.get("max_usos")).isNull();
        assertThat(fila.get("desactivado_en")).isNull();
        assertThat(fila.get("creado_por").toString()).isEqualToIgnoringCase(admin.toString());
    }

    @Test
    void crearConDuracionYMaximoDeUsos() throws Exception {
        crear(admin, cuerpo(60, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxUsos").value(3))
                .andExpect(jsonPath("$.venceEn").value("2026-11-09T14:00:00Z"));
        crear(admin, cuerpo(43200, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.venceEn").value("2026-12-09T13:00:00Z"));
    }

    @Test
    void duracionFueraDeRangoOMaximoDeUsosCeroSon400YNoSeGuardaNada() throws Exception {
        crear(admin, cuerpo(59, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.duracionMinutos").isNotEmpty());
        crear(admin, cuerpo(43201, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.duracionMinutos").isNotEmpty());
        crear(admin, cuerpo(null, 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.maxUsos").isNotEmpty());
        crear(admin, cuerpo(1440, -2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.maxUsos").isNotEmpty());
        crear(admin, Map.of("duracionMinutos", "mucho")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        assertThat(contar("enlace_invitacion")).isZero();
    }

    @Test
    void cadaEnlaceTieneUnCodigoDistinto() throws Exception {
        String a = crearOk(Map.of()).get("codigo").asText();
        String b = crearOk(Map.of()).get("codigo").asText();

        assertThat(a).isNotEqualTo(b);
    }

    // ---------- Listar ----------

    @Test
    void listarDevuelveLosMasNuevosPrimeroConEstadoUsosVencimientoYCreador() throws Exception {
        UUID otro = crearUsuario("luis", rolAdmin);
        jdbc.update("UPDATE usuario SET nombre = 'Luis', apellido = 'Paz' WHERE id = ?", otro);
        JsonNode viejo = crearOk(cuerpo(60, 2));
        fijarAhora(AHORA.plusMinutes(10));
        String nuevo = json.readTree(crear(otro, Map.of()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        jdbc.update("UPDATE enlace_invitacion SET usos = 1 WHERE id = ?", UUID.fromString(viejo.get("id").asText()));
        fijarAhora(AHORA.plusMinutes(60)); // el primero vence justo ahora

        obtener(ENLACES, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(nuevo))
                .andExpect(jsonPath("$[0].estado").value("Activo"))
                .andExpect(jsonPath("$[0].creadoPor").value(otro.toString()))
                .andExpect(jsonPath("$[0].creadoPorNombre").value("Luis Paz"))
                .andExpect(jsonPath("$[0].creadoEn").value("2026-11-09T13:10:00Z"))
                .andExpect(jsonPath("$[1].id").value(viejo.get("id").asText()))
                .andExpect(jsonPath("$[1].estado").value("Vencido"))
                .andExpect(jsonPath("$[1].usos").value(1))
                .andExpect(jsonPath("$[1].maxUsos").value(2))
                .andExpect(jsonPath("$[1].venceEn").value("2026-11-09T14:00:00Z"))
                .andExpect(jsonPath("$[1].codigo").value(viejo.get("codigo").asText()))
                .andExpect(jsonPath("$[1].ruta").value("/agendar/" + viejo.get("codigo").asText()))
                .andExpect(jsonPath("$[1].creadoPorNombre").value("Marta Gómez"));
    }

    @Test
    void listarMuestraAgotadoYDesactivado() throws Exception {
        JsonNode agotado = crearOk(cuerpo(null, 1));
        fijarAhora(AHORA.plusMinutes(1));
        JsonNode desactivado = crearOk(Map.of());
        jdbc.update("UPDATE enlace_invitacion SET usos = 1 WHERE id = ?",
                UUID.fromString(agotado.get("id").asText()));
        desactivar(admin, desactivado.get("id").asText()).andExpect(status().isOk());

        obtener(ENLACES, admin)
                .andExpect(jsonPath("$[0].estado").value("Desactivado"))
                .andExpect(jsonPath("$[1].estado").value("Agotado"));
    }

    // ---------- Desactivar ----------

    @Test
    void desactivarEsIdempotenteYConservaLaPrimeraFecha() throws Exception {
        String id = crearOk(Map.of()).get("id").asText();
        fijarAhora(AHORA.plusMinutes(5));

        desactivar(admin, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Desactivado"))
                .andExpect(jsonPath("$.id").value(id));
        Object primera = jdbc.queryForObject("SELECT desactivado_en FROM enlace_invitacion", Object.class);
        fijarAhora(AHORA.plusMinutes(30));
        desactivar(admin, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Desactivado"));

        assertThat(jdbc.queryForObject("SELECT desactivado_en FROM enlace_invitacion", Object.class))
                .isEqualTo(primera);
        assertThat(primera.toString()).startsWith("2026-11-09 13:05:00");
    }

    @Test
    void desactivarUnoVencidoLoDejaDesactivado() throws Exception {
        String id = crearOk(cuerpo(60, null)).get("id").asText();
        fijarAhora(AHORA.plusDays(3));

        desactivar(admin, id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("Desactivado"));
    }

    @Test
    void desactivarUnoInexistenteEs404() throws Exception {
        desactivar(admin, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    // ---------- Permisos ----------

    @Test
    void sinGestionarEnlacesTodoEs403() throws Exception {
        String id = crearOk(Map.of()).get("id").asText();
        UUID sinPermiso = crearUsuario("recepcion", crearRol("Recepción", "ver_panel", "ver_citas",
                "gestionar_citas", "gestionar_servicios", "gestionar_clientes", "gestionar_usuarios"));

        crear(sinPermiso, Map.of()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        obtener(ENLACES, sinPermiso).andExpect(status().isForbidden());
        desactivar(sinPermiso, id).andExpect(status().isForbidden());

        assertThat(contar("enlace_invitacion")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT desactivado_en FROM enlace_invitacion", Object.class)).isNull();
    }

    @Test
    void soloGestionarEnlacesBasta() throws Exception {
        UUID soloEnlaces = crearUsuario("enlaces", crearRol("Enlaces", "gestionar_enlaces"));

        String id = json.readTree(crear(soloEnlaces, Map.of()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        obtener(ENLACES, soloEnlaces).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        desactivar(soloEnlaces, id).andExpect(status().isOk());
    }

    @Test
    void sinTokenOConTokenInvalidoEs401() throws Exception {
        String id = crearOk(Map.of()).get("id").asText();

        mvc.perform(get(ENLACES)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(post(ENLACES).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ENLACES + "/" + id + "/desactivar")).andExpect(status().isUnauthorized());
        mvc.perform(get(ENLACES).header(HttpHeaders.AUTHORIZATION, "Bearer basura.que.no-vale"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ENLACES).header(HttpHeaders.AUTHORIZATION, "Bearer basura")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

        assertThat(contar("enlace_invitacion")).isEqualTo(1);
    }

    // ---------- Usuarios que crearon enlaces ----------

    @Test
    void noSeEliminaAUnUsuarioQueCreoEnlaces() throws Exception {
        UUID luis = crearUsuario("luis", rolAdmin);
        crear(luis, Map.of()).andExpect(status().isCreated());

        eliminar("/api/panel/usuarios/" + luis, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("enlaces")));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE id = ?", Integer.class, luis))
                .isEqualTo(1);
        assertThat(contar("enlace_invitacion")).isEqualTo(1);
    }

    @Test
    void lasProteccionesDeUsuariosSiguenAntesQueLaDeEnlaces() throws Exception {
        // Un actor sin gestionar_roles no puede tocar a un superior: 403, aunque el superior tenga enlaces.
        UUID gestor = crearUsuario("gestor", crearRol("Gestor", "gestionar_usuarios"));
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);
        crear(otroAdmin, Map.of()).andExpect(status().isCreated());

        eliminar("/api/panel/usuarios/" + otroAdmin, gestor).andExpect(status().isForbidden());
        // Y nadie se elimina a sí mismo (409 de siempre).
        eliminar("/api/panel/usuarios/" + admin, admin).andExpect(status().isConflict());

        assertThat(contar("usuario")).isEqualTo(3);
    }

    @Test
    void unUsuarioSinEnlacesSeSigueEliminando() throws Exception {
        UUID luis = crearUsuario("luis", rolAdmin);
        crear(admin, Map.of()).andExpect(status().isCreated());

        eliminar("/api/panel/usuarios/" + luis, admin).andExpect(status().isNoContent());
    }

    // ---------- Restricciones de la base ----------

    private void insertarEnlace(UUID id, String codigo, Integer maxUsos, int usos) {
        jdbc.update("INSERT INTO enlace_invitacion (id, codigo, creado_por, creado_en, vence_en, max_usos, usos) "
                + "VALUES (?, ?, ?, '2026-11-09T13:00:00', '2026-11-10T13:00:00', ?, ?)",
                id, codigo, admin, maxUsos, usos);
    }

    @Test
    void laBaseImponeSusRestricciones() {
        insertarEnlace(UUID.randomUUID(), "AAAAAAAAAAAAAAAAAAAAAA", null, 0);

        // Código único.
        assertThatThrownBy(() -> insertarEnlace(UUID.randomUUID(), "AAAAAAAAAAAAAAAAAAAAAA", null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        // El código distingue mayúsculas (Base64 URL): otro en minúsculas es otro código.
        insertarEnlace(UUID.randomUUID(), "aaaaaaaaaaaaaaaaaaaaaa", null, 0);
        // max_usos nulo o >= 1; usos >= 0 y nunca por encima del máximo.
        assertThatThrownBy(() -> insertarEnlace(UUID.randomUUID(), "BBBBBBBBBBBBBBBBBBBBBB", 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarEnlace(UUID.randomUUID(), "CCCCCCCCCCCCCCCCCCCCCC", null, -1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarEnlace(UUID.randomUUID(), "DDDDDDDDDDDDDDDDDDDDDD", 1, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
        // creado_por es una llave foránea a usuario.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO enlace_invitacion "
                + "(id, codigo, creado_por, creado_en, vence_en, usos) VALUES (?, 'EEEEEEEEEEEEEEEEEEEEEE', ?, "
                + "'2026-11-09T13:00:00', '2026-11-10T13:00:00', 0)", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Borrar el usuario por SQL choca con la llave (sin cascada).
        assertThatThrownBy(() -> jdbc.update("DELETE FROM usuario WHERE id = ?", admin))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(contar("enlace_invitacion")).isEqualTo(2);
    }

    @Test
    void laCitaSoloPuedeApuntarAUnEnlaceExistente() {
        UUID servicio = UUID.randomUUID();
        jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'S', 30, 1, 0)",
                servicio);
        UUID cliente = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, 'A', 'B', '1', 'a@x.co')",
                cliente);
        UUID enlace = UUID.randomUUID();
        insertarEnlace(enlace, "AAAAAAAAAAAAAAAAAAAAAA", null, 0);
        String sql = "INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en, "
                + "enlace_id) VALUES (?, ?, ?, '2026-11-10', ?, ?, 'Confirmada', SYSUTCDATETIME(), ?)";

        jdbc.update(sql, UUID.randomUUID(), servicio, cliente, "09:00", "09:30", enlace);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), servicio, cliente, "10:00", "10:30",
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        // Y el enlace con citas no se puede borrar (sin cascada).
        assertThatThrownBy(() -> jdbc.update("DELETE FROM enlace_invitacion WHERE id = ?", enlace))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
