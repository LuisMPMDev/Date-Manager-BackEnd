package com.datemanager.infraestructura.openapi;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.datemanager.infraestructura.http.DocumentoRuta;
import com.datemanager.infraestructura.http.Ruta;

/**
 * Genera el contrato OpenAPI 3.0.3 a partir de la tabla de rutas (lo que antes hacía springdoc leyendo los
 * controladores). Declara el esquema de seguridad Bearer JWT y lo exige de forma global, para que Swagger UI muestre el
 * botón <em>Authorize</em> y pueda llamar a las rutas del panel con el token de {@code POST /api/auth/login}; las
 * rutas públicas lo anulan con {@code security: []}. El requisito es solo una indicación para la interfaz: la
 * autorización real la hace el servidor.
 *
 * <p>Los cuerpos y respuestas se describen por reflexión de los {@code record} de {@link DocumentoRuta}, como
 * {@code $ref} a {@code components.schemas}; cada operación lleva en {@code x-permisos} los permisos que exige
 * (basta con uno).
 */
public final class GeneradorOpenApi {

    public static final String ESQUEMA_BEARER = "bearerAuth";

    private GeneradorOpenApi() {
    }

    /** El documento OpenAPI de esas rutas, listo para {@code Json.escribir}. */
    public static Map<String, Object> generar(List<Ruta> rutas) {
        Esquemas esquemas = new Esquemas();
        Map<String, Object> paths = new LinkedHashMap<>();
        for (Ruta ruta : rutas) {
            @SuppressWarnings("unchecked")
            Map<String, Object> operaciones = (Map<String, Object>) paths.computeIfAbsent(ruta.patron(),
                    p -> new LinkedHashMap<String, Object>());
            operaciones.put(ruta.metodo().toLowerCase(Locale.ROOT), operacion(ruta, esquemas));
        }

        Map<String, Object> bearer = new LinkedHashMap<>();
        bearer.put("type", "http");
        bearer.put("scheme", "bearer");
        bearer.put("bearerFormat", "JWT");
        bearer.put("description", "Token de POST /api/auth/login (sin el prefijo «Bearer»).");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("securitySchemes", Map.of(ESQUEMA_BEARER, bearer));
        components.put("schemas", esquemas.definiciones);

        Map<String, Object> documento = new LinkedHashMap<>();
        documento.put("openapi", "3.0.3");
        documento.put("info", mapa("title", "DateManager API", "version", "1"));
        documento.put("security", List.of(Map.of(ESQUEMA_BEARER, List.of())));
        documento.put("tags", etiquetas(rutas));
        documento.put("paths", paths);
        documento.put("components", components);
        return documento;
    }

    private static Map<String, Object> operacion(Ruta ruta, Esquemas esquemas) {
        DocumentoRuta doc = ruta.doc();
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("tags", List.of(etiqueta(ruta)));
        op.put("summary", doc != null && doc.resumen() != null ? doc.resumen() : ruta.metodo() + " " + ruta.patron());
        op.put("operationId", ruta.metodo().toLowerCase(Locale.ROOT) + idDe(ruta.patron()));

        List<Object> parametros = new ArrayList<>();
        for (String segmento : ruta.patron().split("/")) {
            if (segmento.length() >= 2 && segmento.startsWith("{") && segmento.endsWith("}")) {
                String nombre = segmento.substring(1, segmento.length() - 1);
                Map<String, Object> esquema = nombre.equals("id") || nombre.endsWith("Id")
                        ? mapa("type", "string", "format", "uuid")
                        : mapa("type", "string");
                Map<String, Object> parametro = new LinkedHashMap<>();
                parametro.put("name", nombre);
                parametro.put("in", "path");
                parametro.put("required", true);
                parametro.put("schema", esquema);
                parametros.add(parametro);
            }
        }
        if (!parametros.isEmpty()) {
            op.put("parameters", parametros);
        }

        if (doc != null && doc.cuerpo() != null) {
            op.put("requestBody", mapa("required", true,
                    "content", Map.of("application/json", Map.of("schema", esquemas.de(doc.cuerpo())))));
        }

        Map<String, Object> respuestas = new LinkedHashMap<>();
        int estado = doc != null && doc.estadoOk() > 0 ? doc.estadoOk() : 200;
        Map<String, Object> exito = new LinkedHashMap<>();
        exito.put("description", estado == 201 ? "Creado" : estado == 204 ? "Sin contenido" : "OK");
        if (doc != null && doc.respuesta() != null && estado != 204) {
            Map<String, Object> esquema = esquemas.de(doc.respuesta());
            if (doc.lista()) {
                esquema = mapa("type", "array", "items", esquema);
            }
            exito.put("content", Map.of("application/json", Map.of("schema", esquema)));
        }
        respuestas.put(Integer.toString(estado), exito);
        if (!ruta.publica()) {
            respuestas.put("401", mapa("description", "Sin sesión o token inválido"));
            if (!ruta.permisosAlgunoDe().isEmpty()) {
                respuestas.put("403", mapa("description", "Sin permiso"));
            }
        }
        op.put("responses", respuestas);

        if (ruta.publica()) {
            op.put("security", List.of());
        } else {
            op.put("x-permisos", ruta.permisosAlgunoDe().stream().sorted().toList());
        }
        return op;
    }

    /** La etiqueta del documento o, si no tiene, la del grupo por prefijo ({@code /api/panel/citas} da «Citas»). */
    static String etiqueta(Ruta ruta) {
        if (ruta.doc() != null && ruta.doc().etiqueta() != null && !ruta.doc().etiqueta().isBlank()) {
            return ruta.doc().etiqueta();
        }
        List<String> segmentos = new ArrayList<>(Arrays.asList(ruta.patron().substring(1).split("/")));
        if (!segmentos.isEmpty() && segmentos.get(0).equals("api")) {
            segmentos.remove(0);
        }
        if (segmentos.size() > 1 && segmentos.get(0).equals("panel")) {
            segmentos.remove(0);
        }
        String grupo = segmentos.isEmpty() || segmentos.get(0).isEmpty() ? "General" : segmentos.get(0);
        return Character.toUpperCase(grupo.charAt(0)) + grupo.substring(1);
    }

    private static List<Object> etiquetas(List<Ruta> rutas) {
        return rutas.stream().map(GeneradorOpenApi::etiqueta).distinct()
                .map(nombre -> (Object) Map.of("name", nombre)).toList();
    }

    /** {@code /api/panel/citas/{id}/mover} da {@code ApiPanelCitasIdMover}. */
    private static String idDe(String patron) {
        StringBuilder id = new StringBuilder();
        for (String parte : patron.split("[^A-Za-z0-9]+")) {
            if (!parte.isEmpty()) {
                id.append(Character.toUpperCase(parte.charAt(0))).append(parte.substring(1));
            }
        }
        return id.toString();
    }

    private static Map<String, Object> mapa(Object... claveValor) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            mapa.put((String) claveValor[i], claveValor[i + 1]);
        }
        return mapa;
    }

    /** Los esquemas de {@code components.schemas}, generados una sola vez por clase. */
    private static final class Esquemas {

        final Map<String, Object> definiciones = new TreeMap<>();
        private final Map<Class<?>, String> nombres = new HashMap<>();

        /** El esquema de un tipo: {@code $ref} si es un record; en línea si no. */
        Map<String, Object> de(Type tipo) {
            if (tipo instanceof ParameterizedType parametrizado
                    && parametrizado.getRawType() instanceof Class<?> crudo) {
                Type[] argumentos = parametrizado.getActualTypeArguments();
                if (Collection.class.isAssignableFrom(crudo)) {
                    return mapa("type", "array", "items", de(argumentos.length == 1 ? argumentos[0] : Object.class));
                }
                if (Map.class.isAssignableFrom(crudo)) {
                    return mapa("type", "object", "additionalProperties",
                            de(argumentos.length == 2 ? argumentos[1] : Object.class));
                }
                return de(crudo);
            }
            if (!(tipo instanceof Class<?> clase)) {
                return mapa();
            }
            if (clase == String.class || clase == char.class || clase == Character.class) {
                return mapa("type", "string");
            }
            if (clase == UUID.class) {
                return mapa("type", "string", "format", "uuid");
            }
            if (clase == LocalDate.class) {
                return mapa("type", "string", "format", "date");
            }
            if (clase == Instant.class) {
                return mapa("type", "string", "format", "date-time");
            }
            if (clase == LocalTime.class) {
                return mapa("type", "string");
            }
            if (clase == int.class || clase == Integer.class || clase == short.class || clase == Short.class
                    || clase == byte.class || clase == Byte.class) {
                return mapa("type", "integer", "format", "int32");
            }
            if (clase == long.class || clase == Long.class || clase == BigInteger.class) {
                return mapa("type", "integer", "format", "int64");
            }
            if (clase == boolean.class || clase == Boolean.class) {
                return mapa("type", "boolean");
            }
            if (clase == double.class || clase == Double.class) {
                return mapa("type", "number", "format", "double");
            }
            if (clase == float.class || clase == Float.class) {
                return mapa("type", "number", "format", "float");
            }
            if (clase == BigDecimal.class || Number.class.isAssignableFrom(clase)) {
                return mapa("type", "number");
            }
            if (clase.isEnum()) {
                return mapa("type", "string", "enum",
                        Arrays.stream(clase.getEnumConstants()).map(c -> ((Enum<?>) c).name()).toList());
            }
            if (clase.isArray()) {
                return mapa("type", "array", "items", de(clase.getComponentType()));
            }
            if (Collection.class.isAssignableFrom(clase)) {
                return mapa("type", "array", "items", mapa());
            }
            if (Map.class.isAssignableFrom(clase)) {
                return mapa("type", "object");
            }
            if (clase.isRecord()) {
                return mapa("$ref", "#/components/schemas/" + registrar(clase));
            }
            return mapa();
        }

        private String registrar(Class<?> registro) {
            String nombre = nombres.get(registro);
            if (nombre != null) {
                return nombre;
            }
            nombre = registro.getSimpleName();
            if (nombres.containsValue(nombre) && registro.getEnclosingClass() != null) {
                nombre = registro.getEnclosingClass().getSimpleName() + "_" + nombre;
            }
            String base = nombre;
            for (int n = 2; nombres.containsValue(nombre); n++) {
                nombre = base + n;
            }
            nombres.put(registro, nombre);
            Map<String, Object> propiedades = new LinkedHashMap<>();
            Map<String, Object> esquema = mapa("type", "object", "properties", propiedades);
            definiciones.put(nombre, esquema); // antes de recorrer: un record recursivo no da vueltas sin fin
            for (RecordComponent componente : registro.getRecordComponents()) {
                propiedades.put(componente.getName(), de(componente.getGenericType()));
            }
            return nombre;
        }
    }
}
