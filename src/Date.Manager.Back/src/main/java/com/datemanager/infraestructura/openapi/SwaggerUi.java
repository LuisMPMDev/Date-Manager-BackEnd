package com.datemanager.infraestructura.openapi;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.datemanager.infraestructura.http.Enrutador;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;

/**
 * Rutas de documentación, solo para el perfil {@code dev} (quien las registra decide): {@code GET /v3/api-docs}
 * (el contrato de {@link GeneradorOpenApi}) y {@code GET /swagger-ui.html} (Swagger UI apuntando a ese contrato).
 * Las dos son públicas. Fuera de {@code dev} no se registran y dan 404 como cualquier ruta inexistente.
 *
 * <p>Los archivos de Swagger UI ({@code swagger-ui-dist} {@value #VERSION}) se cargan de jsDelivr: no se
 * vendorizan en el repositorio, así que la página necesita internet. El contrato se genera en cada petición con las
 * rutas registradas en ese momento (sin estas dos).
 */
public final class SwaggerUi {

    public static final String VERSION = "5.17.14";
    public static final String RUTA_CONTRATO = "/v3/api-docs";
    public static final String RUTA_PAGINA = "/swagger-ui.html";

    private static final String CDN = "https://cdn.jsdelivr.net/npm/swagger-ui-dist@" + VERSION + "/";
    private static final Set<String> PROPIAS = Set.of(RUTA_CONTRATO, RUTA_PAGINA);

    private static final String PAGINA = """
            <!doctype html>
            <html lang="es">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>DateManager API · Swagger UI</title>
              <link rel="icon" type="image/png" href="%1$sfavicon-32x32.png">
              <link rel="stylesheet" href="%1$sswagger-ui.css" crossorigin="anonymous">
            </head>
            <body>
              <div id="swagger-ui"></div>
              <script src="%1$sswagger-ui-bundle.js" crossorigin="anonymous"></script>
              <script src="%1$sswagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
              <script>
                window.ui = SwaggerUIBundle({
                  url: "%2$s",
                  dom_id: "#swagger-ui",
                  deepLinking: true,
                  persistAuthorization: true,
                  presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
                  plugins: [SwaggerUIBundle.plugins.DownloadUrl],
                  layout: "StandaloneLayout"
                });
              </script>
            </body>
            </html>
            """.formatted(CDN, RUTA_CONTRATO);

    private SwaggerUi() {
    }

    /** Las rutas de documentación sobre la tabla {@code enrutador} (leída en cada petición). */
    public static List<Ruta> rutas(Enrutador enrutador) {
        Objects.requireNonNull(enrutador);
        byte[] pagina = PAGINA.getBytes(StandardCharsets.UTF_8);
        return List.of(
                new Ruta("GET", RUTA_CONTRATO, Set.of(), true,
                        s -> Respuesta.ok(GeneradorOpenApi.generar(documentables(enrutador))), null),
                new Ruta("GET", RUTA_PAGINA, Set.of(), true,
                        s -> Respuesta.contenido("text/html; charset=utf-8", pagina), null));
    }

    /** Las rutas de la API, sin las de documentación. */
    public static List<Ruta> documentables(Enrutador enrutador) {
        return enrutador.rutas().stream().filter(r -> !PROPIAS.contains(r.patron())).toList();
    }
}
