package com.datemanager.api;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.VentanaDisponibilidad;

/** Servicio en el JSON de la demo: horas {@code HH:mm} y {@code valor} como número. */
public record ServicioDto(UUID id, String nombre, int duracionMin, boolean activo, BigDecimal valor,
        List<VentanaDto> ventanas) {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    public record VentanaDto(UUID id, int dia, String inicio, String fin) {

        static VentanaDto de(VentanaDisponibilidad v) {
            return new VentanaDto(v.id(), v.dia(), v.inicio().format(HHMM), v.fin().format(HHMM));
        }
    }

    public static ServicioDto de(Servicio s) {
        return new ServicioDto(s.id(), s.nombre(), s.duracionMin(), s.activo(), s.valor(),
                s.ventanas().stream().map(VentanaDto::de).toList());
    }
}
