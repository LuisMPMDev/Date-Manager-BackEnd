package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.datemanager.dominio.CodigoError;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ManejadorErroresTest {

    @Test
    void cadaCodigoTieneSuEstadoHttp() {
        Map<CodigoError, HttpStatus> esperado = Map.ofEntries(
                Map.entry(CodigoError.VALIDACION, HttpStatus.BAD_REQUEST),
                Map.entry(CodigoError.NO_AUTENTICADO, HttpStatus.UNAUTHORIZED),
                Map.entry(CodigoError.CREDENCIALES_INVALIDAS, HttpStatus.UNAUTHORIZED),
                Map.entry(CodigoError.SIN_PERMISO, HttpStatus.FORBIDDEN),
                Map.entry(CodigoError.NO_ENCONTRADO, HttpStatus.NOT_FOUND),
                Map.entry(CodigoError.ENLACE_NO_ENCONTRADO, HttpStatus.NOT_FOUND),
                Map.entry(CodigoError.HORARIO_NO_DISPONIBLE, HttpStatus.CONFLICT),
                Map.entry(CodigoError.ESTADO_INVALIDO, HttpStatus.CONFLICT),
                Map.entry(CodigoError.CITA_NO_HA_INICIADO, HttpStatus.CONFLICT),
                Map.entry(CodigoError.CONFLICTO, HttpStatus.CONFLICT),
                Map.entry(CodigoError.ENLACE_VENCIDO, HttpStatus.GONE),
                Map.entry(CodigoError.ENLACE_AGOTADO, HttpStatus.GONE),
                Map.entry(CodigoError.ENLACE_DESACTIVADO, HttpStatus.GONE),
                Map.entry(CodigoError.ERROR_INTERNO, HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(esperado).containsOnlyKeys(CodigoError.values());
        esperado.forEach((codigo, estado) ->
                assertThat(ManejadorErrores.estadoHttp(codigo)).as(codigo.name()).isEqualTo(estado));
    }

    /** Lanza el error con que llega a la aplicación el rechazo de un disparador de la base (THROW 50010). */
    @RestController
    static class ControladorQueFalla {

        @GetMapping("/disparador")
        void disparador() {
            throw new UncategorizedSQLException("guardar cita", "INSERT INTO cita ...",
                    new SQLException("La cita se solapa con otra del mismo servicio. valor-de-la-base", "S0001",
                            50010));
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ControladorQueFalla())
            .setControllerAdvice(new ManejadorErrores()).build();

    @Test
    void elErrorDeUnDisparadorEsUnConflictoSinElTextoDeLaBase() throws Exception {
        // El manejador avisa (WARN) del rechazo; aquí es esperado, así que no se imprime.
        Logger registro = (Logger) LoggerFactory.getLogger(ManejadorErrores.class);
        Level nivel = registro.getLevel();
        registro.setLevel(Level.ERROR);
        try {
            String cuerpo = mvc.perform(get("/disparador"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                    .andExpect(jsonPath("$.mensaje")
                            .value("No se pudo guardar porque entra en conflicto con datos que ya existen."))
                    .andReturn().getResponse().getContentAsString();

            assertThat(cuerpo).doesNotContain("solapa").doesNotContain("valor-de-la-base").doesNotContain("INSERT");
        } finally {
            registro.setLevel(nivel);
        }
    }
}
