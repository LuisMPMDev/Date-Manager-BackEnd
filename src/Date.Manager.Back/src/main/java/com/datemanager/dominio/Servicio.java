package com.datemanager.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record Servicio(
        UUID id,
        String nombre,
        int duracionMin,
        boolean activo,
        BigDecimal valor,
        List<VentanaDisponibilidad> ventanas) {

    public Servicio {
        ventanas = ventanas == null ? List.of() : List.copyOf(ventanas);
    }
}
