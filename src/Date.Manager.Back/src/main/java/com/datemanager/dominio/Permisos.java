package com.datemanager.dominio;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Reglas puras de permisos y protecciones de roles (portadas de {@code core/permisos.ts}). */
public final class Permisos {

    private Permisos() {
    }

    /** {@code gestionar_citas} y {@code atender_citas} requieren {@code ver_citas}. */
    public static Set<Permiso> conDependencias(Set<Permiso> permisos) {
        EnumSet<Permiso> resultado = EnumSet.noneOf(Permiso.class);
        resultado.addAll(permisos);
        if (resultado.contains(Permiso.GESTIONAR_CITAS) || resultado.contains(Permiso.ATENDER_CITAS)) {
            resultado.add(Permiso.VER_CITAS);
        }
        return Set.copyOf(resultado);
    }

    /** Mensaje de error si el cambio deja al actor sin {@code gestionar_roles} en su propio rol. */
    public static Optional<String> validarCambioRol(UUID actorRolId, Rol antes, Rol despues) {
        if (actorRolId != null
                && actorRolId.equals(antes.id())
                && antes.permisos().contains(Permiso.GESTIONAR_ROLES)
                && !despues.permisos().contains(Permiso.GESTIONAR_ROLES)) {
            return Optional.of("No puedes quitarte el permiso de gestionar roles de tu propio rol.");
        }
        return Optional.empty();
    }

    public static boolean puedeEliminarRol(UUID rolId, List<Usuario> usuarios) {
        return usuarios.stream().noneMatch(u -> rolId.equals(u.rolId()));
    }

    /** Con {@code gestionar_roles} se puede asignar cualquier rol; sin él, solo roles que no excedan los permisos del actor. */
    public static boolean puedeAsignarRol(Set<Permiso> actor, Set<Permiso> destino) {
        return actor.contains(Permiso.GESTIONAR_ROLES) || actor.containsAll(destino);
    }
}
