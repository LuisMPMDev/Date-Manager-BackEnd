package com.datemanager.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.datemanager.aplicacion.InicioServicio;
import com.datemanager.infraestructura.http.DocumentoRuta;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.http.Solicitud;

/** Datos del inicio del panel. Sin reglas: delega en {@link InicioServicio}. */
public final class InicioControlador {

    private final InicioServicio inicio;

    public InicioControlador(InicioServicio inicio) {
        this.inicio = Objects.requireNonNull(inicio);
    }

    public List<Ruta> rutas() {
        return List.of(new Ruta("GET", "/api/panel/inicio", Set.of("ver_panel"), false, this::obtener,
                new DocumentoRuta("Inicio", "Cifras del inicio del panel (filtro fecha AAAA-MM-DD)", null,
                        InicioDto.class, false, 200)));
    }

    /** {@code fecha} ({@code AAAA-MM-DD}) es el «hoy» de las cifras por día; sin ella, hoy en la zona del negocio. */
    Respuesta obtener(Solicitud solicitud) {
        String fecha = solicitud.consulta("fecha").orElse(null);
        return Respuesta.ok(InicioDto.de(inicio.obtener(fecha == null ? null : FormatoFechas.fecha("fecha", fecha))));
    }
}
