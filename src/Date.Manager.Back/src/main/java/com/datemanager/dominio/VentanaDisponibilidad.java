package com.datemanager.dominio;

import java.time.LocalTime;
import java.util.UUID;

/** Tramo horario semanal en que un servicio se puede reservar; {@code dia} es ISO (lunes = 1 ... domingo = 7). */
public record VentanaDisponibilidad(UUID id, int dia, LocalTime inicio, LocalTime fin) {
}
