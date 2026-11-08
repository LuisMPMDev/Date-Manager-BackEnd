package com.datemanager.api;

import java.util.List;
import java.util.UUID;

import com.datemanager.aplicacion.ServiciosServicio;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Servicios y ventanas de disponibilidad del panel. Sin reglas: delega en {@link ServiciosServicio}. */
@RestController
@RequestMapping("/api/panel/servicios")
public class ServiciosControlador {

    private final ServiciosServicio servicios;

    public ServiciosControlador(ServiciosServicio servicios) {
        this.servicios = servicios;
    }

    @GetMapping
    @PreAuthorize("@permisos.tiene('ver_panel', 'ver_citas', 'gestionar_servicios')")
    public List<ServicioDto> listar() {
        return servicios.listar().stream().map(ServicioDto::de).toList();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permisos.tiene('gestionar_servicios')")
    public ServicioDto guardar(@PathVariable UUID id, @Valid @RequestBody ServicioPeticion peticion) {
        return ServicioDto.de(servicios.guardar(peticion.aDominio(id)));
    }
}
