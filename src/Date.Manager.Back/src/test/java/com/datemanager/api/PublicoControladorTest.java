package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

/** Zona del cliente: solo con el código del enlace, sin token. */
class PublicoControladorTest extends PruebaPanel {

    private static final String BASE = "/api/publico/invitaciones/";
    /** Lunes 9 de noviembre de 2026, 08:00 en Bogotá = 13:00 UTC. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 8, 0);
    private static final String MANANA = "2026-11-10";

    private UUID admin;
    /** 30 min, todos los días de 09:00 a 12:00. */
    private UUID corte;
    private UUID inactivo;

    @BeforeEach
    void preparar() {
        fijarAhora(AHORA);
        admin = crearUsuario("admin", crearRol("Administrador", "gestionar_enlaces", "gestionar_citas", "ver_citas"));
        corte = crearServicio("Corte", 30, true);
        crearServicio("Barba", 30, true);
        inactivo = crearServicio("Antiguo", 30, false);
    }

    // ---------- Utilidades ----------

    private UUID crearServicio(String nombre, int duracion, boolean activo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, ?, ?, ?, 10)",
                id, nombre, duracion, activo);
        for (int dia = 1; dia <= 7; dia++) {
            jdbc.update("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                    + "VALUES (?, ?, ?, '09:00', '12:00')", id, UUID.randomUUID(), dia);
        }
        return id;
    }

    /** Enlace creado por el panel; devuelve {id, codigo}. */
    private JsonNode crearEnlace(Integer duracion, Integer maxUsos) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        if (duracion != null) {
            cuerpo.put("duracionMinutos", duracion);
        }
        if (maxUsos != null) {
            cuerpo.put("maxUsos", maxUsos);
        }
        String respuesta = mvc.perform(post("/api/panel/enlaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta);
    }

    private String codigo(Integer duracion, Integer maxUsos) throws Exception {
        return crearEnlace(duracion, maxUsos).get("codigo").asText();
    }

    private void desactivarPorPanel(JsonNode enlace) throws Exception {
        mvc.perform(post("/api/panel/enlaces/" + enlace.get("id").asText() + "/desactivar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(admin))).andExpect(status().isOk());
    }

    private static Map<String, Object> reserva(UUID servicio, String fecha, String inicio, String correo) {
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("nombre", "Ana");
        cliente.put("apellido", "Ruiz");
        cliente.put("telefono", "300 111");
        cliente.put("correo", correo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("servicioId", servicio.toString());
        r.put("fecha", fecha);
        r.put("inicio", inicio);
        r.put("cliente", cliente);
        return r;
    }

    private ResultActions estado(String codigo) throws Exception {
        return mvc.perform(get(BASE + codigo));
    }

    private ResultActions servicios(String codigo) throws Exception {
        return mvc.perform(get(BASE + codigo + "/servicios"));
    }

    private ResultActions horarios(String codigo, Object servicio, String fecha) throws Exception {
        return mvc.perform(get(BASE + codigo + "/servicios/" + servicio + "/horarios").param("fecha", fecha));
    }

    private ResultActions reservar(String codigo, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post(BASE + codigo + "/citas").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions reservar(String codigo, String inicio) throws Exception {
        return reservar(codigo, reserva(corte, MANANA, inicio, "ana@x.co"));
    }

    private int usos(String codigo) {
        return jdbc.queryForObject("SELECT usos FROM enlace_invitacion WHERE codigo = ?", Integer.class, codigo);
    }

    private String cuerpoDe(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    /**
     * Cuerpo de error sin {@code instance} (la ruta que pidió el propio cliente, que Spring repite): todo lo demás
     * debe ser idéntico para no dar pistas.
     */
    private String errorSinRuta(ResultActions r) throws Exception {
        ObjectNode nodo = (ObjectNode) json.readTree(cuerpoDe(r));
        nodo.remove("instance");
        return json.writeValueAsString(nodo);
    }

    // ---------- Código desconocido ----------

    @Test
    void unCodigoDesconocidoEs404SiempreConElMismoCuerpo() throws Exception {
        String existente = codigo(null, null);
        String otraMayuscula = existente.chars().anyMatch(Character::isLowerCase)
                ? existente.toUpperCase() : existente.toLowerCase();
        List<String> desconocidos = List.of(
                "AAAAAAAAAAAAAAAAAAAAAA",        // bien formado, no existe
                "no-existe",                      // mal formado
                otraMayuscula,                    // el real con otras mayúsculas
                existente + " ",                  // el real con un espacio final
                existente.substring(1));          // el real sin su primer carácter

        String referencia = errorSinRuta(estado("AAAAAAAAAAAAAAAAAAAAAA").andExpect(status().isNotFound()));
        assertThat(json.readTree(referencia).get("codigo").asText()).isEqualTo("ENLACE_NO_ENCONTRADO");
        assertThat(json.readTree(referencia).properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("type", "title", "status", "detail", "codigo", "mensaje");

        List<String> cuerpos = new ArrayList<>();
        for (String codigo : desconocidos) {
            cuerpos.add(errorSinRuta(estado(codigo).andExpect(status().isNotFound())));
            cuerpos.add(errorSinRuta(servicios(codigo).andExpect(status().isNotFound())));
            cuerpos.add(errorSinRuta(horarios(codigo, corte, MANANA).andExpect(status().isNotFound())));
            cuerpos.add(errorSinRuta(reservar(codigo, "09:00").andExpect(status().isNotFound())));
        }
        assertThat(cuerpos).allSatisfy(c -> assertThat(c).isEqualTo(referencia));
        assertThat(contar("cita")).isZero();
        assertThat(usos(existente)).isZero();
    }

    // ---------- Estado del enlace ----------

    @Test
    void elEstadoDeUnEnlaceActivoSoloDiceEstadoYVencimiento() throws Exception {
        String codigo = codigo(null, 3);

        JsonNode cuerpo = json.readTree(cuerpoDe(estado(codigo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Activo"))
                .andExpect(jsonPath("$.venceEn").value("2026-11-10T13:00:00Z"))));

        assertThat(cuerpo.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("estado", "venceEn");
    }

    @Test
    void unEnlaceVencidoAgotadoODesactivadoRespondeSuEstadoCon200() throws Exception {
        String vencido = codigo(60, null);
        String agotado = codigo(null, 1);
        JsonNode desactivado = crearEnlace(null, null);
        jdbc.update("UPDATE enlace_invitacion SET usos = 1 WHERE codigo = ?", agotado);
        desactivarPorPanel(desactivado);
        fijarAhora(AHORA.plusMinutes(60)); // vence_en == ahora

        estado(vencido).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("Vencido"));
        estado(agotado).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("Agotado"));
        estado(desactivado.get("codigo").asText()).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Desactivado"));
    }

    @Test
    void unMinutoAntesDeVencerSigueActivo() throws Exception {
        String codigo = codigo(60, null);
        fijarAhora(AHORA.plusMinutes(59));

        estado(codigo).andExpect(jsonPath("$.estado").value("Activo"));
        servicios(codigo).andExpect(status().isOk());
    }

    @Test
    void conUnEnlaceNoActivoNoSeConsultaNiSeReserva() throws Exception {
        String vencido = codigo(60, null);
        String agotado = codigo(null, 1);
        JsonNode desactivado = crearEnlace(null, null);
        jdbc.update("UPDATE enlace_invitacion SET usos = 1 WHERE codigo = ?", agotado);
        desactivarPorPanel(desactivado);
        fijarAhora(AHORA.plusMinutes(60));

        Map<String, String> casos = Map.of(
                vencido, "ENLACE_VENCIDO",
                agotado, "ENLACE_AGOTADO",
                desactivado.get("codigo").asText(), "ENLACE_DESACTIVADO");
        for (Map.Entry<String, String> caso : casos.entrySet()) {
            String codigo = caso.getKey();
            servicios(codigo).andExpect(status().isGone()).andExpect(jsonPath("$.codigo").value(caso.getValue()));
            horarios(codigo, corte, MANANA).andExpect(status().isGone())
                    .andExpect(jsonPath("$.codigo").value(caso.getValue()));
            reservar(codigo, "10:00").andExpect(status().isGone())
                    .andExpect(jsonPath("$.codigo").value(caso.getValue()));
        }
        assertThat(contar("cita")).isZero();
        assertThat(contar("cliente")).isZero();
        assertThat(usos(agotado)).isEqualTo(1);
    }

    @Test
    void desactivadoGanaAVencidoYAgotado() throws Exception {
        JsonNode enlace = crearEnlace(60, 1);
        String codigo = enlace.get("codigo").asText();
        jdbc.update("UPDATE enlace_invitacion SET usos = 1 WHERE codigo = ?", codigo);
        desactivarPorPanel(enlace);
        fijarAhora(AHORA.plusDays(2));

        estado(codigo).andExpect(jsonPath("$.estado").value("Desactivado"));
        reservar(codigo, "10:00").andExpect(jsonPath("$.codigo").value("ENLACE_DESACTIVADO"));
    }

    // ---------- Servicios y horarios ----------

    @Test
    void soloSeListanLosServiciosActivosPorNombre() throws Exception {
        servicios(codigo(null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("Barba", "Corte")))
                .andExpect(jsonPath("$[1].id").value(corte.toString()))
                .andExpect(jsonPath("$[1].duracionMin").value(30))
                .andExpect(jsonPath("$[1].ventanas[0].inicio").value("09:00"));
    }

    @Test
    void horariosDevuelveSoloHorasLibres() throws Exception {
        String codigo = codigo(null, null);
        reservar(codigo, "09:30").andExpect(status().isCreated());

        horarios(codigo, corte, MANANA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("09:00"))
                .andExpect(jsonPath("$", not(hasItem("09:30"))))
                .andExpect(jsonPath("$", hasItem("11:30")));
    }

    @Test
    void horariosDeUnServicioInactivoODesconocidoSon404IgualesSinPistas() throws Exception {
        String codigo = codigo(null, null);

        String deInactivo = errorSinRuta(horarios(codigo, inactivo, MANANA).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO")));
        String deDesconocido = errorSinRuta(horarios(codigo, UUID.randomUUID(), MANANA)
                .andExpect(status().isNotFound()));

        assertThat(deInactivo).isEqualTo(deDesconocido);
    }

    @Test
    void horariosConServicioOFechaMalFormadosSon400() throws Exception {
        String codigo = codigo(null, null);

        horarios(codigo, "no-es-uuid", MANANA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        horarios(codigo, corte, "2026-13-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        horarios(codigo, corte, "10/11/2026").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mvc.perform(get(BASE + codigo + "/servicios/" + corte + "/horarios")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    // ---------- Reservar ----------

    @Test
    void unEnlaceActivoPermiteElFlujoCompletoYLaCitaQuedaLigadaAlEnlace() throws Exception {
        JsonNode enlace = crearEnlace(null, 5);
        String codigo = enlace.get("codigo").asText();

        estado(codigo).andExpect(jsonPath("$.estado").value("Activo"));
        servicios(codigo).andExpect(status().isOk());
        horarios(codigo, corte, MANANA).andExpect(jsonPath("$", hasItem("10:00")));
        JsonNode cita = json.readTree(cuerpoDe(reservar(codigo, "10:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.servicioId").value(corte.toString()))
                .andExpect(jsonPath("$.servicio").value("Corte"))
                .andExpect(jsonPath("$.fecha").value(MANANA))
                .andExpect(jsonPath("$.inicio").value("10:00"))
                .andExpect(jsonPath("$.fin").value("10:30"))
                .andExpect(jsonPath("$.estado").value("Confirmada"))));

        assertThat(cita.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("servicioId", "servicio", "fecha", "inicio", "fin", "estado");
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cita");
        assertThat(fila.get("enlace_id").toString()).isEqualToIgnoringCase(enlace.get("id").asText());
        assertThat(fila.get("estado")).isEqualTo("Confirmada");
        assertThat(usos(codigo)).isEqualTo(1);
        horarios(codigo, corte, MANANA).andExpect(jsonPath("$", not(hasItem("10:00"))));
        // El panel ve la cita y el uso.
        obtener("/api/panel/citas", admin).andExpect(jsonPath("$[0].inicio").value("10:00"));
        obtener("/api/panel/enlaces", admin).andExpect(jsonPath("$[0].usos").value(1));
    }

    @Test
    void laRespuestaNoIncluyeDatosDeOtrosClientes() throws Exception {
        jdbc.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) "
                + "VALUES (?, 'Beatriz', 'Secreta', '311 999 9999', 'bea@x.co')", UUID.randomUUID());
        String codigo = codigo(null, null);

        String cuerpo = cuerpoDe(reservar(codigo, reserva(corte, MANANA, "09:00", "BEA@x.co"))
                .andExpect(status().isCreated()));

        assertThat(cuerpo).doesNotContain("Beatriz", "Secreta", "311 999 9999", "bea@x.co", "clienteId",
                "telefono", "correo");
        assertThat(json.readTree(cuerpo).properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("servicioId", "servicio", "fecha", "inicio", "fin", "estado");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unaReservaFallidaNoGastaUnUso() throws Exception {
        String codigo = codigo(null, 2);
        reservar(codigo, "09:00").andExpect(status().isCreated());
        assertThat(usos(codigo)).isEqualTo(1);

        // Horario ocupado.
        reservar(codigo, "09:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HORARIO_NO_DISPONIBLE"));
        // Horario mal alineado o en el pasado.
        reservar(codigo, "09:15").andExpect(status().isConflict());
        reservar(codigo, reserva(corte, "2026-11-08", "10:00", "ana@x.co")).andExpect(status().isConflict());
        // Datos del cliente inválidos (validación de la aplicación y de Bean Validation).
        reservar(codigo, reserva(corte, MANANA, "10:00", "no-es-correo")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.correo").isNotEmpty());
        Map<String, Object> sinNombre = reserva(corte, MANANA, "10:00", "ana@x.co");
        ((Map<String, Object>) sinNombre.get("cliente")).put("nombre", " ");
        reservar(codigo, sinNombre).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['cliente.nombre']").isNotEmpty());
        // Hora mal escrita.
        reservar(codigo, "9:00").andExpect(status().isBadRequest());
        // Servicio inactivo o desconocido.
        reservar(codigo, reserva(inactivo, MANANA, "10:00", "ana@x.co")).andExpect(status().isNotFound());
        reservar(codigo, reserva(UUID.randomUUID(), MANANA, "10:00", "ana@x.co")).andExpect(status().isNotFound());

        assertThat(usos(codigo)).isEqualTo(1);
        assertThat(contar("cita")).isEqualTo(1);

        reservar(codigo, "09:30").andExpect(status().isCreated());
        assertThat(usos(codigo)).isEqualTo(2);
        reservar(codigo, "10:00").andExpect(status().isGone()).andExpect(jsonPath("$.codigo").value("ENLACE_AGOTADO"));
        estado(codigo).andExpect(jsonPath("$.estado").value("Agotado"));
        assertThat(usos(codigo)).isEqualTo(2);
        assertThat(contar("cita")).isEqualTo(2);
    }

    @Test
    void cancelarLaCitaNoDevuelveElUso() throws Exception {
        String codigo = codigo(null, 1);
        reservar(codigo, "09:00").andExpect(status().isCreated());
        UUID cita = jdbc.queryForObject("SELECT id FROM cita", UUID.class);

        mvc.perform(post("/api/panel/citas/" + cita + "/cancelar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(admin))).andExpect(status().isOk());

        assertThat(usos(codigo)).isEqualTo(1);
        estado(codigo).andExpect(jsonPath("$.estado").value("Agotado"));
        reservar(codigo, "09:00").andExpect(status().isGone()).andExpect(jsonPath("$.codigo").value("ENLACE_AGOTADO"));
    }

    @Test
    void siElEnlaceSeDesactivaAMitadDelFlujoLaReservaFalla() throws Exception {
        JsonNode enlace = crearEnlace(null, null);
        String codigo = enlace.get("codigo").asText();
        horarios(codigo, corte, MANANA).andExpect(status().isOk());

        desactivarPorPanel(enlace);

        reservar(codigo, "09:00").andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("ENLACE_DESACTIVADO"));
        assertThat(contar("cita")).isZero();
    }

    @Test
    void siElEnlaceVenceAMitadDelFlujoLaReservaFalla() throws Exception {
        String codigo = codigo(60, null);
        horarios(codigo, corte, MANANA).andExpect(status().isOk());

        fijarAhora(AHORA.plusMinutes(60));

        reservar(codigo, "09:00").andExpect(status().isGone()).andExpect(jsonPath("$.codigo").value("ENLACE_VENCIDO"));
        assertThat(contar("cita")).isZero();
    }

    // ---------- Sin token, o con un token viejo ----------

    private static MockHttpServletRequestBuilder conTokenBasura(MockHttpServletRequestBuilder peticion) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer basura.vencida.xxx");
    }

    @Test
    void unTokenInvalidoEnLaZonaDelClienteSeIgnora() throws Exception {
        String codigo = codigo(null, null);

        mvc.perform(conTokenBasura(get(BASE + codigo))).andExpect(status().isOk());
        mvc.perform(conTokenBasura(get(BASE + codigo + "/servicios"))).andExpect(status().isOk());
        mvc.perform(conTokenBasura(get(BASE + codigo + "/servicios/" + corte + "/horarios").param("fecha", MANANA)))
                .andExpect(status().isOk());
        mvc.perform(conTokenBasura(post(BASE + codigo + "/citas")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(reserva(corte, MANANA, "09:00", "ana@x.co"))))
                .andExpect(status().isCreated());
        mvc.perform(conTokenBasura(get(BASE + "AAAAAAAAAAAAAAAAAAAAAA"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ENLACE_NO_ENCONTRADO"));
    }

    @Test
    void unTokenInvalidoNoImpideIniciarSesion() throws Exception {
        String usuario = "login-" + UUID.randomUUID().toString().substring(0, 8);
        crearUsuario(usuario, crearRol("R", "ver_panel"), "clave-1");

        mvc.perform(conTokenBasura(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("usuario", usuario, "clave", "clave-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        // Con credenciales malas sigue siendo CREDENCIALES_INVALIDAS, no el 401 del token.
        mvc.perform(conTokenBasura(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("usuario", usuario, "clave", "mala"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void lasRutasDelPanelYYoSiguenExigiendoUnTokenValido() throws Exception {
        mvc.perform(conTokenBasura(get("/api/panel/enlaces"))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(conTokenBasura(get("/api/auth/yo"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void laZonaDelClienteFuncionaSinToken() throws Exception {
        String codigo = codigo(null, null);

        mvc.perform(get(BASE + codigo)).andExpect(status().isOk());
        mvc.perform(get(BASE + codigo + "/servicios")).andExpect(status().isOk());
        horarios(codigo, corte, MANANA).andExpect(status().isOk());
        reservar(codigo, "09:00").andExpect(status().isCreated());
        assertThat(usos(codigo)).isEqualTo(1);
    }
}
