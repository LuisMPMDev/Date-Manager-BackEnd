package com.datemanager.api;

import java.util.UUID;

import com.datemanager.dominio.Cita;

/**
 * Cita en el JSON de la demo: {@code fecha} {@code YYYY-MM-DD}, horas {@code HH:mm} sin zona, {@code creadaEn} y
 * {@code canceladaEn} en ISO UTC ({@code canceladaEn} se omite si la cita no está cancelada).
 */
public record CitaDto(UUID id, UUID servicioId, UUID clienteId, String fecha, String inicio, String fin, String estado,
        String creadaEn, String canceladaEn) {

    public static CitaDto de(Cita c) {
        return new CitaDto(c.id(), c.servicioId(), c.clienteId(), c.fecha().format(FormatoFechas.FECHA),
                c.inicio().format(FormatoFechas.HORA), c.fin().format(FormatoFechas.HORA), c.estado().name(),
                c.creadaEn().toString(), c.canceladaEn() == null ? null : c.canceladaEn().toString());
    }
}
