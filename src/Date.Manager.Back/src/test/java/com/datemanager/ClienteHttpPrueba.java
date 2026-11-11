package com.datemanager;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.datemanager.infraestructura.Configuracion;
import com.datemanager.infraestructura.http.Enrutador;
import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.ServidorHttp;
import com.datemanager.infraestructura.seguridad.Autenticacion;

/** Servidor real en un puerto efímero y un {@link HttpClient} del JDK contra él; se cierra con {@link #close()}. */
public final class ClienteHttpPrueba implements AutoCloseable {

    /** Lo que respondió el servidor. */
    public record Resultado(int estado, byte[] bytes, HttpHeaders cabeceras) {

        public String cuerpo() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        /** El cuerpo como objeto JSON. */
        @SuppressWarnings("unchecked")
        public Map<String, Object> json() {
            return (Map<String, Object>) Json.parse(cuerpo());
        }

        /** Primer valor de la cabecera, o {@code null} si no vino. */
        public String cabecera(String nombre) {
            return cabeceras.firstValue(nombre).orElse(null);
        }
    }

    /** Configuración mínima con puerto efímero (0) y los orígenes CORS dados. */
    public static Configuracion configuracion(List<String> origenes) {
        return new Configuracion("jdbc:sqlserver://no-se-usa", "usuario", "clave", 1,
                "secreto-de-prueba-con-mas-de-32-bytes!!", ZoneId.of("America/Bogota"), origenes, "", "", false, 0);
    }

    private final ServidorHttp servidor;
    private final HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final String base;

    private ClienteHttpPrueba(ServidorHttp servidor) {
        this.servidor = servidor;
        this.base = "http://127.0.0.1:" + servidor.puerto();
    }

    public static ClienteHttpPrueba iniciar(Enrutador rutas, Autenticacion autenticacion, String... origenes) {
        return iniciarConPlazo(ServidorHttp.PLAZO_LECTURA, rutas, autenticacion, origenes);
    }

    /** Como {@link #iniciar} pero con otro plazo para recibir el cuerpo de las peticiones. */
    public static ClienteHttpPrueba iniciarConPlazo(Duration plazoLectura, Enrutador rutas,
            Autenticacion autenticacion, String... origenes) {
        ServidorHttp servidor = new ServidorHttp(configuracion(Arrays.asList(origenes)), rutas, autenticacion,
                plazoLectura);
        servidor.iniciar();
        return new ClienteHttpPrueba(servidor);
    }

    public int puerto() {
        return servidor.puerto();
    }

    /** Un socket conectado al servidor, para enviar bytes a mano (el que lo abre lo cierra). */
    public Socket abrirSocket() {
        try {
            Socket socket = new Socket("127.0.0.1", servidor.puerto());
            socket.setSoTimeout(10_000);
            return socket;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Envía los bytes tal cual por un socket y devuelve todo lo que el servidor responda hasta que cierre. Con
     * {@code semicerrar}, cierra antes el sentido de escritura (el cliente "se calla" pero sigue escuchando).
     */
    public String enviarCrudo(String peticion, boolean semicerrar) {
        try (Socket socket = abrirSocket()) {
            socket.getOutputStream().write(peticion.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            if (semicerrar) {
                socket.shutdownOutput();
            }
            return leerRespuesta(socket.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("La petición cruda falló: " + e, e);
        }
    }

    /** Lee una respuesta HTTP/1.1: hasta que cierren o hasta completar el {@code Content-Length} anunciado. */
    private static String leerRespuesta(InputStream entrada) throws IOException {
        ByteArrayOutputStream recibido = new ByteArrayOutputStream();
        int leido;
        while ((leido = entrada.read()) >= 0) {
            recibido.write(leido);
            String texto = recibido.toString(StandardCharsets.ISO_8859_1);
            int finCabeceras = texto.indexOf("\r\n\r\n");
            if (finCabeceras < 0) {
                continue;
            }
            Matcher largo = Pattern
                    .compile("(?im)^content-length:\\s*(\\d+)").matcher(texto.substring(0, finCabeceras));
            if (largo.find() && recibido.size() >= finCabeceras + 4 + Integer.parseInt(largo.group(1))) {
                break;
            }
        }
        return recibido.toString(StandardCharsets.UTF_8);
    }

    /** {@code http://127.0.0.1:puerto}, que es también el origen de las peticiones del mismo sitio. */
    public String base() {
        return base;
    }

    /** @param cabeceras pares nombre, valor */
    public Resultado get(String ruta, String... cabeceras) {
        return enviar("GET", ruta, (byte[]) null, cabeceras);
    }

    public Resultado enviar(String metodo, String ruta, String cuerpo, String... cabeceras) {
        return enviar(metodo, ruta, cuerpo == null ? null : cuerpo.getBytes(StandardCharsets.UTF_8), cabeceras);
    }

    /** {@code ruta} se envía tal cual (con su consulta y sus %XX). */
    public Resultado enviar(String metodo, String ruta, byte[] cuerpo, String... cabeceras) {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(base + ruta))
                .timeout(Duration.ofSeconds(20))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(cuerpo));
        for (int i = 0; i + 1 < cabeceras.length; i += 2) {
            peticion.header(cabeceras[i], cabeceras[i + 1]);
        }
        try {
            HttpResponse<byte[]> respuesta = cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Resultado(respuesta.statusCode(), respuesta.body(), respuesta.headers());
        } catch (IOException e) {
            throw new IllegalStateException("La petición " + metodo + " " + ruta + " falló: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Envía por un socket una petición GET con el destino tal cual (aunque no sea un URI válido, que HttpClient no
     * deja enviar) y devuelve solo el estado de la respuesta.
     */
    public int estadoDeDestinoCrudo(String destino) {
        try (Socket socket = new Socket("127.0.0.1", servidor.puerto())) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(("GET " + destino + " HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String linea = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.ISO_8859_1)).readLine();
            return Integer.parseInt(linea.split(" ")[1]);
        } catch (IOException e) {
            throw new IllegalStateException("La petición cruda " + destino + " falló: " + e, e);
        }
    }

    @Override
    public void close() {
        servidor.detener();
    }
}
