package com.datemanager.aplicacion;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.datemanager.dominio.Permiso;
import com.datemanager.infraestructura.persistencia.RolEntidad;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import org.springframework.stereotype.Component;

/**
 * Una sola regla para «no dejar al sistema sin nadie que gestione roles»: cuenta los usuarios con
 * {@code gestionar_roles} que quedarían tras un cambio. La usan {@link RolesServicio} y {@link UsuariosServicio};
 * debe llamarse con el bloqueo de administración tomado, para que la lectura no quede desfasada.
 */
@Component
class ProteccionGestores {

    static final String MENSAJE = "Debe quedar al menos un usuario que pueda gestionar roles.";

    private final RolRepositorio roles;
    private final UsuarioRepositorio usuarios;

    ProteccionGestores(RolRepositorio roles, UsuarioRepositorio usuarios) {
        this.roles = roles;
        this.usuarios = usuarios;
    }

    /** Cuántos usuarios, sin contar a {@code excluido}, tienen hoy {@code gestionar_roles} por su rol. */
    long otrosGestores(UUID excluido) {
        Set<UUID> rolesGestores = rolesGestores(null, null);
        return rolesGestores.isEmpty() ? 0 : usuarios.countByIdNotAndRolIdIn(excluido, rolesGestores);
    }

    /** Cuántos usuarios tendrían {@code gestionar_roles} si el rol {@code rolId} pasara a tener {@code permisosNuevos}. */
    long gestoresTrasEditarRol(UUID rolId, Set<Permiso> permisosNuevos) {
        Set<UUID> rolesGestores = rolesGestores(rolId, permisosNuevos);
        return rolesGestores.isEmpty() ? 0 : usuarios.countByRolIdIn(rolesGestores);
    }

    static boolean gestionaRoles(Set<Permiso> permisos) {
        return permisos.contains(Permiso.GESTIONAR_ROLES);
    }

    /** Ids de los roles con {@code gestionar_roles}; el rol {@code rolEditado}, si se indica, con sus permisos nuevos. */
    private Set<UUID> rolesGestores(UUID rolEditado, Set<Permiso> permisosNuevos) {
        Set<UUID> resultado = new HashSet<>();
        for (RolEntidad rol : roles.findAll()) {
            Set<Permiso> permisos = rol.getId().equals(rolEditado) ? permisosNuevos : rol.getPermisos();
            if (gestionaRoles(permisos)) {
                resultado.add(rol.getId());
            }
        }
        return resultado;
    }
}
