package com.datemanager.api;

import com.datemanager.dominio.DatosCliente;

/**
 * Datos de un cliente: cuerpo del {@code PUT /api/panel/clientes/{id}} y parte {@code cliente} de una reserva.
 * El correo lo valida y normaliza {@code aplicacion} (misma regla que en la reserva pública).
 */
public record ClientePeticion(String nombre, String apellido, String telefono, String correo) {

    /** Como cuerpo propio: lanza si algo falla. */
    void validar() {
        Validacion v = new Validacion();
        validar(v);
        v.lanzarSiHay();
    }

    /** Lo que antes hacían sus {@code @NotBlank} y {@code @Size(max)}; acumula en {@code v}. */
    void validar(Validacion v) {
        v.noEnBlanco("nombre", nombre, "Escribe el nombre.")
                .largoMaximo("nombre", nombre, 100, "El nombre es demasiado largo.")
                .noEnBlanco("apellido", apellido, "Escribe el apellido.")
                .largoMaximo("apellido", apellido, 100, "El apellido es demasiado largo.")
                .noEnBlanco("telefono", telefono, "Escribe el teléfono.")
                .largoMaximo("telefono", telefono, 40, "El teléfono es demasiado largo.")
                .noEnBlanco("correo", correo, "Escribe el correo.")
                .largoMaximo("correo", correo, 254, "El correo es demasiado largo.");
    }

    DatosCliente aDatos() {
        return new DatosCliente(nombre, apellido, telefono, correo);
    }
}
