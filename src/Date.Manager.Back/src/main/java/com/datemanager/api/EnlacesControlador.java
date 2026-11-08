package com.datemanager.api;

import java.util.List;
import java.util.UUID;

import com.datemanager.aplicacion.EnlacesServicio;
import com.datemanager.infraestructura.seguridad.UsuarioActual;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Enlaces de invitación del panel ({@code gestionar_enlaces}). Sin reglas: delega en {@link EnlacesServicio}. */
@RestController
@RequestMapping("/api/panel/enlaces")
@PreAuthorize("@permisos.tiene('gestionar_enlaces')")
public class EnlacesControlador {

    private final EnlacesServicio enlaces;

    public EnlacesControlador(EnlacesServicio enlaces) {
        this.enlaces = enlaces;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnlaceDto crear(@RequestBody(required = false) EnlacePeticion peticion,
            @AuthenticationPrincipal UsuarioActual actual) {
        EnlacePeticion datos = peticion == null ? new EnlacePeticion(null, null) : peticion;
        return EnlaceDto.de(enlaces.crear(datos.duracionMinutos(), datos.maxUsos(), actual.id()));
    }

    @GetMapping
    public List<EnlaceDto> listar() {
        return enlaces.listar().stream().map(EnlaceDto::de).toList();
    }

    /** Idempotente: desactivar uno ya desactivado responde igual y conserva la primera fecha. */
    @PostMapping("/{id}/desactivar")
    public EnlaceDto desactivar(@PathVariable UUID id) {
        return EnlaceDto.de(enlaces.desactivar(id));
    }
}
