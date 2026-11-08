package com.datemanager.api;

import java.util.Arrays;
import java.util.List;

import com.datemanager.dominio.Permiso;

/** Entrada del catálogo de permisos: id snake_case y etiqueta en español. */
public record PermisoDto(String id, String etiqueta) {

    /** Todo el catálogo, en el orden estable del enumerado. */
    public static List<PermisoDto> catalogo() {
        return Arrays.stream(Permiso.values()).map(p -> new PermisoDto(p.id(), p.etiqueta())).toList();
    }
}
