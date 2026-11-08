package com.datemanager.api;

import com.datemanager.aplicacion.InicioServicio;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Datos del inicio del panel. Sin reglas: delega en {@link InicioServicio}. */
@RestController
@RequestMapping("/api/panel")
public class InicioControlador {

    private final InicioServicio inicio;

    public InicioControlador(InicioServicio inicio) {
        this.inicio = inicio;
    }

    /** {@code fecha} ({@code AAAA-MM-DD}) es el «hoy» de las cifras por día; sin ella, hoy en la zona del negocio. */
    @GetMapping("/inicio")
    @PreAuthorize("@permisos.tiene('ver_panel')")
    public InicioDto obtener(@RequestParam(required = false) String fecha) {
        return InicioDto.de(inicio.obtener(fecha == null ? null : FormatoFechas.fecha("fecha", fecha)));
    }
}
