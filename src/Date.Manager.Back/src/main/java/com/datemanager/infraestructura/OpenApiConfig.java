package com.datemanager.infraestructura;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara en OpenAPI el esquema de seguridad Bearer JWT, para que Swagger UI (perfil {@code dev}) muestre el botón
 * <em>Authorize</em> y pueda llamar a las rutas del panel con el token de {@code POST /api/auth/login}.
 * El requisito es global solo como indicación para la interfaz: la autorización real la hace Spring Security,
 * y las rutas públicas siguen siendo invocables sin token.
 */
@Configuration
public class OpenApiConfig {

    public static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("DateManager API").version("1"))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token de POST /api/auth/login (sin el prefijo «Bearer»).")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
    }
}
