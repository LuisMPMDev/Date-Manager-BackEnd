package com.datemanager;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.infraestructura.AdministradorInicial;
import com.datemanager.infraestructura.Aplicacion;
import com.datemanager.infraestructura.Configuracion;
import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.ManejadorErrores;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.persistencia.ErrorSql;
import com.datemanager.infraestructura.seguridad.ServicioJwt;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base de las pruebas de la API por HTTP: una sola {@link Aplicacion} por JVM, sobre el pool de {@link PruebaBase},
 * escuchando en un puerto efímero, con un {@link RelojMutable} en la zona {@code America/Bogota}, el secreto JWT de
 * pruebas, CORS solo para {@value #ORIGEN_CORS} y sin administrador inicial. Antes de cada prueba se vacían las tablas
 * (en {@link PruebaBase}) y el reloj vuelve a la hora real.
 *
 * <p>El cliente {@code get/post/put/delete(ruta).conToken(t).cuerpo(objeto).enviar()} devuelve un {@link Resp}, y
 * {@link Resp#en(String)} lee el JSON con una ruta mínima al estilo JSONPath ({@code a.b[0].c}, {@code [*].id},
 * {@code campos['cliente.nombre']}; el {@code $} inicial es opcional).
 */
public abstract class PruebaIntegracion extends PruebaBase {

    public static final String SECRETO = "secreto-falso-solo-para-pruebas-0123456789abcdef";
    public static final String ORIGEN_CORS = "http://localhost:4200";
    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private static final HttpClient CLIENTE = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** La aplicación compartida: se crea al usarla por primera vez, cuando {@link PruebaBase} ya tiene el pool. */
    private static final class Compartida {
        static final RelojMutable RELOJ = new RelojMutable(ZONA);
        static final ServicioJwt JWT = new ServicioJwt(SECRETO, RELOJ);
        static final Configuracion CFG = new Configuracion("jdbc:sqlserver://no-se-usa", "no-se-usa", "no-se-usa",
                10, SECRETO, ZONA, List.of(ORIGEN_CORS), "", "", false, 0);
        static final Aplicacion APP;
        static final String BASE;

        static {
            APP = new Aplicacion(CFG, pool, RELOJ);
            rutasDePrueba().forEach(APP.rutas()::agregar);
            // Sin administrador inicial: su aviso de arranque no ensucia la salida de pruebas.
            try (CapturaRegistro silencio = new CapturaRegistro(AdministradorInicial.class, true)) {
                APP.iniciar();
            }
            Runtime.getRuntime().addShutdownHook(new Thread(APP::detener));
            BASE = "http://127.0.0.1:" + APP.puerto();
        }
    }

    /**
     * Rutas SOLO de pruebas (lo que en la versión Spring eran {@code ControladorPrueba} y {@code ControladorPanelPrueba}):
     * protegidas para verificar 401 y 403, y una pública que lanza el error de negocio pedido.
     */
    private static List<Ruta> rutasDePrueba() {
        return List.of(
                new Ruta("GET", "/api/panel/_prueba/citas", Set.of("ver_citas"), false, s -> Respuesta.ok("ok"), null),
                new Ruta("GET", "/api/panel/_prueba/cualquiera", Set.of("gestionar_servicios", "ver_panel"), false,
                        s -> Respuesta.ok("ok"), null),
                new Ruta("GET", "/api/panel/_prueba/no-autenticado", Set.of(), false, s -> {
                    throw new ErrorNegocio(CodigoError.NO_AUTENTICADO, ManejadorErrores.MENSAJE_NO_AUTENTICADO);
                }, null),
                new Ruta("GET", "/api/publico/_prueba/error-negocio/{codigo}", Set.of(), true, s -> {
                    throw new ErrorNegocio(CodigoError.valueOf(s.parametro("codigo")), "x");
                }, null),
                // Lo que en Spring era un @NotBlank(message = "es obligatorio") sobre «nombre».
                new Ruta("POST", "/api/publico/_prueba/validacion", Set.of(), true, s -> {
                    Object nombre = s.cuerpoObjeto().get("nombre");
                    if (!(nombre instanceof String texto) || texto.isBlank()) {
                        throw new ErrorNegocio(CodigoError.VALIDACION, ManejadorErrores.MENSAJE_DATOS_INVALIDOS,
                                Map.of("nombre", "es obligatorio"));
                    }
                    return Respuesta.ok("ok");
                }, null),
                // Última línea de defensa: una violación de restricción de la base no puede ser un 500.
                new Ruta("GET", "/api/publico/_prueba/violacion-unica", Set.of(), true, s -> {
                    throw new ErrorSql(new SQLException("Violation of UNIQUE KEY (detalle interno)", "23000", 2627));
                }, null),
                // Un fallo de bloqueo de la base (interbloqueo, tiempo de espera) no puede ser un 500.
                new Ruta("GET", "/api/publico/_prueba/bloqueo", Set.of(), true, s -> {
                    throw new ErrorSql(new SQLException(
                            "Transaction was deadlocked on lock resources (detalle interno)", "40001", 1205));
                }, null),
                new Ruta("GET", "/api/publico/_prueba/inesperado", Set.of(), true, s -> {
                    throw new IllegalStateException("detalle interno que no debe filtrarse");
                }, null));
    }

    /** La configuración de la aplicación compartida (escrita a mano: nunca sale del entorno real). */
    protected static Configuracion configuracion() {
        return Compartida.CFG;
    }

    protected RelojMutable reloj;

    @BeforeEach
    void prepararIntegracion() {
        reloj = Compartida.RELOJ;
        reloj.restablecer();
    }

    /** Fija "ahora" a esa hora de pared en la zona del negocio (no en la de la JVM). */
    protected void fijarAhora(LocalDateTime ahora) {
        reloj.fijarAhora(ahora);
    }

    /** Un token válido para ese usuario, firmado como los de la aplicación y con su reloj. */
    protected static String tokenDe(UUID usuarioId) {
        return Compartida.JWT.emitir(usuarioId);
    }

    // ---------------------------------------------------------------- Cliente HTTP

    protected static Peticion get(String ruta) {
        return new Peticion("GET", ruta);
    }

    protected static Peticion post(String ruta) {
        return new Peticion("POST", ruta);
    }

    protected static Peticion put(String ruta) {
        return new Peticion("PUT", ruta);
    }

    protected static Peticion delete(String ruta) {
        return new Peticion("DELETE", ruta);
    }

    protected static Peticion options(String ruta) {
        return new Peticion("OPTIONS", ruta);
    }

    /** Una petición en construcción; {@link #enviar()} la manda a la aplicación compartida. */
    public static final class Peticion {

        private final String metodo;
        private final String ruta;
        private final List<String> cabeceras = new ArrayList<>();
        private byte[] cuerpo;

        private Peticion(String metodo, String ruta) {
            this.metodo = metodo;
            this.ruta = ruta;
        }

        public Peticion conToken(String token) {
            return cabecera("Authorization", "Bearer " + token);
        }

        public Peticion cabecera(String nombre, String valor) {
            cabeceras.add(nombre);
            cabeceras.add(valor);
            return this;
        }

        /** El objeto (mapa, lista, record...) como JSON, con {@code Content-Type: application/json}. */
        public Peticion cuerpo(Object objeto) {
            return cuerpoCrudo(Json.escribir(objeto));
        }

        /** El texto tal cual, con {@code Content-Type: application/json}. */
        public Peticion cuerpoCrudo(String texto) {
            this.cuerpo = texto.getBytes(StandardCharsets.UTF_8);
            return cabecera("Content-Type", "application/json");
        }

        public Resp enviar() {
            HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(Compartida.BASE + ruta))
                    .timeout(Duration.ofSeconds(30))
                    .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofByteArray(cuerpo));
            for (int i = 0; i + 1 < cabeceras.size(); i += 2) {
                peticion.header(cabeceras.get(i), cabeceras.get(i + 1));
            }
            try {
                HttpResponse<byte[]> respuesta = CLIENTE.send(peticion.build(), HttpResponse.BodyHandlers.ofByteArray());
                String texto = new String(respuesta.body(), StandardCharsets.UTF_8);
                String tipo = respuesta.headers().firstValue("Content-Type").orElse("");
                Object json = texto.isEmpty() || !tipo.contains("json") ? null : Json.parse(texto);
                return new Resp(respuesta.statusCode(), texto, json, respuesta.headers());
            } catch (IOException e) {
                throw new IllegalStateException("La petición " + metodo + " " + ruta + " falló: " + e, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    /** Lo que respondió la aplicación: estado, cuerpo como texto y como JSON ya leído ({@code null} si vino vacío). */
    public record Resp(int estado, String texto, Object json, HttpHeaders cabeceras) {

        /** El valor en esa ruta, o {@code null} si no existe (el JSON de la API nunca lleva {@code null}). */
        public Object en(String ruta) {
            return navegar(json, pasos(ruta), 0);
        }

        public boolean existe(String ruta) {
            return en(ruta) != null;
        }

        public String texto(String ruta) {
            return (String) en(ruta);
        }

        /** El número en esa ruta, para compararlo sin depender de si llegó como entero o decimal. */
        public BigDecimal numero(String ruta) {
            Object valor = en(ruta);
            return valor == null ? null : new BigDecimal(valor.toString());
        }

        @SuppressWarnings("unchecked")
        public List<Object> lista(String ruta) {
            return (List<Object>) en(ruta);
        }

        /** Primer valor de la cabecera, o {@code null}. */
        public String cabecera(String nombre) {
            return cabeceras.firstValue(nombre).orElse(null);
        }

        private static List<String> pasos(String ruta) {
            String resto = ruta.startsWith("$") ? ruta.substring(1) : ruta;
            List<String> pasos = new ArrayList<>();
            int i = 0;
            while (i < resto.length()) {
                char c = resto.charAt(i);
                if (c == '.') {
                    i++;
                } else if (c == '[') {
                    int fin = resto.indexOf(']', i);
                    String dentro = resto.substring(i + 1, fin);
                    pasos.add(dentro.startsWith("'") ? "." + dentro.substring(1, dentro.length() - 1) : "[" + dentro);
                    i = fin + 1;
                } else {
                    int j = i;
                    while (j < resto.length() && resto.charAt(j) != '.' && resto.charAt(j) != '[') {
                        j++;
                    }
                    pasos.add("." + resto.substring(i, j));
                    i = j;
                }
            }
            return pasos;
        }

        private static Object navegar(Object valor, List<String> pasos, int k) {
            if (k == pasos.size() || valor == null) {
                return valor;
            }
            String paso = pasos.get(k);
            if (paso.equals("[*")) {
                if (!(valor instanceof List<?> lista)) {
                    return null;
                }
                List<Object> resultado = new ArrayList<>();
                for (Object elemento : lista) {
                    resultado.add(navegar(elemento, pasos, k + 1));
                }
                return resultado;
            }
            if (paso.startsWith("[")) {
                int indice = Integer.parseInt(paso.substring(1));
                if (!(valor instanceof List<?> lista) || indice >= lista.size()) {
                    return null;
                }
                return navegar(lista.get(indice), pasos, k + 1);
            }
            if (!(valor instanceof Map<?, ?> mapa)) {
                return null;
            }
            return navegar(mapa.get(paso.substring(1)), pasos, k + 1);
        }
    }
}
