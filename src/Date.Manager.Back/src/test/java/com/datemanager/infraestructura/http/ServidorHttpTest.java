package com.datemanager.infraestructura.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.datemanager.ClienteHttpPrueba;
import com.datemanager.ClienteHttpPrueba.Resultado;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.infraestructura.seguridad.Autenticacion;
import com.datemanager.infraestructura.seguridad.UsuarioActual;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El servidor real en un puerto efímero, con rutas de juguete y una {@link Autenticacion} falsa. */
class ServidorHttpTest {

    static final String ORIGEN = "http://localhost:4200";
    static final int UN_MEGABYTE = 1024 * 1024;
    static final UUID ROL_ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID ROL_BASICO = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    enum Estado { PENDIENTE, CONFIRMADA }

    /** "Bearer admin" y "Bearer basico" son sesiones válidas; "Bearer explota" falla; lo demás no es sesión. */
    static final class AutenticacionFalsa implements Autenticacion {
        final AtomicInteger llamadas = new AtomicInteger();

        @Override
        public Optional<UsuarioActual> autenticar(String cabecera) {
            llamadas.incrementAndGet();
            if ("Bearer admin".equals(cabecera)) {
                return Optional.of(new UsuarioActual(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        ROL_ADMIN));
            }
            if ("Bearer basico".equals(cabecera)) {
                return Optional.of(new UsuarioActual(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        ROL_BASICO));
            }
            if ("Bearer explota".equals(cabecera)) {
                throw new IllegalStateException("la base no responde");
            }
            return Optional.empty();
        }

        @Override
        public boolean tiene(UsuarioActual usuario, Set<String> permisosAlgunoDe) {
            return usuario.rolId().equals(ROL_ADMIN) && permisosAlgunoDe.contains("ver_citas");
        }
    }

    private final AutenticacionFalsa autenticacion = new AutenticacionFalsa();
    private final AtomicInteger manejadoresEjecutados = new AtomicInteger();
    private final CountDownLatch liberado = new CountDownLatch(1);
    private Logger registroDeErrores;
    private Level nivelAnterior;
    private ClienteHttpPrueba http;

    @BeforeEach
    void arrancar() {
        // Los 500 de estas pruebas son esperados: el manejador los registra con traza y aquí no se imprimen.
        registroDeErrores = Logger.getLogger(ManejadorErrores.class.getName());
        nivelAnterior = registroDeErrores.getLevel();
        registroDeErrores.setLevel(Level.OFF);
        http = ClienteHttpPrueba.iniciar(rutas(), autenticacion, ORIGEN, "https://otro.ejemplo/");
    }

    @AfterEach
    void parar() {
        http.close();
        registroDeErrores.setLevel(nivelAnterior);
    }

    private static Ruta publica(String metodo, String patron, Manejador manejador) {
        return new Ruta(metodo, patron, Set.of(), true, manejador, null);
    }

    private static Ruta conSesion(String metodo, String patron, Manejador manejador) {
        return new Ruta(metodo, patron, Set.of(), false, manejador, null);
    }

    private Enrutador rutas() {
        Enrutador rutas = new Enrutador();
        rutas.agregar(publica("GET", "/api/eco/{id}", s -> {
            manejadoresEjecutados.incrementAndGet();
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("id", s.parametro("id"));
            cuerpo.put("ruta", s.ruta());
            return Respuesta.ok(cuerpo);
        }));
        rutas.agregar(publica("GET", "/api/citas/hoy", s -> Respuesta.ok(Map.of("quien", "literal"))));
        rutas.agregar(publica("GET", "/api/citas/{id}", s -> Respuesta.ok(Map.of("quien", "parametro"))));
        rutas.agregar(publica("POST", "/api/cuerpo", s -> Respuesta.ok(s.cuerpoObjeto())));
        rutas.agregar(publica("POST", "/api/lenta-despues", s -> {
            s.cuerpoObjeto();
            Thread.sleep(1500);
            return Respuesta.ok(Map.of("ok", true));
        }));
        rutas.agregar(publica("POST", "/api/crear", s -> Respuesta.creada(Map.of("creado", true))));
        rutas.agregar(publica("DELETE", "/api/cosas/{id}", s -> Respuesta.sinContenido()));
        rutas.agregar(publica("GET", "/api/vacia", s -> new Respuesta(200, null)));
        rutas.agregar(publica("GET", "/api/nada", s -> null));
        rutas.agregar(publica("GET", "/api/falla", s -> {
            throw new IllegalStateException("secreto-interno de la base");
        }));
        rutas.agregar(publica("GET", "/api/falla-error", s -> {
            throw new StackOverflowError();
        }));
        rutas.agregar(publica("GET", "/api/no-serializable", s -> Respuesta.ok(new Object())));
        rutas.agregar(publica("GET", "/api/negocio", s -> {
            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("nombre", "no puede estar vacío");
            campos.put("duracion", "debe ser mayor que 0");
            throw new ErrorNegocio(CodigoError.VALIDACION, "Hay datos inválidos en la petición.", campos);
        }));
        rutas.agregar(publica("GET", "/api/gone", s -> {
            throw new ErrorNegocio(CodigoError.ENLACE_VENCIDO, "El enlace venció.");
        }));
        rutas.agregar(publica("GET", "/api/uuid/{id}", s -> Respuesta.ok(Map.of("id", s.parametroUuid("id")))));
        rutas.agregar(publica("GET", "/api/filtro", s -> {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("cliente", s.consultaUuid("cliente"));
            cuerpo.put("estado", s.consultaEnum("estado", Estado.class));
            cuerpo.put("q", s.consulta("q").orElse(null));
            return Respuesta.ok(cuerpo);
        }));
        rutas.agregar(publica("GET", "/api/texto", s -> Respuesta.ok(Map.of("texto", "Niño ñandú 😀 café"))));
        rutas.agregar(publica("GET", "/api/espera", s -> {
            liberado.await(10, TimeUnit.SECONDS);
            return Respuesta.ok(Map.of("espero", true));
        }));
        rutas.agregar(publica("GET", "/api/libera", s -> {
            liberado.countDown();
            return Respuesta.ok(Map.of("libero", true));
        }));
        rutas.agregar(publica("POST", "/api/auth/login", s -> Respuesta.ok(Map.of("token", "t"))));
        rutas.agregar(new Ruta("GET", "/api/citas", Set.of("ver_citas"), false, s -> {
            manejadoresEjecutados.incrementAndGet();
            return Respuesta.ok(Map.of("lista", true));
        }, null));
        rutas.agregar(new Ruta("POST", "/api/citas", Set.of("ver_citas"), false, s -> Respuesta.ok(s.cuerpoObjeto()),
                null));
        rutas.agregar(conSesion("GET", "/api/yo", s -> Respuesta.ok(Map.of("id", s.usuario().id()))));
        rutas.agregar(publica("GET", "/api/publica", s -> {
            try {
                s.usuario();
                return Respuesta.ok(Map.of("usuario", true));
            } catch (IllegalStateException e) {
                return Respuesta.ok(Map.of("usuario", false));
            }
        }));
        return rutas;
    }

    private static String cuerpoJsonDe(int bytes) {
        // {"a":"xxxx"} de exactamente `bytes` bytes.
        int relleno = bytes - "{\"a\":\"\"}".length();
        return "{\"a\":\"" + "x".repeat(relleno) + "\"}";
    }

    private static void esProblema(Resultado r, int estado, String codigo, String mensaje) {
        assertEquals(estado, r.estado(), r.cuerpo());
        assertTrue(r.cabecera("Content-Type").startsWith("application/problem+json"), r.cabecera("Content-Type"));
        Map<String, Object> cuerpo = r.json();
        assertEquals(codigo, cuerpo.get("codigo"));
        assertEquals(mensaje, cuerpo.get("mensaje"));
        assertEquals(mensaje, cuerpo.get("detail"));
        assertEquals((long) estado, cuerpo.get("status"));
        assertEquals("about:blank", cuerpo.get("type"));
    }

    // ------------------------------------------------------------------ enrutado

    @Test
    void elParametroDeLaRutaSeDecodificaYLaRutaSeConservaTalCualLlego() {
        Resultado r = http.get("/api/eco/ni%C3%B1o");

        assertEquals(200, r.estado());
        assertEquals("application/json; charset=utf-8", r.cabecera("Content-Type"));
        assertEquals("niño", r.json().get("id"));
        assertEquals("/api/eco/ni%C3%B1o", r.json().get("ruta"));
    }

    @Test
    void unaBarraCodificadaNoPartePorLaMitadElParametro() {
        assertEquals("a/b", http.get("/api/eco/a%2Fb").json().get("id"));
        assertEquals("a/b", http.get("/api/eco/a%2fb").json().get("id"));
        esProblema(http.get("/api/eco/a/b"), 404, "NO_ENCONTRADO", "No se encontró el recurso solicitado.");
    }

    @Test
    void elMasEnLaRutaNoEsUnEspacioYSeAceptaLaEspecialCodificada() {
        assertEquals("a+b", http.get("/api/eco/a+b").json().get("id"));
        assertEquals("a b", http.get("/api/eco/a%20b").json().get("id"));
    }

    @Test
    void unaCodificacionInvalidaEnLaRutaEsUn400() {
        // UTF-8 inválido tras decodificar: llega al enrutador, que responde el JSON de error.
        esProblema(http.get("/api/eco/%C3%28"), 400, "VALIDACION", "La petición no es válida.");
        // Escapes mal formados: el propio servidor del JDK rechaza la línea de petición con un 400 sin cuerpo.
        assertEquals(400, http.estadoDeDestinoCrudo("/api/eco/%ZZ"));
        assertEquals(400, http.estadoDeDestinoCrudo("/api/eco/abc%2"));
        assertEquals(200, http.estadoDeDestinoCrudo("/api/eco/abc"));
    }

    @Test
    void elLiteralGanaAlParametro() {
        assertEquals("literal", http.get("/api/citas/hoy").json().get("quien"));
        assertEquals("parametro", http.get("/api/citas/ma%C3%B1ana").json().get("quien"));
    }

    @Test
    void laBarraFinalYLasRutasSinPatronSon404() {
        esProblema(http.get("/api/eco/1/"), 404, "NO_ENCONTRADO", "No se encontró el recurso solicitado.");
        esProblema(http.get("/api/eco/"), 404, "NO_ENCONTRADO", "No se encontró el recurso solicitado.");
        esProblema(http.get("/api//eco/1"), 404, "NO_ENCONTRADO", "No se encontró el recurso solicitado.");
        esProblema(http.get("/"), 404, "NO_ENCONTRADO", "No se encontró el recurso solicitado.");
        esProblema(http.get("/no/existe"), 404, "NO_ENCONTRADO", "No se encontró el recurso solicitado.");
        assertEquals(0, manejadoresEjecutados.get());
    }

    @Test
    void elCuerpoDeErrorTraeLaRutaComoInstanceYLaFraseDelEstado() {
        Map<String, Object> cuerpo = http.get("/no/existe?x=1").json();

        assertEquals("/no/existe", cuerpo.get("instance"));
        assertEquals("Not Found", cuerpo.get("title"));
        assertFalse(cuerpo.containsKey("campos"));
    }

    @Test
    void unMetodoQueLaRutaNoTieneEs405ConAllow() {
        Resultado r = http.enviar("POST", "/api/eco/1", "{}");

        esProblema(r, 405, "VALIDACION", "Método no permitido para este recurso.");
        assertEquals("GET, HEAD, OPTIONS", r.cabecera("Allow"));
        assertEquals("Method Not Allowed", r.json().get("title"));

        assertEquals("GET, HEAD, POST, OPTIONS", http.enviar("PUT", "/api/citas", "{}", "Authorization", "Bearer admin")
                .cabecera("Allow"));
        assertEquals("DELETE, OPTIONS", http.get("/api/cosas/1").cabecera("Allow"));
    }

    @Test
    void unMetodoDesconocidoEs405EnUnaRutaExistenteY404EnUnaInexistente() {
        assertEquals(405, http.enviar("PROPFIND", "/api/eco/1", (byte[]) null).estado());
        assertEquals(405, http.enviar("get", "/api/eco/1", (byte[]) null).estado());
        assertEquals(404, http.enviar("PROPFIND", "/no/existe", (byte[]) null).estado());
        assertEquals(0, manejadoresEjecutados.get());
    }

    @Test
    void headAtiendeLaRutaGetSinCuerpoYConElMismoLargo() {
        Resultado get = http.get("/api/eco/xyz");
        Resultado head = http.enviar("HEAD", "/api/eco/xyz", (byte[]) null);

        assertEquals(200, head.estado());
        assertEquals(0, head.bytes().length);
        assertEquals(String.valueOf(get.bytes().length), head.cabecera("Content-Length"));
        assertEquals("application/json; charset=utf-8", head.cabecera("Content-Type"));
    }

    @Test
    void headSobreUnaRutaSinGetEs405() {
        Resultado r = http.enviar("HEAD", "/api/crear", (byte[]) null);

        assertEquals(405, r.estado());
        assertEquals(0, r.bytes().length);
        assertEquals("POST, OPTIONS", r.cabecera("Allow"));
    }

    @Test
    void optionsSinOrigenListaLosMetodosDeUnaRutaExistente() {
        Resultado r = http.enviar("OPTIONS", "/api/eco/1", (byte[]) null);

        assertEquals(200, r.estado());
        assertEquals("GET, HEAD, OPTIONS", r.cabecera("Allow"));
        assertEquals(0, r.bytes().length);
        assertNull(r.cabecera("Access-Control-Allow-Origin"));
    }

    @Test
    void optionsSinOrigenEnUnaRutaInexistenteEs404() {
        esProblema(http.enviar("OPTIONS", "/no/existe", (byte[]) null), 404, "NO_ENCONTRADO",
                "No se encontró el recurso solicitado.");
    }

    // ------------------------------------------------------------------ respuestas

    @Test
    void creadaSinContenidoYVaciaLlevanSuEstadoYSoloLoNecesario() {
        Resultado creada = http.enviar("POST", "/api/crear", "{}");
        assertEquals(201, creada.estado());
        assertEquals(true, creada.json().get("creado"));

        Resultado sinContenido = http.enviar("DELETE", "/api/cosas/1", (byte[]) null);
        assertEquals(204, sinContenido.estado());
        assertEquals(0, sinContenido.bytes().length);
        assertNull(sinContenido.cabecera("Content-Type"));

        Resultado vacia = http.get("/api/vacia");
        assertEquals(200, vacia.estado());
        assertEquals(0, vacia.bytes().length);
        assertEquals("0", vacia.cabecera("Content-Length"));
        assertNull(vacia.cabecera("Content-Type"));
    }

    @Test
    void lasTildesLaEnieYElEmojiViajanIntactosEnLaRespuestaYEnElCuerpoDeLaPeticion() {
        assertEquals("Niño ñandú 😀 café", http.get("/api/texto").json().get("texto"));

        Resultado eco = http.enviar("POST", "/api/cuerpo", "{\"n\":\"Niño ñandú 😀\"}",
                "Content-Type", "application/json; charset=UTF-8");
        assertEquals("Niño ñandú 😀", eco.json().get("n"));
    }

    @Test
    void todaRespuestaLlevaNoSniffYNoGuardarEnCache() {
        for (Resultado r : List.of(http.get("/api/eco/1"), http.get("/no/existe"), http.get("/api/citas"))) {
            assertEquals("nosniff", r.cabecera("X-Content-Type-Options"));
            assertEquals("no-store", r.cabecera("Cache-Control"));
        }
    }

    // ------------------------------------------------------------------ cuerpo

    @Test
    void unCuerpoQueNoEsJsonEsUn400QueNoReflejaLoRecibido() {
        Resultado r = http.enviar("POST", "/api/cuerpo", "esto-no-es-json-secreto");

        esProblema(r, 400, "VALIDACION", "Hay datos inválidos en la petición.");
        assertFalse(r.cuerpo().contains("secreto"));
    }

    @Test
    void unCuerpoQueNoEsUnObjetoOEstaVacioEsUn400() {
        for (String cuerpo : List.of("[]", "[{}]", "5", "\"texto\"", "null", "true", "  ", "{\"a\":1} x", "{")) {
            esProblema(http.enviar("POST", "/api/cuerpo", cuerpo), 400, "VALIDACION",
                    "Hay datos inválidos en la petición.");
        }
        esProblema(http.enviar("POST", "/api/cuerpo", (byte[]) null), 400, "VALIDACION",
                "Hay datos inválidos en la petición.");
        esProblema(http.enviar("POST", "/api/cuerpo", ""), 400, "VALIDACION", "Hay datos inválidos en la petición.");
    }

    @Test
    void unCuerpoConUtf8InvalidoEsUn400() {
        byte[] malo = {'{', '"', 'a', '"', ':', '"', (byte) 0xC3, (byte) 0x28, '"', '}'};

        esProblema(http.enviar("POST", "/api/cuerpo", malo), 400, "VALIDACION", "Hay datos inválidos en la petición.");
    }

    @Test
    void laMarcaDeOrdenDeBytesInicialSeIgnora() {
        byte[] conMarca = ((char) 0xFEFF + "{\"a\":1}").getBytes(StandardCharsets.UTF_8);

        assertEquals(1L, http.enviar("POST", "/api/cuerpo", conMarca).json().get("a"));
    }

    @Test
    void sinContentTypeSeAceptaUnCuerpoJsonValido() {
        Resultado r = http.enviar("POST", "/api/cuerpo", "{\"a\":1}");

        assertEquals(200, r.estado());
        assertEquals(1L, r.json().get("a"));
    }

    @Test
    void conContentTypeJsonSeAcepta() {
        for (String tipo : List.of("application/json", "Application/JSON; charset=utf-8", "application/merge-patch+json")) {
            assertEquals(200, http.enviar("POST", "/api/cuerpo", "{}", "Content-Type", tipo).estado(), tipo);
        }
    }

    @Test
    void unContentTypeQueNoEsJsonEs415() {
        esProblema(http.enviar("POST", "/api/cuerpo", "{\"a\":1}", "Content-Type", "text/plain"), 415, "VALIDACION",
                "Tipo de contenido no soportado.");
        esProblema(http.enviar("POST", "/api/cuerpo", "a=1", "Content-Type", "application/x-www-form-urlencoded"),
                415, "VALIDACION", "Tipo de contenido no soportado.");
    }

    @Test
    void unCuerpoDeExactamenteUnMegabyteSeAcepta() {
        String cuerpo = cuerpoJsonDe(UN_MEGABYTE);
        assertEquals(UN_MEGABYTE, cuerpo.getBytes(StandardCharsets.UTF_8).length);

        Resultado r = http.enviar("POST", "/api/cuerpo", cuerpo, "Content-Type", "application/json");

        assertEquals(200, r.estado());
    }

    @Test
    void unCuerpoDeUnMegabyteMasUnByteEs413() {
        Resultado r = http.enviar("POST", "/api/cuerpo", cuerpoJsonDe(UN_MEGABYTE + 1),
                "Content-Type", "application/json");

        esProblema(r, 413, "VALIDACION", "La petición es demasiado grande.");
        assertEquals("Content Too Large", r.json().get("title"));
    }

    @Test
    void unCuerpoMuchoMayorTambienEs413SinCortarLaConexion() {
        Resultado r = http.enviar("POST", "/api/cuerpo", cuerpoJsonDe(3 * UN_MEGABYTE));

        esProblema(r, 413, "VALIDACION", "La petición es demasiado grande.");
        assertEquals(200, http.get("/api/eco/1").estado());
    }

    @Test
    void sinSesionNoSeLeeElCuerpo() {
        // La autenticación va antes que el cuerpo: sin sesión es un 401 aunque el cuerpo sea basura o enorme.
        esProblema(http.enviar("POST", "/api/citas", "basura"), 401, "NO_AUTENTICADO", "Inicia sesión para continuar.");
        esProblema(http.enviar("POST", "/api/citas", cuerpoJsonDe(2 * UN_MEGABYTE)), 401, "NO_AUTENTICADO",
                "Inicia sesión para continuar.");
    }

    // ------------------------------------------------------------------ parámetros

    @Test
    void unUuidInvalidoEnLaRutaEsUn400() {
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid.toString(), http.get("/api/uuid/" + uuid).json().get("id"));

        for (String malo : List.of("no-es-uuid", "1-1-1-1-1", "0000000000000000000000000000000000000",
                "g0000000-0000-0000-0000-000000000000")) {
            esProblema(http.get("/api/uuid/" + malo), 400, "VALIDACION", "La petición no es válida.");
        }
    }

    @Test
    void losParametrosDeConsultaSeDecodificanYValidan() {
        UUID cliente = UUID.randomUUID();

        Map<String, Object> cuerpo = http.get("/api/filtro?cliente=" + cliente + "&estado=CONFIRMADA&q=ni%C3%B1o+ana")
                .json();

        assertEquals(cliente.toString(), cuerpo.get("cliente"));
        assertEquals("CONFIRMADA", cuerpo.get("estado"));
        assertEquals("niño ana", cuerpo.get("q"));
    }

    @Test
    void sinFiltrosOConValoresVaciosLosFiltrosSonNulos() {
        assertEquals(Map.of(), http.get("/api/filtro").json());
        assertEquals(Map.of("q", ""), http.get("/api/filtro?cliente=&estado=&q=").json());
    }

    @Test
    void unUuidOUnEstadoInvalidosEnLaConsultaSonUn400() {
        esProblema(http.get("/api/filtro?cliente=xx"), 400, "VALIDACION", "La petición no es válida.");
        esProblema(http.get("/api/filtro?estado=DESCONOCIDO"), 400, "VALIDACION", "La petición no es válida.");
        esProblema(http.get("/api/filtro?estado=confirmada"), 400, "VALIDACION", "La petición no es válida.");
        esProblema(http.get("/api/filtro?q=%C3"), 400, "VALIDACION", "La petición no es válida.");
        assertEquals(400, http.estadoDeDestinoCrudo("/api/filtro?q=%ZZ"));
    }

    @Test
    void conUnParametroRepetidoGanaElPrimero() {
        Map<String, Object> cuerpo = http.get("/api/filtro?estado=PENDIENTE&estado=CONFIRMADA&q=a&q=b").json();

        assertEquals("PENDIENTE", cuerpo.get("estado"));
        assertEquals("a", cuerpo.get("q"));
    }

    // ------------------------------------------------------------------ errores

    @Test
    void unErrorDeNegocioConCamposLosDevuelve() {
        Resultado r = http.get("/api/negocio");

        esProblema(r, 400, "VALIDACION", "Hay datos inválidos en la petición.");
        assertEquals(Map.of("nombre", "no puede estar vacío", "duracion", "debe ser mayor que 0"),
                r.json().get("campos"));
        assertEquals("/api/negocio", r.json().get("instance"));
    }

    @Test
    void unErrorDeNegocioUsaElEstadoDeSuCodigo() {
        Resultado r = http.get("/api/gone");

        esProblema(r, 410, "ENLACE_VENCIDO", "El enlace venció.");
        assertEquals("Gone", r.json().get("title"));
    }

    @Test
    void unaExcepcionInesperadaEsUn500SinTrazasNiDetallesYElServidorSigueVivo() {
        for (String ruta : List.of("/api/falla", "/api/falla-error", "/api/no-serializable", "/api/nada")) {
            Resultado r = http.get(ruta);

            esProblema(r, 500, "ERROR_INTERNO", "Ocurrió un error inesperado. Inténtalo de nuevo más tarde.");
            assertFalse(r.cuerpo().contains("secreto"), r.cuerpo());
            assertFalse(r.cuerpo().contains("at com."), r.cuerpo());
            assertFalse(r.cuerpo().contains("Exception"), r.cuerpo());
        }
        assertEquals(200, http.get("/api/eco/1").estado());
    }

    @Test
    void muchasPeticionesQueFallanNoDejanElServidorColgado() {
        for (int i = 0; i < 50; i++) {
            assertEquals(500, http.get("/api/falla").estado());
            assertEquals(404, http.get("/no/existe").estado());
        }
        assertEquals(200, http.get("/api/eco/1").estado());
    }

    // ------------------------------------------------------------------ autenticación

    @Test
    void sinTokenOConUnTokenInvalidoEs401ConWwwAuthenticate() {
        Resultado sin = http.get("/api/citas");
        esProblema(sin, 401, "NO_AUTENTICADO", "Inicia sesión para continuar.");
        assertEquals("Bearer", sin.cabecera("WWW-Authenticate"));
        assertEquals("Unauthorized", sin.json().get("title"));

        esProblema(http.get("/api/citas", "Authorization", "Bearer basura"), 401, "NO_AUTENTICADO",
                "Inicia sesión para continuar.");
        esProblema(http.get("/api/yo"), 401, "NO_AUTENTICADO", "Inicia sesión para continuar.");
        assertEquals(0, manejadoresEjecutados.get());
    }

    @Test
    void conSesionPeroSinElPermisoEs403() {
        Resultado r = http.get("/api/citas", "Authorization", "Bearer basico");

        esProblema(r, 403, "SIN_PERMISO", "No tienes permiso para realizar esta acción.");
        assertNull(r.cabecera("WWW-Authenticate"));
        assertEquals(0, manejadoresEjecutados.get());
    }

    @Test
    void conSesionYPermisoLaRutaResponde() {
        assertEquals(200, http.get("/api/citas", "Authorization", "Bearer admin").estado());
        assertEquals(1, manejadoresEjecutados.get());
    }

    @Test
    void unaRutaSoloConSesionNoPideAlgunPermiso() {
        Resultado r = http.get("/api/yo", "Authorization", "Bearer basico");

        assertEquals(200, r.estado());
        assertEquals("00000000-0000-0000-0000-000000000002", r.json().get("id"));
    }

    @Test
    void laRutaPublicaNiSiquieraLeeLaCabeceraAuthorizationYNoTieneUsuario() {
        assertEquals(200, http.get("/api/publica", "Authorization", "Bearer explota").estado());
        assertEquals(200, http.enviar("POST", "/api/auth/login", "{}", "Authorization", "Bearer vencido.roto").estado());
        assertEquals(false, http.get("/api/publica", "Authorization", "Basura sin forma").json().get("usuario"));
        assertEquals(0, autenticacion.llamadas.get());
    }

    @Test
    void siLaAutenticacionFallaDeFormaInesperadaEsUn500() {
        esProblema(http.get("/api/citas", "Authorization", "Bearer explota"), 500, "ERROR_INTERNO",
                "Ocurrió un error inesperado. Inténtalo de nuevo más tarde.");
    }

    // ------------------------------------------------------------------ cuerpos ilegibles o que no llegan

    private static final String POST_CUERPO = "POST /api/cuerpo HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n";

    /** Ejecuta la acción y devuelve lo que se registró como aviso o error (nada, si todo va bien). */
    private List<LogRecord> registrosGraves(Runnable accion) {
        List<LogRecord> graves = new CopyOnWriteArrayList<>();
        Handler captura = new Handler() {
            @Override
            public void publish(LogRecord registro) {
                if (registro.getLevel().intValue() >= Level.WARNING.intValue()) {
                    graves.add(registro);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger servidorLog = Logger.getLogger(ServidorHttp.class.getName());
        Level nivelErrores = registroDeErrores.getLevel();
        Level nivelServidor = servidorLog.getLevel();
        registroDeErrores.setLevel(Level.ALL);
        servidorLog.setLevel(Level.ALL);
        registroDeErrores.addHandler(captura);
        servidorLog.addHandler(captura);
        try {
            accion.run();
        } finally {
            registroDeErrores.removeHandler(captura);
            servidorLog.removeHandler(captura);
            registroDeErrores.setLevel(nivelErrores);
            servidorLog.setLevel(nivelServidor);
        }
        return graves;
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Espera a que el servidor cierre la conexión (EOF o reinicio); falla si en cambio vence el tiempo del socket. */
    private static void esperarCierre(Socket socket) throws IOException {
        try {
            assertEquals(-1, socket.getInputStream().read(), "el servidor no debía responder nada");
        } catch (SocketTimeoutException e) {
            throw e;
        } catch (SocketException e) {
            // Reinicio de la conexión: también es una conexión liberada.
        }
    }

    @Test
    void unTroceadoMalFormadoEsUn400YNoUnErrorRegistrado() {
        List<LogRecord> graves = registrosGraves(() -> {
            String respuesta = http.enviarCrudo(
                    POST_CUERPO + "Transfer-Encoding: chunked\r\n\r\nZZ\r\nabc\r\n0\r\n\r\n", false);

            assertTrue(respuesta.startsWith("HTTP/1.1 400"), respuesta);
            assertTrue(respuesta.contains("\"codigo\":\"VALIDACION\""), respuesta);
            assertFalse(respuesta.contains("chunk"), respuesta);
        });

        assertEquals(List.of(), graves);
        assertEquals(200, http.get("/api/eco/1").estado());
    }

    @Test
    void unCuerpoTruncadoPorUnClienteQueSeCallaEsUn400YNoUnErrorRegistrado() {
        List<LogRecord> graves = registrosGraves(() -> {
            String respuesta = http.enviarCrudo(POST_CUERPO + "Content-Length: 100\r\n\r\n{\"a\":1}", true);

            assertTrue(respuesta.startsWith("HTTP/1.1 400"), respuesta);
        });

        assertEquals(List.of(), graves);
        assertEquals(200, http.get("/api/eco/1").estado());
    }

    @Test
    void unaConexionCortadaACuerpoMedioNoRegistraErrores() throws Exception {
        List<LogRecord> graves = registrosGraves(() -> {
            try (Socket socket = http.abrirSocket()) {
                socket.getOutputStream().write((POST_CUERPO + "Content-Length: 5000\r\n\r\n{\"a\":")
                        .getBytes(StandardCharsets.ISO_8859_1));
                socket.getOutputStream().flush();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            dormir(400);
        });

        assertEquals(List.of(), graves);
        assertEquals(200, http.get("/api/eco/1").estado());
    }

    @Test
    void unCuerpoQueNoTerminaDeLlegarSeCortaAlVencerElPlazoYLasDemasPeticionesSiguen() throws Exception {
        try (ClienteHttpPrueba lento = ClienteHttpPrueba.iniciarConPlazo(Duration.ofSeconds(1), rutas(), autenticacion,
                ORIGEN); Socket parado = lento.abrirSocket()) {
            long inicio = System.nanoTime();
            parado.getOutputStream().write((POST_CUERPO + "Content-Length: 1000\r\n\r\nabc")
                    .getBytes(StandardCharsets.ISO_8859_1));
            parado.getOutputStream().flush();

            assertEquals(200, lento.get("/api/eco/1").estado(), "mientras tanto el servidor atiende a los demás");
            esperarCierre(parado);

            long milisegundos = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
            assertTrue(milisegundos >= 900 && milisegundos < 6000, milisegundos + " ms");
            assertEquals(200, lento.get("/api/eco/1").estado());
        }
    }

    @Test
    void elCuerpoQueNoTerminaDeLlegarEnUnaRutaRechazadaTambienSeCorta() throws Exception {
        // Sin sesión se rechaza antes de leer el cuerpo y luego se descarta; ese descarte también tiene plazo.
        try (ClienteHttpPrueba lento = ClienteHttpPrueba.iniciarConPlazo(Duration.ofSeconds(1), rutas(), autenticacion,
                ORIGEN); Socket parado = lento.abrirSocket()) {
            long inicio = System.nanoTime();
            parado.getOutputStream().write(("POST /api/citas HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n"
                    + "Content-Length: 1000\r\n\r\nabc").getBytes(StandardCharsets.ISO_8859_1));
            parado.getOutputStream().flush();

            esperarCierre(parado);

            long milisegundos = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
            assertTrue(milisegundos >= 900 && milisegundos < 6000, milisegundos + " ms");
            assertEquals(200, lento.get("/api/eco/1").estado());
        }
    }

    @Test
    void elPlazoNoCortaUnCuerpoLentoPeroCompletoNiLaEjecucionPosteriorDelManejador() throws Exception {
        try (ClienteHttpPrueba lento = ClienteHttpPrueba.iniciarConPlazo(Duration.ofSeconds(1), rutas(), autenticacion,
                ORIGEN); Socket socket = lento.abrirSocket()) {
            socket.getOutputStream().write((POST_CUERPO.replace("/api/cuerpo", "/api/lenta-despues")
                    + "Content-Length: 7\r\n\r\n{\"a\"").getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            dormir(400);
            socket.getOutputStream().write(":1}".getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();

            // El manejador tarda 1,5 s después de leer el cuerpo: más que el plazo, que ya no corre.
            String respuesta = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(respuesta.startsWith("HTTP/1.1 200"), respuesta);
            assertTrue(respuesta.contains("\"ok\":true"), respuesta);
        }
    }

    @Test
    void elPlazoDeLecturaPorDefectoEs30SegundosYElServidorDelJdkCierraLasPeticionesAMedias() {
        assertEquals(Duration.ofSeconds(30), ServidorHttp.PLAZO_LECTURA);
        // Para las cabeceras, que llegan antes de que haya un manejador: la propiedad la lee el servidor del JDK.
        assertEquals("30", System.getProperty("sun.net.httpserver.maxReqTime"));
    }

    // ------------------------------------------------------------------ concurrencia

    @Test
    void unaPeticionLentaNoBloqueaALasDemas() throws Exception {
        CompletableFuture<Resultado> lenta = CompletableFuture.supplyAsync(() -> http.get("/api/espera"));

        // La petición lenta sigue esperando mientras otra la libera: el servidor atiende en paralelo.
        assertEquals(200, http.get("/api/libera").estado());
        assertEquals(200, lenta.get(10, TimeUnit.SECONDS).estado());
    }

    @Test
    void variasPeticionesSimultaneasSeAtiendenTodas() throws Exception {
        List<CompletableFuture<Resultado>> futuros = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            String id = "n" + i;
            futuros.add(CompletableFuture.supplyAsync(() -> http.get("/api/eco/" + id)));
        }
        for (int i = 0; i < 40; i++) {
            assertEquals("n" + i, futuros.get(i).get(20, TimeUnit.SECONDS).json().get("id"));
        }
    }

    // ------------------------------------------------------------------ CORS

    private Resultado preflight(String ruta, String origen, String metodo, String cabeceras) {
        String[] extra = cabeceras == null
                ? new String[] {"Origin", origen, "Access-Control-Request-Method", metodo}
                : new String[] {"Origin", origen, "Access-Control-Request-Method", metodo,
                        "Access-Control-Request-Headers", cabeceras};
        return http.enviar("OPTIONS", ruta, (byte[]) null, extra);
    }

    @Test
    void elPreflightDeUnOrigenPermitidoResponde200ConLasCabecerasCors() {
        Resultado r = preflight("/api/auth/login", ORIGEN, "POST", "content-type, authorization");

        assertEquals(200, r.estado());
        assertEquals(ORIGEN, r.cabecera("Access-Control-Allow-Origin"));
        assertEquals("GET,POST,PUT,PATCH,DELETE,OPTIONS", r.cabecera("Access-Control-Allow-Methods"));
        assertEquals("Authorization,Content-Type", r.cabecera("Access-Control-Allow-Headers"));
        assertEquals("3600", r.cabecera("Access-Control-Max-Age"));
        assertNull(r.cabecera("Access-Control-Allow-Credentials"));
        String vary = String.join(",", r.cabeceras().allValues("Vary")).toLowerCase();
        assertTrue(vary.contains("origin") && vary.contains("access-control-request-method")
                && vary.contains("access-control-request-headers"), vary);
        assertEquals(0, r.bytes().length);
    }

    @Test
    void elPreflightNoEjecutaLaRutaNiPideSesion() {
        Resultado r = preflight("/api/citas", ORIGEN, "GET", "authorization");

        assertEquals(200, r.estado());
        assertEquals(0, autenticacion.llamadas.get());
        assertEquals(0, manejadoresEjecutados.get());
    }

    @Test
    void elPreflightDeUnaRutaQueNoExisteTambienSeResponde() {
        assertEquals(200, preflight("/no/existe", ORIGEN, "DELETE", null).estado());
    }

    @Test
    void losNombresDeCabeceraDelPreflightNoDistinguenMayusculas() {
        assertEquals(200, preflight("/api/citas", ORIGEN, "PATCH", "AUTHORIZATION,Content-type").estado());
    }

    @Test
    void elOrigenSeComparaSinDistinguirMayusculasNiBarraFinal() {
        assertEquals("https://otro.ejemplo", preflight("/api/citas", "https://otro.ejemplo", "GET", null)
                .cabecera("Access-Control-Allow-Origin"));
        assertEquals("HTTPS://OTRO.EJEMPLO", preflight("/api/citas", "HTTPS://OTRO.EJEMPLO", "GET", null)
                .cabecera("Access-Control-Allow-Origin"));
    }

    @Test
    void elPreflightDeUnOrigenNoPermitidoEs403SinCabecerasCors() {
        Resultado r = preflight("/api/auth/login", "http://malicioso.example", "POST", "content-type");

        assertEquals(403, r.estado());
        assertEquals("Invalid CORS request", r.cuerpo());
        assertNull(r.cabecera("Access-Control-Allow-Origin"));
    }

    @Test
    void elPreflightConMetodoOCabeceraNoPermitidosEs403() {
        assertEquals(403, preflight("/api/citas", ORIGEN, "TRACE", null).estado());
        assertEquals(403, preflight("/api/citas", ORIGEN, "POST", "x-secreta").estado());
        assertEquals(403, preflight("/api/citas", ORIGEN, "POST", "content-type, x-secreta").estado());
    }

    @Test
    void unaPeticionRealDeUnOrigenPermitidoLlevaAllowOriginYVary() {
        Resultado r = http.enviar("POST", "/api/auth/login", "{}", "Origin", ORIGEN);

        assertEquals(200, r.estado());
        assertEquals(ORIGEN, r.cabecera("Access-Control-Allow-Origin"));
        assertTrue(r.cabeceras().allValues("Vary").stream().anyMatch(v -> v.contains("Origin")));
        assertNull(r.cabecera("Access-Control-Allow-Methods"));
        assertNull(r.cabecera("Access-Control-Max-Age"));
    }

    @Test
    void tambienLasRespuestasDeErrorLlevanLasCabecerasCorsParaQueElNavegadorLasLea() {
        for (Resultado r : List.of(http.get("/no/existe", "Origin", ORIGEN), http.get("/api/citas", "Origin", ORIGEN),
                http.get("/api/falla", "Origin", ORIGEN))) {
            assertEquals(ORIGEN, r.cabecera("Access-Control-Allow-Origin"), r.cuerpo());
        }
    }

    @Test
    void unaPeticionRealDeUnOrigenNoPermitidoEs403YNoEjecutaLaRuta() {
        Resultado r = http.enviar("POST", "/api/auth/login", "{}", "Origin", "http://malicioso.example");

        assertEquals(403, r.estado());
        assertEquals("Invalid CORS request", r.cuerpo());
        assertNull(r.cabecera("Access-Control-Allow-Origin"));
        assertEquals(0, manejadoresEjecutados.get());
    }

    @Test
    void unaPeticionDelMismoOrigenNoEsCorsAunqueNoEsteConfigurado() {
        Resultado r = http.get("/api/eco/1", "Origin", http.base());

        assertEquals(200, r.estado());
        assertNull(r.cabecera("Access-Control-Allow-Origin"));
    }

    @Test
    void sinOrigenNoHayCabecerasCors() {
        Resultado r = http.get("/api/eco/1");

        assertNull(r.cabecera("Access-Control-Allow-Origin"));
        assertNull(r.cabecera("Vary"));
    }

    @Test
    void optionsConOrigenPermitidoPeroSinMetodoSolicitadoNoEsPreflight() {
        Resultado r = http.enviar("OPTIONS", "/api/eco/1", (byte[]) null, "Origin", ORIGEN);

        assertEquals(200, r.estado());
        assertEquals("GET, HEAD, OPTIONS", r.cabecera("Allow"));
        assertEquals(ORIGEN, r.cabecera("Access-Control-Allow-Origin"));
        assertNull(r.cabecera("Access-Control-Max-Age"));
    }

    @Test
    void unOrigenComodinPermiteCualquierOrigen() {
        try (ClienteHttpPrueba abierto = ClienteHttpPrueba.iniciar(rutas(), autenticacion, "*")) {
            assertEquals("*", abierto.get("/api/eco/1", "Origin", "http://cualquiera.example")
                    .cabecera("Access-Control-Allow-Origin"));
        }
    }

    @Test
    void sinOrigenesConfiguradosTodaPeticionDeOtroOrigenEs403() {
        try (ClienteHttpPrueba cerrado = ClienteHttpPrueba.iniciar(rutas(), autenticacion)) {
            assertEquals(403, cerrado.get("/api/eco/1", "Origin", ORIGEN).estado());
            assertEquals(200, cerrado.get("/api/eco/1").estado());
        }
    }

    @Test
    void elMetodoYLasCabecerasPermitidasSonLasDelContratoOriginal() {
        // Misma lista que ConfiguracionSeguridad de la versión con Spring.
        assertEquals(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
                Arrays.asList(preflight("/api/citas", ORIGEN, "GET", null).cabecera("Access-Control-Allow-Methods")
                        .split(",")));
    }
}
