package com.datemanager.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;

/** Fechas {@code YYYY-MM-DD} y horas {@code HH:mm} del JSON, sin zona y estrictas (ni «24:00» ni «2026-02-30»). */
final class FormatoFechas {

    static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private FormatoFechas() {
    }

    static LocalDate fecha(String campo, String texto) {
        try {
            return LocalDate.parse(texto, FECHA);
        } catch (DateTimeParseException | NullPointerException e) {
            throw invalido(campo, "Escribe una fecha válida con el formato AAAA-MM-DD.");
        }
    }

    static LocalTime hora(String campo, String texto) {
        try {
            return LocalTime.parse(texto, HORA);
        } catch (DateTimeParseException | NullPointerException e) {
            throw invalido(campo, "Escribe una hora válida con el formato HH:mm.");
        }
    }

    private static ErrorNegocio invalido(String campo, String mensaje) {
        return new ErrorNegocio(CodigoError.VALIDACION, mensaje, Map.of(campo, mensaje));
    }
}
