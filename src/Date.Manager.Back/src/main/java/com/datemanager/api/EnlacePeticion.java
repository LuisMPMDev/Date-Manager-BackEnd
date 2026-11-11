package com.datemanager.api;

/**
 * Cuerpo del {@code POST /api/panel/enlaces}: {@code duracionMinutos} (60..43200, nulo = 1440) y {@code maxUsos}
 * opcional (nulo = sin límite, si no al menos 1). Los límites los valida {@code dominio.Enlaces}.
 */
public record EnlacePeticion(Integer duracionMinutos, Integer maxUsos) {
}
