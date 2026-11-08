package com.datemanager.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Convierte toda excepción en el JSON de error {@code {codigo, mensaje, campos?}} (problem details).
 * Las excepciones propias de Spring MVC conservan su estado HTTP; cualquier otra es un 500 sin detalles.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);
    private static final String MENSAJE_INESPERADO = "Ocurrió un error inesperado. Inténtalo de nuevo más tarde.";

    /** Estado HTTP de cada código; el switch exhaustivo obliga a mapear los códigos nuevos. */
    public static HttpStatus estadoHttp(CodigoError codigo) {
        return switch (codigo) {
            case VALIDACION -> HttpStatus.BAD_REQUEST;
            case NO_AUTENTICADO, CREDENCIALES_INVALIDAS -> HttpStatus.UNAUTHORIZED;
            case SIN_PERMISO -> HttpStatus.FORBIDDEN;
            case NO_ENCONTRADO, ENLACE_NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case HORARIO_NO_DISPONIBLE, ESTADO_INVALIDO, CITA_NO_HA_INICIADO, CONFLICTO -> HttpStatus.CONFLICT;
            case ENLACE_VENCIDO, ENLACE_AGOTADO, ENLACE_DESACTIVADO -> HttpStatus.GONE;
            case ERROR_INTERNO -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @ExceptionHandler(ErrorNegocio.class)
    ResponseEntity<Object> errorNegocio(ErrorNegocio e) {
        return respuesta(e.codigo(), e.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> inesperado(Exception e) {
        LOG.error("Error inesperado", e);
        return respuesta(CodigoError.ERROR_INTERNO, MENSAJE_INESPERADO, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return respuesta(CodigoError.VALIDACION, "Hay datos inválidos en la petición.", campos);
    }

    /** Resto de errores de Spring MVC (JSON ilegible, ruta o método inexistente...): mismo formato. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> base = super.handleExceptionInternal(e, body, headers, status, request);
        if (base != null && base.getBody() instanceof ProblemDetail problema) {
            CodigoError codigo = status.value() == 404 ? CodigoError.NO_ENCONTRADO
                    : status.is5xxServerError() ? CodigoError.ERROR_INTERNO : CodigoError.VALIDACION;
            String mensaje = mensajeGenerico(status);
            problema.setDetail(mensaje);
            problema.setProperty("codigo", codigo.name());
            problema.setProperty("mensaje", mensaje);
        }
        return base;
    }

    private static String mensajeGenerico(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "No se encontró el recurso solicitado.";
            case 405 -> "Método no permitido para este recurso.";
            case 415 -> "Tipo de contenido no soportado.";
            case 500 -> MENSAJE_INESPERADO;
            default -> "La petición no es válida.";
        };
    }

    private static ResponseEntity<Object> respuesta(CodigoError codigo, String mensaje, Map<String, String> campos) {
        HttpStatus estado = estadoHttp(codigo);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, mensaje);
        problema.setProperty("codigo", codigo.name());
        problema.setProperty("mensaje", mensaje);
        if (campos != null) {
            problema.setProperty("campos", campos);
        }
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }
}
