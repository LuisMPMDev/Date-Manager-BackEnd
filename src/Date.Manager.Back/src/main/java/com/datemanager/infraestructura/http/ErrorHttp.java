package com.datemanager.infraestructura.http;

import java.util.Map;

import com.datemanager.dominio.CodigoError;

/**
 * Fallo propio del protocolo HTTP (ruta inexistente, método no permitido, cuerpo demasiado grande...) cuyo estado
 * no sale de un {@link CodigoError}. Lo traduce {@link ManejadorErrores}; {@code cabeceras} se añaden a la
 * respuesta (p. ej. {@code Allow}).
 */
final class ErrorHttp extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int estado;
    private final CodigoError codigo;
    private final transient Map<String, String> cabeceras;

    private ErrorHttp(int estado, CodigoError codigo, String mensaje, Map<String, String> cabeceras) {
        super(mensaje, null, false, false);
        this.estado = estado;
        this.codigo = codigo;
        this.cabeceras = Map.copyOf(cabeceras);
    }

    int estado() {
        return estado;
    }

    CodigoError codigo() {
        return codigo;
    }

    Map<String, String> cabeceras() {
        return cabeceras;
    }

    static ErrorHttp noEncontrado() {
        return new ErrorHttp(404, CodigoError.NO_ENCONTRADO, ManejadorErrores.MENSAJE_NO_ENCONTRADO, Map.of());
    }

    static ErrorHttp metodoNoPermitido(String allow) {
        return new ErrorHttp(405, CodigoError.VALIDACION, ManejadorErrores.MENSAJE_METODO_NO_PERMITIDO,
                Map.of("Allow", allow));
    }

    static ErrorHttp cuerpoDemasiadoGrande() {
        return new ErrorHttp(413, CodigoError.VALIDACION, ManejadorErrores.MENSAJE_CUERPO_GRANDE, Map.of());
    }

    static ErrorHttp tipoNoSoportado() {
        return new ErrorHttp(415, CodigoError.VALIDACION, ManejadorErrores.MENSAJE_TIPO_NO_SOPORTADO, Map.of());
    }

    static ErrorHttp peticionInvalida() {
        return new ErrorHttp(400, CodigoError.VALIDACION, ManejadorErrores.MENSAJE_PETICION_INVALIDA, Map.of());
    }

    static ErrorHttp noAutenticado() {
        return new ErrorHttp(401, CodigoError.NO_AUTENTICADO, ManejadorErrores.MENSAJE_NO_AUTENTICADO,
                Map.of("WWW-Authenticate", "Bearer"));
    }

    static ErrorHttp sinPermiso() {
        return new ErrorHttp(403, CodigoError.SIN_PERMISO, ManejadorErrores.MENSAJE_SIN_PERMISO, Map.of());
    }
}
