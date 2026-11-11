package com.datemanager.api;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.datemanager.dominio.Permiso;

/** Cuerpo del {@code PUT /api/panel/roles/{id}}. El id del cuerpo, si viene, se ignora: manda el de la ruta. */
public record RolPeticion(String nombre, String descripcion, List<String> permisos) {

    /** Lo que antes hacían sus {@code @NotBlank} y {@code @Size(max)}. */
    void validar() {
        new Validacion()
                .noEnBlanco("nombre", nombre, "Escribe el nombre del rol.")
                .largoMaximo("nombre", nombre, 100, "El nombre es demasiado largo.")
                .largoMaximo("descripcion", descripcion, 300, "La descripción es demasiado larga.")
                .lanzarSiHay();
    }

    /** Ids snake_case a permisos; un id desconocido (o nulo) es un 400 (VALIDACION). */
    Set<Permiso> permisosComoEnum() {
        Set<Permiso> resultado = EnumSet.noneOf(Permiso.class);
        if (permisos != null) {
            permisos.forEach(id -> resultado.add(Permiso.deId(id)));
        }
        return resultado;
    }
}
