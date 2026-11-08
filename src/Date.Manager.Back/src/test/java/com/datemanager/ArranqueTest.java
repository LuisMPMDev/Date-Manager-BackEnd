package com.datemanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.http.MediaType;

class ArranqueTest extends PruebaIntegracion {

    private static final String BASE = "/api/publico/_prueba";

    @Autowired
    private LoggingSystem loggingSystem;

    @Test
    void elContextoArrancaYFlywayAplicaLaLineaBase() {
        Integer aplicadas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class);

        assertThat(aplicadas).isEqualTo(1);
    }

    @Test
    void errorDeNegocioConflictoResponde409ConCodigoYMensaje() throws Exception {
        mvc.perform(get(BASE + "/error-negocio/CONFLICTO"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje").value("x"));
    }

    @Test
    void horarioNoDisponibleResponde409() throws Exception {
        mvc.perform(get(BASE + "/error-negocio/HORARIO_NO_DISPONIBLE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HORARIO_NO_DISPONIBLE"));
    }

    @Test
    void enlaceVencidoResponde410() throws Exception {
        mvc.perform(get(BASE + "/error-negocio/ENLACE_VENCIDO"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("ENLACE_VENCIDO"));
    }

    @Test
    void laValidacionDeBeanValidationResponde400ConLosCampos() throws Exception {
        mvc.perform(post(BASE + "/validacion").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.campos.nombre").value("es obligatorio"));
    }

    @Test
    void unCuerpoIlegibleResponde400ConCodigoDeValidacion() throws Exception {
        mvc.perform(post(BASE + "/validacion").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void unaRutaInexistenteResponde404ConCodigoYNoSeConvierteEn500() throws Exception {
        mvc.perform(get(BASE + "/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void unaViolacionDeRestriccionDeLaBaseResponde409SinFiltrarDetalles() throws Exception {
        mvc.perform(get(BASE + "/violacion-unica"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("detalle interno"))));
    }

    @Test
    void unaExcepcionInesperadaResponde500SinFiltrarDetalles() throws Exception {
        // La excepción se registra en el log (ERROR); aquí se silencia para no ensuciar la salida de pruebas.
        String registrador = "com.datemanager.api.ManejadorErrores";
        loggingSystem.setLogLevel(registrador, LogLevel.OFF);
        try {
            mvc.perform(get(BASE + "/inesperado"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("detalle interno"))));
        } finally {
            loggingSystem.setLogLevel(registrador, null);
        }
    }

    @Test
    void elRelojUsaLaZonaDelNegocioYSePuedeFijar() {
        assertThat(reloj().getZone()).isEqualTo(ZoneId.of("America/Bogota"));

        fijarAhora(LocalDateTime.of(2026, 11, 8, 23, 30));

        // 23:30 en Bogotá es ya el día siguiente en UTC; "hoy" debe seguir siendo el de Bogotá.
        assertThat(LocalDate.now(reloj())).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(LocalDateTime.now(reloj())).isEqualTo(LocalDateTime.of(2026, 11, 8, 23, 30));
    }
}
