package com.datemanager.api;

import java.util.UUID;

import com.datemanager.aplicacion.EnlacesServicio.EnlaceVista;
import com.datemanager.dominio.Enlace;

/**
 * Enlace en el panel. {@code ruta} es la ruta lógica del futuro Angular ({@code /agendar/<codigo>}); el sitio base
 * lo pone el cliente. Instantes en ISO UTC; {@code maxUsos} se omite si no hay límite.
 */
public record EnlaceDto(UUID id, String codigo, String ruta, String estado, int usos, Integer maxUsos,
        String venceEn, String creadoEn, UUID creadoPor, String creadoPorNombre) {

    public static EnlaceDto de(EnlaceVista vista) {
        Enlace e = vista.enlace();
        return new EnlaceDto(e.id(), e.codigo(), "/agendar/" + e.codigo(), vista.estado().name(), e.usos(),
                e.maxUsos(), e.venceEn().toString(), e.creadoEn().toString(), e.creadoPor(),
                vista.creadoPorNombre());
    }
}
