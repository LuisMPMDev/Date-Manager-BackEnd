package com.datemanager.dominio;

/** Estado de un enlace de invitación; no se guarda, se deriva con {@link Enlaces#estado}. */
public enum EstadoEnlace {
    Activo,
    Vencido,
    Agotado,
    Desactivado
}
