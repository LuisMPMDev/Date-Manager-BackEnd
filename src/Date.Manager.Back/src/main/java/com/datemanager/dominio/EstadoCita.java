package com.datemanager.dominio;

/** Estados de una cita; nace {@code Confirmada}. Los nombres viajan tal cual en el JSON. */
public enum EstadoCita {
    Confirmada,
    Atendida,
    Cancelada
}
