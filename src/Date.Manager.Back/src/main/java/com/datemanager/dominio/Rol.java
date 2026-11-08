package com.datemanager.dominio;

import java.util.Set;
import java.util.UUID;

public record Rol(UUID id, String nombre, String descripcion, Set<Permiso> permisos) {

    public Rol {
        permisos = permisos == null ? Set.of() : Set.copyOf(permisos);
    }
}
