package com.datemanager.api;

import java.util.List;
import java.util.UUID;

import com.datemanager.aplicacion.UsuariosServicio;
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

/** Usuarios del panel. Sin reglas: delega en {@link UsuariosServicio}. Nunca devuelve la clave ni su hash. */
@RestController
@RequestMapping("/api/panel/usuarios")
@PreAuthorize("@permisos.tiene('gestionar_usuarios')")
public class UsuariosControlador {

    private final UsuariosServicio usuarios;

    public UsuariosControlador(UsuariosServicio usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping
    public List<UsuarioDto> listar() {
        return usuarios.listar().stream().map(UsuarioDto::de).toList();
    }

    @PutMapping("/{id}")
    public UsuarioDto guardar(@PathVariable UUID id, @Valid @RequestBody UsuarioPeticion peticion,
            @AuthenticationPrincipal UsuarioActual actual) {
        return UsuarioDto.de(usuarios.guardar(id, peticion.aDatos(), actual.id()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioActual actual) {
        usuarios.eliminar(id, actual.id());
        return ResponseEntity.noContent().build();
    }
}
