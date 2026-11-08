package com.datemanager.dominio;

/** Códigos de error de negocio que viajan al cliente en el campo {@code codigo}. */
public enum CodigoError {
    VALIDACION,
    NO_AUTENTICADO,
    CREDENCIALES_INVALIDAS,
    SIN_PERMISO,
    NO_ENCONTRADO,
    ENLACE_NO_ENCONTRADO,
    HORARIO_NO_DISPONIBLE,
    ESTADO_INVALIDO,
    CITA_NO_HA_INICIADO,
    CONFLICTO,
    ENLACE_VENCIDO,
    ENLACE_AGOTADO,
    ENLACE_DESACTIVADO,
    /** Fallo inesperado del servidor (no lo lanza el negocio; lo produce el manejador de errores). */
    ERROR_INTERNO
}
