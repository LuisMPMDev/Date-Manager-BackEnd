package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.datemanager.dominio.Permiso;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * El recorrido completo de punta a punta con peticiones HTTP reales (solo el reloj es falso):
 * una administradora entra, crea un enlace de 24 h con 1 uso, el cliente (sin token) ve servicios y horarios y
 * reserva, la cita aparece en el panel, el enlace ya no admite otra reserva y, al cancelar la cita, el horario
 * vuelve a quedar libre.
 */
class RecorridoCompletoTest extends PruebaPanel {

    /** Lunes 9 de noviembre de 2026, 08:00 en Bogotá. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 8, 0);
    private static final String MANANA = "2026-11-10";
    private static final String CLAVE_ADMIN = "clave-de-prueba-del-recorrido";

    @Test
    void recorridoCompletoDeLaAdministradoraAlClienteYDeVuelta() throws Exception {
        fijarAhora(AHORA);
        String[] todos = Arrays.stream(Permiso.values()).map(Permiso::id).toArray(String[]::new);
        crearUsuario("admin", crearRol("Administrador", todos), CLAVE_ADMIN);

        // 1. Login real con usuario y clave.
        String token = json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("usuario", "admin", "clave", CLAVE_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.usuario").value("admin"))
                .andExpect(jsonPath("$.usuario.clave").doesNotExist())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        String bearer = "Bearer " + token;

        // El panel crea el servicio con sus ventanas (30 min, todos los días de 09:00 a 12:00).
        UUID servicio = UUID.randomUUID();
        List<Map<String, Object>> ventanas = new ArrayList<>();
        for (int dia = 1; dia <= 7; dia++) {
            ventanas.add(Map.of("dia", dia, "inicio", "09:00", "fin", "12:00"));
        }
        mvc.perform(put("/api/panel/servicios/" + servicio).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombre", "Corte", "duracionMin", 30, "activo", true,
                                "valor", 40000, "ventanas", ventanas))))
                .andExpect(status().isOk());

        // 2. Enlace de 24 h con 1 uso.
        JsonNode enlace = json.readTree(mvc.perform(post("/api/panel/enlaces").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("duracionMinutos", 1440, "maxUsos", 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("Activo"))
                .andExpect(jsonPath("$.maxUsos").value(1))
                .andExpect(jsonPath("$.usos").value(0))
                // 08:00 en Bogotá = 13:00 UTC; 24 h después.
                .andExpect(jsonPath("$.venceEn").value("2026-11-10T13:00:00Z"))
                .andReturn().getResponse().getContentAsString());
        String codigo = enlace.get("codigo").asText();
        String base = "/api/publico/invitaciones/" + codigo;

        // 3. El cliente, sin token: el enlace está Activo.
        mvc.perform(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("Activo"));

        // 4. Servicios activos.
        mvc.perform(get(base + "/servicios")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(servicio.toString()))
                .andExpect(jsonPath("$[0].nombre").value("Corte"));

        // 5. Horarios libres de mañana.
        mvc.perform(get(base + "/servicios/" + servicio + "/horarios").param("fecha", MANANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("09:00")))
                .andExpect(jsonPath("$", hasItem("11:30")));

        // 6. Reserva.
        mvc.perform(post(base + "/citas").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reserva(servicio, "09:00", "ana@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("Confirmada"))
                .andExpect(jsonPath("$.inicio").value("09:00"))
                .andExpect(jsonPath("$.fin").value("09:30"));
        mvc.perform(get(base)).andExpect(jsonPath("$.estado").value("Agotado"));

        // 7. La cita aparece en el panel.
        JsonNode citas = json.readTree(mvc.perform(get("/api/panel/citas").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(citas).hasSize(1);
        JsonNode cita = citas.get(0);
        assertThat(cita.get("servicioId").asText()).isEqualTo(servicio.toString());
        assertThat(cita.get("fecha").asText()).isEqualTo(MANANA);
        assertThat(cita.get("inicio").asText()).isEqualTo("09:00");
        assertThat(cita.get("estado").asText()).isEqualTo("Confirmada");
        String citaId = cita.get("id").asText();

        // 8. Una segunda reserva con el mismo enlace: 410 ENLACE_AGOTADO y no se crea nada.
        mvc.perform(post(base + "/citas").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reserva(servicio, "09:30", "bea@example.com"))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("ENLACE_AGOTADO"));
        assertThat(contar("cita")).isEqualTo(1);
        assertThat(contar("cliente")).isEqualTo(1);

        // Con la cita confirmada, el horario de las 09:00 ya no se ofrece (por el panel; el enlace está agotado).
        mvc.perform(get("/api/panel/servicios/" + servicio + "/horarios").param("fecha", MANANA)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasItem("09:00"))))
                .andExpect(jsonPath("$", hasItem("09:30")));

        // 9. El panel cancela la cita.
        mvc.perform(post("/api/panel/citas/" + citaId + "/cancelar").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Cancelada"))
                .andExpect(jsonPath("$.canceladaEn").exists());

        // 10. El horario vuelve a salir libre (por el panel: el enlace ya agotado no sirve para consultar).
        mvc.perform(get("/api/panel/servicios/" + servicio + "/horarios").param("fecha", MANANA)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("09:00")));
        mvc.perform(get(base + "/servicios/" + servicio + "/horarios").param("fecha", MANANA))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("ENLACE_AGOTADO"));
        // Y un enlace nuevo lo ofrece al cliente otra vez.
        String otro = json.readTree(mvc.perform(post("/api/panel/enlaces").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("codigo").asText();
        mvc.perform(get("/api/publico/invitaciones/" + otro + "/servicios/" + servicio + "/horarios")
                        .param("fecha", MANANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("09:00")));
        mvc.perform(get("/api/panel/citas").param("estado", "Confirmada").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private static Map<String, Object> reserva(UUID servicio, String inicio, String correo) {
        return Map.of("servicioId", servicio.toString(), "fecha", MANANA, "inicio", inicio,
                "cliente", Map.of("nombre", "Ana", "apellido", "Ruiz", "telefono", "300 111", "correo", correo));
    }
}
