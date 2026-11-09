package com.datemanager.infraestructura;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datemanager.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** El contrato OpenAPI (activado aquí como en el perfil dev) declara el esquema Bearer JWT: habilita «Authorize». */
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
class OpenApiConfigTest extends PruebaIntegracion {

    @Test
    void elContratoDeclaraElEsquemaBearerJwtYLoExigeDeFormaGlobal() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }
}
