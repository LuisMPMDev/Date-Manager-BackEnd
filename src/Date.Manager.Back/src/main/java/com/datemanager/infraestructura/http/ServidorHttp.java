package com.datemanager.infraestructura.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.datemanager.infraestructura.Configuracion;
import com.datemanager.infraestructura.seguridad.Autenticacion;
import com.datemanager.infraestructura.seguridad.UsuarioActual;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Servidor HTTP del JDK con un hilo virtual por petición. Por cada petición: CORS (el preflight se responde antes
 * de enrutar), enrutado, sesión y permisos, lectura del cuerpo (máximo 1 MB), el {@link Manejador} de la ruta y la
 * escritura de la respuesta. Todo fallo, previsto o no, sale como el JSON de {@link ManejadorErrores}, y el
 * intercambio se cierra siempre.
 */
public final class ServidorHttp {

    private static final Logger LOG = Logger.getLogger(ServidorHttp.class.getName());

    /** Tiempo máximo, por defecto, para recibir el cuerpo de una petición (o descartarlo). */
    public static final Duration PLAZO_LECTURA = Duration.ofSeconds(30);

    static {
        // El servidor del JDK no corta por sí mismo a quien deja la petición a medias (cabeceras o cuerpo): con esta
        // propiedad (segundos) cierra la conexión pasado ese tiempo. Se lee una sola vez, al cargar el servidor, y
        // una -D explícita manda. Cubre las cabeceras, que ocurren antes de que haya un manejador en marcha.
        if (System.getProperty("sun.net.httpserver.maxReqTime") == null) {
            System.setProperty("sun.net.httpserver.maxReqTime", Long.toString(PLAZO_LECTURA.toSeconds()));
        }
    }

    /** Tamaño máximo del cuerpo de una petición. */
    static final int LIMITE_CUERPO = 1024 * 1024;

    /**
     * Tras pasarse del límite se siguen leyendo (y descartando) hasta estos bytes más, para que el cliente reciba
     * el 413 en lugar de una conexión cortada mientras aún envía; más allá se responde y se deja de leer.
     */
    private static final int LIMITE_DESCARTE = 4 * 1024 * 1024;

    /** Marca el intercambio cuyo cuerpo ya se leyó; si no, hay que descartarlo antes de responder. */
    private static final String ATRIBUTO_CUERPO_LEIDO = "cuerpoLeido";

    private static final List<String> METODOS_CORS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> CABECERAS_CORS = List.of("Authorization", "Content-Type");
    private static final String TIPO_JSON = "application/json; charset=utf-8";
    private static final String TIPO_PROBLEMA = "application/problem+json";
    private static final String TIPO_TEXTO = "text/plain; charset=utf-8";

    /** El plazo de lectura venció y se cerró la conexión: ya no hay a quién responder. */
    private static final class ConexionAbandonada extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ConexionAbandonada() {
            super("Se agotó el plazo de lectura de la petición.", null, false, false);
        }
    }

    @FunctionalInterface
    private interface Lectura<T> {
        T leer() throws IOException;
    }

    /** Lo que se escribe en la conexión; {@code tipo} es {@code null} si no hay cuerpo. */
    private record Salida(int estado, String tipo, byte[] cuerpo) {
    }

    private final int puerto;
    private final List<String> origenes;
    private final Enrutador rutas;
    private final Autenticacion autenticacion;
    private final Duration plazoLectura;
    private HttpServer servidor;
    private ExecutorService executor;
    private ScheduledThreadPoolExecutor vigilante;

    /**
     * @param cfg puerto (0 = uno libre cualquiera) y orígenes CORS permitidos ({@code *} para todos)
     */
    public ServidorHttp(Configuracion cfg, Enrutador rutas, Autenticacion autenticacion) {
        this(cfg, rutas, autenticacion, PLAZO_LECTURA);
    }

    /**
     * @param plazoLectura lo máximo que se espera a recibir el cuerpo de una petición; pasado ese tiempo se cierra
     *     la conexión (para quien envía el cuerpo a cuentagotas o lo deja a medias)
     */
    public ServidorHttp(Configuracion cfg, Enrutador rutas, Autenticacion autenticacion, Duration plazoLectura) {
        this.plazoLectura = plazoLectura;
        this.puerto = cfg.puerto();
        this.origenes = cfg.corsOrigenes().stream().map(ServidorHttp::sinBarraFinal).toList();
        this.rutas = rutas;
        this.autenticacion = autenticacion;
    }

    public synchronized void iniciar() {
        if (servidor != null) {
            throw new IllegalStateException("El servidor ya está iniciado.");
        }
        try {
            servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir el puerto " + puerto + ".", e);
        }
        executor = Executors.newVirtualThreadPerTaskExecutor();
        vigilante = new ScheduledThreadPoolExecutor(1, tarea -> {
            Thread hilo = new Thread(tarea, "vigilante-lectura");
            hilo.setDaemon(true);
            return hilo;
        });
        vigilante.setRemoveOnCancelPolicy(true);
        servidor.setExecutor(executor);
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    /** El puerto en que escucha (el elegido por el sistema si se pidió el 0). */
    public synchronized int puerto() {
        if (servidor == null) {
            throw new IllegalStateException("El servidor no está iniciado.");
        }
        return servidor.getAddress().getPort();
    }

    /** Deja de aceptar conexiones y espera un poco a las peticiones en curso. Se puede llamar de nuevo sin efecto. */
    public synchronized void detener() {
        if (servidor == null) {
            return;
        }
        servidor.stop(0);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        vigilante.shutdownNow();
        servidor = null;
        executor = null;
        vigilante = null;
    }

    // ------------------------------------------------------------------ una petición

    private void atender(HttpExchange intercambio) {
        String ruta = rutaCruda(intercambio);
        try {
            Salida salida;
            try {
                salida = procesar(intercambio, ruta);
            } catch (ConexionAbandonada e) {
                LOG.log(Level.FINE, "Conexión cerrada por plazo de lectura: {0}", ruta);
                return;
            } catch (Throwable t) {
                salida = deError(intercambio, t, ruta);
            }
            if (intercambio.getAttribute(ATRIBUTO_CUERPO_LEIDO) == null) {
                descartarCuerpo(intercambio.getRequestBody());
            }
            enviar(intercambio, salida);
        } catch (ConexionAbandonada e) {
            LOG.log(Level.FINE, "Conexión cerrada por plazo de lectura al descartar el cuerpo: {0}", ruta);
        } catch (Throwable t) {
            // Normalmente el cliente se fue a mitad de la respuesta; no hay a quién contestar.
            LOG.log(Level.FINE, "No se pudo escribir la respuesta", t);
        } finally {
            intercambio.close();
        }
    }

    private Salida procesar(HttpExchange intercambio, String ruta) throws Exception {
        String metodo = intercambio.getRequestMethod();
        Headers cabeceras = intercambio.getRequestHeaders();

        Salida cors = aplicarCors(intercambio, metodo, cabeceras);
        if (cors != null) {
            return cors;
        }
        if (metodo.equals("OPTIONS")) {
            intercambio.getResponseHeaders().set("Allow", rutas.allowDe(ruta));
            return new Salida(200, null, new byte[0]);
        }

        Enrutador.Coincidencia coincidencia = rutas.buscar(metodo, ruta);
        Ruta rutaElegida = coincidencia.ruta();

        // Sin leer la cabecera Authorization si la ruta es pública (un token vencido no debe romper el login).
        UsuarioActual usuario = null;
        if (!rutaElegida.publica()) {
            usuario = autenticacion.autenticar(cabeceras.getFirst("Authorization"))
                    .orElseThrow(ErrorHttp::noAutenticado);
            if (!rutaElegida.permisosAlgunoDe().isEmpty()
                    && !autenticacion.tiene(usuario, rutaElegida.permisosAlgunoDe())) {
                throw ErrorHttp.sinPermiso();
            }
        }

        Map<String, List<String>> consulta = Solicitud.leerConsulta(intercambio.getRequestURI().getRawQuery());
        intercambio.setAttribute(ATRIBUTO_CUERPO_LEIDO, Boolean.TRUE);
        byte[] cuerpo;
        try {
            cuerpo = conPlazo(() -> leerCuerpo(intercambio.getRequestBody()));
        } catch (IOException e) {
            // Troceado mal formado, conexión cortada a mitad del cuerpo...: es una petición ilegible, no un fallo
            // del servidor. Si el cliente sigue ahí recibe el 400; si no, la escritura de abajo falla sin ruido.
            LOG.log(Level.FINE, "Cuerpo ilegible: {0}", e.toString());
            throw ErrorHttp.peticionInvalida();
        }
        Solicitud solicitud = new Solicitud(metodo, ruta, coincidencia.parametros(), consulta,
                cabeceras.getFirst("Content-Type"), cuerpo, usuario);

        Respuesta respuesta = rutaElegida.manejador().atender(solicitud);
        if (respuesta == null) {
            throw new IllegalStateException("El manejador de " + metodo + " " + rutaElegida.patron()
                    + " devolvió null.");
        }
        return deRespuesta(respuesta);
    }

    private static Salida deRespuesta(Respuesta respuesta) {
        if (respuesta.cuerpo() == null) {
            return new Salida(respuesta.estado(), null, new byte[0]);
        }
        if (respuesta.cuerpo() instanceof Respuesta.Contenido contenido) {
            return new Salida(respuesta.estado(), contenido.tipo(), contenido.bytes());
        }
        // Se serializa antes de enviar nada: si falla, todavía se puede responder con un 500.
        byte[] bytes = Json.escribir(respuesta.cuerpo()).getBytes(StandardCharsets.UTF_8);
        String tipo = respuesta.cuerpo() instanceof ManejadorErrores.Problema ? TIPO_PROBLEMA : TIPO_JSON;
        return new Salida(respuesta.estado(), tipo, bytes);
    }

    private static Salida deError(HttpExchange intercambio, Throwable t, String ruta) {
        if (t instanceof ErrorHttp http) {
            http.cabeceras().forEach(intercambio.getResponseHeaders()::set);
        }
        return deRespuesta(ManejadorErrores.aRespuesta(t, ruta));
    }

    /** Ruta como llegó, sin consulta y sin decodificar. */
    private static String rutaCruda(HttpExchange intercambio) {
        String ruta = intercambio.getRequestURI().getRawPath();
        return ruta == null ? "" : ruta;
    }

    /**
     * Lee el cuerpo sin guardar más de {@link #LIMITE_CUERPO} bytes.
     *
     * @throws ErrorHttp 413 si lo supera
     */
    private static byte[] leerCuerpo(InputStream entrada) throws IOException {
        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
        byte[] bloque = new byte[8192];
        long total = 0;
        int leidos;
        while ((leidos = entrada.read(bloque)) >= 0) {
            total += leidos;
            if (total > LIMITE_CUERPO + LIMITE_DESCARTE) {
                break;
            }
            if (cuerpo.size() < LIMITE_CUERPO) {
                cuerpo.write(bloque, 0, Math.min(leidos, LIMITE_CUERPO - cuerpo.size()));
            }
        }
        if (total > LIMITE_CUERPO) {
            throw ErrorHttp.cuerpoDemasiadoGrande();
        }
        return cuerpo.toByteArray();
    }

    /**
     * Consume (hasta {@link #LIMITE_DESCARTE}) el cuerpo que no se va a usar, p. ej. el de una petición sin sesión:
     * si se responde con el cuerpo a medio enviar, el servidor del JDK cierra la conexión y el cliente recibe un
     * error de red en vez del 401.
     */
    private void descartarCuerpo(InputStream entrada) {
        byte[] bloque = new byte[8192];
        try {
            conPlazo(() -> {
                long total = 0;
                int leidos;
                while (total <= LIMITE_DESCARTE && (leidos = entrada.read(bloque)) >= 0) {
                    total += leidos;
                }
                return null;
            });
        } catch (IOException e) {
            LOG.log(Level.FINE, "No se pudo descartar el cuerpo: {0}", e.toString());
        }
    }

    /**
     * Ejecuta una lectura de la conexión con el plazo {@link #plazoLectura}. Si vence, interrumpe el hilo: una lectura
     * bloqueante interrumpida cierra el canal y falla, con lo que el hilo y el socket quedan libres aunque el cliente
     * siga callado.
     *
     * @throws ConexionAbandonada si venció el plazo (la conexión ya está cerrada)
     */
    private <T> T conPlazo(Lectura<T> lectura) throws IOException {
        Thread hilo = Thread.currentThread();
        AtomicBoolean vencido = new AtomicBoolean();
        ScheduledFuture<?> alarma = vigilante.schedule(() -> {
            vencido.set(true);
            hilo.interrupt();
        }, plazoLectura.toNanos(), TimeUnit.NANOSECONDS);
        try {
            return lectura.leer();
        } catch (IOException e) {
            if (vencido.get()) {
                throw new ConexionAbandonada();
            }
            throw e;
        } finally {
            if (!alarma.cancel(false)) {
                // Ya corrió o está corriendo: esperar a que termine su interrupt() para limpiarlo después.
                try {
                    alarma.get();
                } catch (InterruptedException | ExecutionException e) {
                    // La interrupción de la alarma; se limpia abajo.
                }
            }
            Thread.interrupted();
        }
    }

    private static void enviar(HttpExchange intercambio, Salida salida) throws IOException {
        Headers cabeceras = intercambio.getResponseHeaders();
        cabeceras.set("X-Content-Type-Options", "nosniff");
        cabeceras.set("Cache-Control", "no-store");
        int largo = salida.cuerpo().length;
        int estado = salida.estado();
        if (estado == 204 || estado == 304 || estado < 200) {
            intercambio.sendResponseHeaders(estado, -1);
            return;
        }
        if (largo > 0 && salida.tipo() != null) {
            cabeceras.set("Content-Type", salida.tipo());
        }
        if (largo == 0 || intercambio.getRequestMethod().equals("HEAD")) {
            // -1 = sin cuerpo (0 significaría «troceado»); en HEAD se anuncia el largo que tendría el GET.
            if (intercambio.getRequestMethod().equals("HEAD")) {
                cabeceras.set("Content-Length", Integer.toString(largo));
            }
            intercambio.sendResponseHeaders(estado, -1);
            return;
        }
        intercambio.sendResponseHeaders(estado, largo);
        try (OutputStream salidaCuerpo = intercambio.getResponseBody()) {
            salidaCuerpo.write(salida.cuerpo());
        }
    }

    // ------------------------------------------------------------------ CORS

    /**
     * Aplica CORS como lo hacía Spring. Devuelve la respuesta si la petición termina aquí (preflight o rechazo), o
     * {@code null} para seguir; si el origen es permitido deja ya puestas las cabeceras que toda respuesta lleva.
     */
    private Salida aplicarCors(HttpExchange intercambio, String metodo, Headers peticion) {
        String origen = peticion.getFirst("Origin");
        if (origen == null || esMismoOrigen(origen, peticion.getFirst("Host"))) {
            return null;
        }
        Headers respuesta = intercambio.getResponseHeaders();
        boolean comodin = origenes.contains("*");
        if (!comodin && origenes.stream().noneMatch(permitido -> permitido.equalsIgnoreCase(sinBarraFinal(origen)))) {
            return rechazoCors();
        }
        respuesta.set("Access-Control-Allow-Origin", comodin ? "*" : origen);
        respuesta.add("Vary", "Origin");

        String metodoSolicitado = peticion.getFirst("Access-Control-Request-Method");
        if (!metodo.equals("OPTIONS") || metodoSolicitado == null) {
            return null;
        }
        // Preflight: se responde sin enrutar ni pedir sesión.
        if (!METODOS_CORS.contains(metodoSolicitado.strip().toUpperCase(Locale.ROOT))
                || !cabecerasPermitidas(peticion.getFirst("Access-Control-Request-Headers"))) {
            respuesta.remove("Access-Control-Allow-Origin");
            respuesta.remove("Vary");
            return rechazoCors();
        }
        respuesta.add("Vary", "Access-Control-Request-Method");
        respuesta.add("Vary", "Access-Control-Request-Headers");
        respuesta.set("Access-Control-Allow-Methods", String.join(",", METODOS_CORS));
        respuesta.set("Access-Control-Allow-Headers", String.join(",", CABECERAS_CORS));
        respuesta.set("Access-Control-Max-Age", "3600");
        return new Salida(200, null, new byte[0]);
    }

    private static boolean cabecerasPermitidas(String solicitadas) {
        if (solicitadas == null) {
            return true;
        }
        for (String cabecera : solicitadas.split(",")) {
            String nombre = cabecera.strip();
            if (!nombre.isEmpty() && CABECERAS_CORS.stream().noneMatch(nombre::equalsIgnoreCase)) {
                return false;
            }
        }
        return true;
    }

    private static Salida rechazoCors() {
        return new Salida(403, TIPO_TEXTO, "Invalid CORS request".getBytes(StandardCharsets.UTF_8));
    }

    /** Una petición del propio sitio (p. ej. la interfaz de Swagger) trae {@code Origin} pero no es CORS. */
    private static boolean esMismoOrigen(String origen, String host) {
        return host != null && sinPuertoPorDefecto(origen.strip().toLowerCase(Locale.ROOT))
                .equals(sinPuertoPorDefecto("http://" + host.strip().toLowerCase(Locale.ROOT)));
    }

    private static String sinPuertoPorDefecto(String origen) {
        return origen.endsWith(":80") ? origen.substring(0, origen.length() - 3) : origen;
    }

    private static String sinBarraFinal(String origen) {
        String limpio = origen.strip();
        return limpio.endsWith("/") ? limpio.substring(0, limpio.length() - 1) : limpio;
    }
}
