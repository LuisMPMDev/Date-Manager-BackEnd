package com.datemanager.api;

import jakarta.validation.constraints.NotNull;

/** Cuerpo del {@code POST /api/panel/citas/{id}/mover}: nueva {@code fecha} {@code YYYY-MM-DD} e {@code inicio} {@code HH:mm}. */
public record MoverPeticion(
        @NotNull(message = "Elige la fecha.") String fecha,
        @NotNull(message = "Elige la hora.") String inicio) {
}
