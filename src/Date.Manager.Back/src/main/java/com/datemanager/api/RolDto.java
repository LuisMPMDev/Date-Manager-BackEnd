package com.datemanager.api;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;

/** Rol en el JSON; {@code permisos} son los ids snake_case en el orden del catálogo (estable). */
public record RolDto(UUID id, String nombre, String descripcion, List<String> permisos) {

    public static RolDto de(Rol rol) {
        List<String> permisos = rol.permisos().stream()
                .sorted(Comparator.comparingInt(Permiso::ordinal))
                .map(Permiso::id)
                .toList();
        return new RolDto(rol.id(), rol.nombre(), rol.descripcion(), permisos);
    }
}
