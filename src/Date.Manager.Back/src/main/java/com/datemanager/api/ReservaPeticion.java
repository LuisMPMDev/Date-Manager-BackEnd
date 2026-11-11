package com.datemanager.api;

import java.util.UUID;

/** Cuerpo del {@code POST /api/panel/citas}: {@code fecha} {@code YYYY-MM-DD} e {@code inicio} {@code HH:mm}. */
public record ReservaPeticion(UUID servicioId, String fecha, String inicio, ClientePeticion cliente) {

    /** Lo que antes hacían sus {@code @NotNull} y el {@code @Valid} del cliente. */
    void validar() {
        Validacion v = new Validacion()
                .noNulo("servicioId", servicioId, "Elige un servicio.")
                .noNulo("fecha", fecha, "Elige la fecha.")
                .noNulo("inicio", inicio, "Elige la hora.")
                .noNulo("cliente", cliente, "Escribe los datos del cliente.");
        if (cliente != null) {
            cliente.validar(v.anidada("cliente"));
        }
        v.lanzarSiHay();
    }
}
