package com.datemanager.infraestructura.seguridad;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.datemanager.dominio.Permiso;
import com.datemanager.infraestructura.persistencia.RolEntidad;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean {@code permisos} para {@code @PreAuthorize("@permisos.tiene('ver_citas', 'gestionar_servicios')")}:
 * basta con uno de la lista. Lee el usuario y su rol de la base en cada llamada (sin caché), así que un
 * cambio de rol o de permisos surte efecto en la siguiente petición.
 */
@Component("permisos")
public class PermisosSeguridad {

    private static final Set<String> CATALOGO =
            Arrays.stream(Permiso.values()).map(Permiso::id).collect(Collectors.toUnmodifiableSet());

    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;

    public PermisosSeguridad(UsuarioRepositorio usuarios, RolRepositorio roles) {
        this.usuarios = usuarios;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public boolean tiene(String... cualquieraDe) {
        Set<String> pedidos = Set.copyOf(Arrays.asList(cualquieraDe));
        for (String id : pedidos) {
            if (!CATALOGO.contains(id)) {
                // Error de programación en la anotación, no del usuario.
                throw new IllegalArgumentException("Permiso desconocido en @PreAuthorize: " + id);
            }
        }
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioActual actual)) {
            return false;
        }
        return usuarios.findById(actual.id())
                .flatMap(usuario -> roles.findById(usuario.getRolId()))
                .map(RolEntidad::getPermisos)
                .map(permisos -> permisos.stream().map(Permiso::id).anyMatch(pedidos::contains))
                .orElse(false);
    }
}
