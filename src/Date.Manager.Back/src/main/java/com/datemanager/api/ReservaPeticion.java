package com.datemanager.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Cuerpo del {@code POST /api/panel/citas}: {@code fecha} {@code YYYY-MM-DD} e {@code inicio} {@code HH:mm}. */
public record ReservaPeticion(
        @NotNull(message = "Elige un servicio.") UUID servicioId,
        @NotNull(message = "Elige la fecha.") String fecha,
        @NotNull(message = "Elige la hora.") String inicio,
        @NotNull(message = "Escribe los datos del cliente.") @Valid ClientePeticion cliente) {
}
