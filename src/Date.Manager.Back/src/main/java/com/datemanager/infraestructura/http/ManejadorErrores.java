package com.datemanager.infraestructura.http;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.infraestructura.persistencia.ErroresBase;

/**
 * Convierte toda excepción en el JSON de error de la API: un problem details (RFC 9457) con las propiedades
 * {@code codigo}, {@code mensaje} y, si hay, {@code campos}. Cualquier excepción que no sea del negocio ni del
 * protocolo es un 500 sin detalles, salvo los errores de la base que son un conflicto (restricciones, bloqueos y
 * disparadores: 409).
 */
public final class ManejadorErrores {

    private static final Logger LOG = Logger.getLogger(ManejadorErrores.class.getName());

    public static final String MENSAJE_INESPERADO = "Ocurrió un error inesperado. Inténtalo de nuevo más tarde.";
    public static final String MENSAJE_NO_AUTENTICADO = "Inicia sesión para continuar.";
    public static final String MENSAJE_SIN_PERMISO = "No tienes permiso para realizar esta acción.";
    public static final String MENSAJE_DATOS_INVALIDOS = "Hay datos inválidos en la petición.";
    public static final String MENSAJE_PETICION_INVALIDA = "La petición no es válida.";
    public static final String MENSAJE_CONFLICTO_DE_DATOS =
            "No se pudo guardar porque entra en conflicto con datos que ya existen.";
    public static final String MENSAJE_OPERACION_EN_CURSO = "Hay otra operación en curso; inténtalo de nuevo.";
    static final String MENSAJE_NO_ENCONTRADO = "No se encontró el recurso solicitado.";
    static final String MENSAJE_METODO_NO_PERMITIDO = "Método no permitido para este recurso.";
    static final String MENSAJE_TIPO_NO_SOPORTADO = "Tipo de contenido no soportado.";
    static final String MENSAJE_CUERPO_GRANDE = "La petición es demasiado grande.";

    private static final Map<Integer, String> FRASES = Map.ofEntries(
            Map.entry(400, "Bad Request"),
            Map.entry(401, "Unauthorized"),
            Map.entry(403, "Forbidden"),
            Map.entry(404, "Not Found"),
            Map.entry(405, "Method Not Allowed"),
            Map.entry(409, "Conflict"),
            Map.entry(410, "Gone"),
            Map.entry(413, "Content Too Large"),
            Map.entry(415, "Unsupported Media Type"),
            Map.entry(500, "Internal Server Error"));

    /**
     * Cuerpo de error: {@code type}, {@code title} (frase del estado), {@code status}, {@code detail} e
     * {@code instance} siguen el problem details de Spring; {@code codigo}, {@code mensaje} y {@code campos} son
     * los de la API. {@link Json#escribir} omite los {@code null}.
     */
    public record Problema(String type, String title, int status, String detail, String instance, String codigo,
            String mensaje, Map<String, String> campos) {
    }

    private ManejadorErrores() {
    }

    /** Estado HTTP de cada código; el switch exhaustivo obliga a mapear los códigos nuevos. */
    public static int estadoHttp(CodigoError codigo) {
        return switch (codigo) {
            case VALIDACION -> 400;
            case NO_AUTENTICADO, CREDENCIALES_INVALIDAS -> 401;
            case SIN_PERMISO -> 403;
            case NO_ENCONTRADO, ENLACE_NO_ENCONTRADO -> 404;
            case HORARIO_NO_DISPONIBLE, ESTADO_INVALIDO, CITA_NO_HA_INICIADO, CONFLICTO -> 409;
            case ENLACE_VENCIDO, ENLACE_AGOTADO, ENLACE_DESACTIVADO -> 410;
            case ERROR_INTERNO -> 500;
        };
    }

    public static Respuesta aRespuesta(Throwable e) {
        return aRespuesta(e, null);
    }

    /** {@code instancia}: la ruta de la petición, o {@code null} si no se conoce. */
    public static Respuesta aRespuesta(Throwable e, String instancia) {
        if (e instanceof ErrorNegocio negocio) {
            return respuesta(estadoHttp(negocio.codigo()), negocio.codigo(), negocio.getMessage(),
                    negocio.campos(), instancia);
        }
        if (e instanceof ErrorHttp http) {
            return respuesta(http.estado(), http.codigo(), http.getMessage(), null, instancia);
        }
        if (e instanceof JsonInvalido) {
            // Su mensaje describe la posición del fallo; nunca se devuelve, para no reflejar lo recibido.
            return respuesta(400, CodigoError.VALIDACION, MENSAJE_DATOS_INVALIDOS, null, instancia);
        }
        // Errores de la base: si una restricción, un bloqueo o un disparador saltan pese a las comprobaciones de la
        // aplicación (p. ej. dos peticiones simultáneas), es un conflicto y no un 500. La excepción puede llegar
        // envuelta en cualquier otra, así que se busca en toda la cadena de causas. El texto de la base (que puede
        // traer valores de las columnas) nunca se devuelve ni se registra: solo el nombre de la clase.
        // Primero los números de error exactos (bloqueo y disparador); la restricción es más amplia (SQLState) y va al final.
        if (ErroresBase.esFalloDeBloqueo(e)) {
            LOG.log(Level.WARNING, "Fallo de bloqueo en la base: {0}", e.getClass().getSimpleName());
            return respuesta(409, CodigoError.CONFLICTO, MENSAJE_OPERACION_EN_CURSO, null, instancia);
        }
        if (ErroresBase.esViolacionDeDisparador(e)) {
            LOG.log(Level.WARNING, "Un disparador de la base rechazó la escritura: {0}", e.getClass().getSimpleName());
            return respuesta(409, CodigoError.CONFLICTO, MENSAJE_CONFLICTO_DE_DATOS, null, instancia);
        }
        if (ErroresBase.esViolacionDeRestriccion(e)) {
            return respuesta(409, CodigoError.CONFLICTO, MENSAJE_CONFLICTO_DE_DATOS, null, instancia);
        }
        LOG.log(Level.SEVERE, "Error inesperado", e);
        return respuesta(500, CodigoError.ERROR_INTERNO, MENSAJE_INESPERADO, null, instancia);
    }

    private static Respuesta respuesta(int estado, CodigoError codigo, String mensaje, Map<String, String> campos,
            String instancia) {
        return new Respuesta(estado, new Problema("about:blank", FRASES.getOrDefault(estado, "Error"), estado,
                mensaje, instancia, codigo.name(), mensaje, campos));
    }
}
