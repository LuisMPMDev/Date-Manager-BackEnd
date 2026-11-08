package com.datemanager.api;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.VentanaDisponibilidad;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del {@code PUT /api/panel/servicios/{id}}. El id del cuerpo, si viene, se ignora: manda el de la ruta.
 * Las reglas del servicio (nombre, duración, valor, ventanas) las comprueba {@code Validaciones.errorServicio}.
 */
public record ServicioPeticion(
        @Size(max = 150, message = "El nombre es demasiado largo.") String nombre,
        Integer duracionMin,
        @NotNull(message = "Indica si el servicio está activo.") Boolean activo,
        BigDecimal valor,
        List<VentanaPeticion> ventanas) {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /** {@code id} y horas pueden faltar: sin id el servidor lo genera; sin horas, la validación lo avisa. */
    public record VentanaPeticion(UUID id, Integer dia, String inicio, String fin) {
    }

    Servicio aDominio(UUID id) {
        List<VentanaDisponibilidad> lista = ventanas == null ? List.of() : ventanas.stream().map(v -> {
            if (v == null || v.dia() == null) {
                throw invalido("Cada ventana necesita un día.");
            }
            return new VentanaDisponibilidad(v.id() == null ? UUID.randomUUID() : v.id(), v.dia(),
                    hora(v.inicio()), hora(v.fin()));
        }).toList();
        return new Servicio(id, nombre, duracionMin == null ? 0 : duracionMin, activo, valor, lista);
    }

    private static LocalTime hora(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return LocalTime.parse(texto, HHMM);
        } catch (DateTimeParseException e) {
            throw invalido("La hora «" + texto + "» no es válida: usa el formato HH:mm.");
        }
    }

    private static ErrorNegocio invalido(String mensaje) {
        return new ErrorNegocio(CodigoError.VALIDACION, mensaje);
    }
}
