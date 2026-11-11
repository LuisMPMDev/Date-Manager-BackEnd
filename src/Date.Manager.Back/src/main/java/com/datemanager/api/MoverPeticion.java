package com.datemanager.api;

/** Cuerpo del {@code POST /api/panel/citas/{id}/mover}: nueva {@code fecha} {@code YYYY-MM-DD} e {@code inicio} {@code HH:mm}. */
public record MoverPeticion(String fecha, String inicio) {

    void validar() {
        new Validacion()
                .noNulo("fecha", fecha, "Elige la fecha.")
                .noNulo("inicio", inicio, "Elige la hora.")
                .lanzarSiHay();
    }
}
