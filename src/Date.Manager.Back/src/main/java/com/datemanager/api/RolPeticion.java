package com.datemanager.api;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.datemanager.dominio.Permiso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo del {@code PUT /api/panel/roles/{id}}. El id del cuerpo, si viene, se ignora: manda el de la ruta. */
public record RolPeticion(
        @NotBlank(message = "Escribe el nombre del rol.") @Size(max = 100, message = "El nombre es demasiado largo.")
        String nombre,
        @Size(max = 300, message = "La descripción es demasiado larga.") String descripcion,
        List<String> permisos) {

    /** Ids snake_case a permisos; un id desconocido es un 400 (VALIDACION). */
    Set<Permiso> permisosComoEnum() {
        Set<Permiso> resultado = EnumSet.noneOf(Permiso.class);
        if (permisos != null) {
            permisos.forEach(id -> resultado.add(Permiso.deId(id)));
        }
        return resultado;
    }
}
