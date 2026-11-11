package com.datemanager.api;

import java.util.UUID;

import com.datemanager.aplicacion.UsuariosServicio;

/**
 * Cuerpo del {@code PUT /api/panel/usuarios/{id}}. {@code clave} solo entra (vacía o ausente = conservar la
 * actual al editar). El id del cuerpo, si viene, se ignora: manda el de la ruta.
 */
public record UsuarioPeticion(String nombre, String apellido, String correo, String telefono, String usuario,
        String clave, UUID rolId) {

    /** Lo que antes hacían sus {@code @NotBlank}, {@code @Size(max)} y {@code @NotNull}. */
    void validar() {
        new Validacion()
                .noEnBlanco("nombre", nombre, "Escribe el nombre.")
                .largoMaximo("nombre", nombre, 100, "El nombre es demasiado largo.")
                .noEnBlanco("apellido", apellido, "Escribe el apellido.")
                .largoMaximo("apellido", apellido, 100, "El apellido es demasiado largo.")
                .noEnBlanco("correo", correo, "Escribe el correo.")
                .largoMaximo("correo", correo, 254, "El correo es demasiado largo.")
                .largoMaximo("telefono", telefono, 40, "El teléfono es demasiado largo.")
                .noEnBlanco("usuario", usuario, "Escribe el nombre de usuario.")
                .largoMaximo("usuario", usuario, 100, "El nombre de usuario es demasiado largo.")
                .noNulo("rolId", rolId, "Elige un rol.")
                .lanzarSiHay();
    }

    UsuariosServicio.Datos aDatos() {
        return new UsuariosServicio.Datos(nombre, apellido, correo, telefono, usuario, clave, rolId);
    }

    /** Nunca incluye la clave: un registro de la petición no debe poder filtrarla. */
    @Override
    public String toString() {
        return "UsuarioPeticion[usuario=" + usuario + ", rolId=" + rolId + "]";
    }
}
