package com.datemanager.api;

import java.util.UUID;

import com.datemanager.aplicacion.UsuariosServicio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del {@code PUT /api/panel/usuarios/{id}}. {@code clave} solo entra (vacía o ausente = conservar la
 * actual al editar). El id del cuerpo, si viene, se ignora: manda el de la ruta.
 */
public record UsuarioPeticion(
        @NotBlank(message = "Escribe el nombre.") @Size(max = 100, message = "El nombre es demasiado largo.")
        String nombre,
        @NotBlank(message = "Escribe el apellido.") @Size(max = 100, message = "El apellido es demasiado largo.")
        String apellido,
        @NotBlank(message = "Escribe el correo.") @Size(max = 254, message = "El correo es demasiado largo.")
        String correo,
        @Size(max = 40, message = "El teléfono es demasiado largo.")
        String telefono,
        @NotBlank(message = "Escribe el nombre de usuario.")
        @Size(max = 100, message = "El nombre de usuario es demasiado largo.")
        String usuario,
        String clave,
        @NotNull(message = "Elige un rol.") UUID rolId) {

    UsuariosServicio.Datos aDatos() {
        return new UsuariosServicio.Datos(nombre, apellido, correo, telefono, usuario, clave, rolId);
    }

    /** Nunca incluye la clave: un registro de la petición no debe poder filtrarla. */
    @Override
    public String toString() {
        return "UsuarioPeticion[usuario=" + usuario + ", rolId=" + rolId + "]";
    }
}
