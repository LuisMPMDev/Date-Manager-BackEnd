package com.datemanager.infraestructura.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.datemanager.PruebaIntegracion;
import com.datemanager.infraestructura.Aplicacion;
import com.datemanager.infraestructura.Configuracion;
import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.http.ServidorHttp;
import com.datemanager.infraestructura.seguridad.Autenticacion;
import com.datemanager.infraestructura.seguridad.UsuarioActual;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * El contrato OpenAPI y Swagger UI existen solo en el perfil {@code dev} y sin token; declaran el esquema Bearer JWT
 * (habilita «Authorize») y documentan todas las rutas. Fuera de {@code dev} dan 404.
 */
class OpenApiTest extends PruebaIntegracion {

    private static final HttpClient CLIENTE = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** Ninguna petición trae una sesión válida: si algo pidiera token, daría 401. */
    private static final Autenticacion SIN_SESION = new Autenticacion() {
        @Override
        public Optional<UsuarioActual> autenticar(String cabeceraAuthorization) {
            return Optional.empty();
        }

        @Override
        public boolean tiene(UsuarioActual usuario, Set<String> permisosAlgunoDe) {
            return false;
        }
    };

    private static Aplicacion dev;
    private static ServidorHttp servidorDev;
    private static String baseDev;

    @BeforeAll
    static void arrancarDev() {
        Configuracion base = configuracion();
        Configuracion cfg = new Configuracion(base.dbUrl(), base.dbUsuario(), base.dbClave(), base.dbPool(),
                base.jwtSecreto(), base.zona(), base.corsOrigenes(), "", "", true, 0);
        // Solo su tabla de rutas: no se migra ni se siembra; el servidor no autentica a nadie.
        dev = new Aplicacion(cfg, pool, Clock.system(base.zona()));
        servidorDev = new ServidorHttp(cfg, dev.rutas(), SIN_SESION);
        servidorDev.iniciar();
        baseDev = "http://127.0.0.1:" + servidorDev.puerto();
    }

    @AfterAll
    static void detenerDev() {
        if (servidorDev != null) {
            servidorDev.detener();
        }
    }

    private static HttpResponse<String> getDev(String ruta) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(baseDev + ruta)).timeout(Duration.ofSeconds(30))
                .GET().build();
        return CLIENTE.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> contrato() throws Exception {
        HttpResponse<String> respuesta = getDev("/v3/api-docs");
        assertEquals(200, respuesta.statusCode());
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        return (Map<String, Object>) Json.parse(respuesta.body());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> m(Object valor) {
        return (Map<String, Object>) valor;
    }

    @Test
    void elContratoDeclaraElEsquemaBearerJwtYLoExigeDeFormaGlobal() throws Exception {
        Map<String, Object> doc = contrato();
        assertEquals("3.0.3", doc.get("openapi"));
        assertEquals("DateManager API", m(doc.get("info")).get("title"));
        assertEquals("1", m(doc.get("info")).get("version"));
        Map<String, Object> bearer = m(m(m(doc.get("components")).get("securitySchemes")).get("bearerAuth"));
        assertEquals("http", bearer.get("type"));
        assertEquals("bearer", bearer.get("scheme"));
        assertEquals("JWT", bearer.get("bearerFormat"));
        List<?> seguridad = (List<?>) doc.get("security");
        assertInstanceOf(List.class, m(seguridad.get(0)).get("bearerAuth"));
    }

    @Test
    void elContratoDocumentaCadaRutaDeLaTabla() throws Exception {
        Map<String, Object> paths = m(contrato().get("paths"));
        Set<String> documentadas = new HashSet<>();
        paths.forEach((patron, operaciones) -> m(operaciones).keySet()
                .forEach(metodo -> documentadas.add(metodo.toUpperCase(Locale.ROOT) + " " + patron)));
        Set<String> registradas = new HashSet<>();
        for (Ruta ruta : SwaggerUi.documentables(dev.rutas())) {
            registradas.add(ruta.metodo() + " " + ruta.patron());
        }
        assertFalse(registradas.isEmpty());
        assertEquals(registradas, documentadas);
        assertFalse(paths.containsKey("/v3/api-docs"));
        assertFalse(paths.containsKey("/swagger-ui.html"));
    }

    @Test
    void lasRutasPublicasAnulanLaSeguridadYLasDelPanelDeclaranSusPermisos() throws Exception {
        Map<String, Object> paths = m(contrato().get("paths"));
        Map<String, Object> login = m(m(paths.get("/api/auth/login")).get("post"));
        assertEquals(List.of(), login.get("security"));
        assertNull(login.get("x-permisos"));
        assertEquals(1, ((List<?>) login.get("tags")).size());

        Map<String, Object> citas = m(m(paths.get("/api/panel/citas")).get("get"));
        assertNull(citas.get("security"));
        assertEquals(List.of("ver_citas", "ver_panel"), citas.get("x-permisos"));
        assertEquals(List.of("Citas"), citas.get("tags"));
        Map<String, Object> esquema = m(m(m(m(m(citas.get("responses")).get("200")).get("content"))
                .get("application/json")).get("schema"));
        assertEquals("array", esquema.get("type"));
        assertEquals("#/components/schemas/CitaDto", m(esquema.get("items")).get("$ref"));

        Map<String, Object> mover = m(m(paths.get("/api/panel/citas/{id}/mover")).get("post"));
        Map<String, Object> parametro = m(((List<?>) mover.get("parameters")).get(0));
        assertEquals("id", parametro.get("name"));
        assertEquals("path", parametro.get("in"));
        assertNotNull(mover.get("requestBody"));
    }

    @Test
    void losEsquemasSalenDeLosRecords() throws Exception {
        Map<String, Object> esquemas = m(m(contrato().get("components")).get("schemas"));
        Map<String, Object> servicio = m(m(esquemas.get("ServicioDto")).get("properties"));
        assertEquals(Map.of("type", "string", "format", "uuid"), servicio.get("id"));
        assertEquals("string", m(servicio.get("nombre")).get("type"));
        assertEquals("integer", m(servicio.get("duracionMin")).get("type"));
        assertEquals("boolean", m(servicio.get("activo")).get("type"));
        assertEquals("number", m(servicio.get("valor")).get("type"));
        assertEquals("array", m(servicio.get("ventanas")).get("type"));
        assertEquals("#/components/schemas/VentanaDto", m(m(servicio.get("ventanas")).get("items")).get("$ref"));
        assertTrue(esquemas.containsKey("VentanaDto"));
        assertTrue(esquemas.containsKey("SesionDto"));
    }

    @Test
    void laPaginaDeSwaggerCargaSinTokenYApuntaAlContrato() throws Exception {
        HttpResponse<String> pagina = getDev("/swagger-ui.html");
        assertEquals(200, pagina.statusCode());
        assertTrue(pagina.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(pagina.body().contains("url: \"/v3/api-docs\""));
        assertTrue(pagina.body().contains("swagger-ui-dist@5.17.14/swagger-ui-bundle.js"));
        assertTrue(pagina.body().contains("SwaggerUIBundle("));
    }

    @Test
    void enDevLasRutasDelPanelSiguenPidiendoToken() throws Exception {
        assertEquals(401, getDev("/api/panel/citas").statusCode());
    }

    @Test
    void fueraDeDevNoExistenNiElContratoNiLaPagina() {
        assertEquals(404, get("/v3/api-docs").enviar().estado());
        assertEquals(404, get("/swagger-ui.html").enviar().estado());
        assertEquals(404, get("/swagger-ui/index.html").enviar().estado());
    }
}
