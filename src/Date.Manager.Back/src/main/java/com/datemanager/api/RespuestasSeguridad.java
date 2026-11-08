package com.datemanager.api;

import java.io.IOException;

import com.datemanager.dominio.CodigoError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Respuestas de la cadena de filtros de seguridad (antes de llegar a un controlador) con el mismo JSON
 * {@code {codigo, mensaje}} que {@link ManejadorErrores}: sin token válido 401 {@code NO_AUTENTICADO};
 * acceso denegado 403 {@code SIN_PERMISO}.
 */
@Component
public class RespuestasSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String MENSAJE_NO_AUTENTICADO = "Inicia sesión para continuar.";
    static final String MENSAJE_SIN_PERMISO = "No tienes permiso para realizar esta acción.";

    private final ObjectMapper json;

    public RespuestasSeguridad(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escribir(response, CodigoError.NO_AUTENTICADO, MENSAJE_NO_AUTENTICADO);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        escribir(response, CodigoError.SIN_PERMISO, MENSAJE_SIN_PERMISO);
    }

    private void escribir(HttpServletResponse response, CodigoError codigo, String mensaje) throws IOException {
        ProblemDetail problema = ManejadorErrores.problema(codigo, mensaje, null);
        response.setStatus(problema.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), problema);
    }
}
