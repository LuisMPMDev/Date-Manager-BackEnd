package com.datemanager.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Fecha y horas son locales del negocio (sin zona); {@code creadaEn} y {@code canceladaEn} son instantes UTC. */
public record Cita(
        UUID id,
        UUID servicioId,
        UUID clienteId,
        LocalDate fecha,
        LocalTime inicio,
        LocalTime fin,
        EstadoCita estado,
        Instant creadaEn,
        Instant canceladaEn) {
}
