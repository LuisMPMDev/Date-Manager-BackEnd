package com.datemanager.api;

import java.util.List;
import java.util.UUID;

import com.datemanager.aplicacion.RolesServicio;
import com.datemanager.infraestructura.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Roles y catálogo de permisos del panel. Sin reglas: delega en {@link RolesServicio}. */
@RestController
@RequestMapping("/api/panel")
public class RolesControlador {

    private final RolesServicio roles;

    public RolesControlador(RolesServicio roles) {
        this.roles = roles;
    }

    @GetMapping("/roles")
    @PreAuthorize("@permisos.tiene('gestionar_roles', 'gestionar_usuarios')")
    public List<RolDto> listar() {
        return roles.listar().stream().map(RolDto::de).toList();
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("@permisos.tiene('gestionar_roles')")
    public RolDto guardar(@PathVariable UUID id, @Valid @RequestBody RolPeticion peticion,
            @AuthenticationPrincipal UsuarioActual actual) {
        return RolDto.de(roles.guardar(id, peticion.nombre(), peticion.descripcion(),
                peticion.permisosComoEnum(), actual.id()));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("@permisos.tiene('gestionar_roles')")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        roles.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permisos")
    @PreAuthorize("@permisos.tiene('gestionar_roles')")
    public List<PermisoDto> permisos() {
        return PermisoDto.catalogo();
    }
}
