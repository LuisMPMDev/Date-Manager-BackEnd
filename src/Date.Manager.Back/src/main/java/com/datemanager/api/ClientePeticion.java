package com.datemanager.api;

import com.datemanager.dominio.DatosCliente;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos de un cliente: cuerpo del {@code PUT /api/panel/clientes/{id}} y parte {@code cliente} de una reserva.
 * El correo lo valida y normaliza {@code aplicacion} (misma regla que en la reserva pública).
 */
public record ClientePeticion(
        @NotBlank(message = "Escribe el nombre.") @Size(max = 100, message = "El nombre es demasiado largo.")
        String nombre,
        @NotBlank(message = "Escribe el apellido.") @Size(max = 100, message = "El apellido es demasiado largo.")
        String apellido,
        @NotBlank(message = "Escribe el teléfono.") @Size(max = 40, message = "El teléfono es demasiado largo.")
        String telefono,
        @NotBlank(message = "Escribe el correo.") @Size(max = 254, message = "El correo es demasiado largo.")
        String correo) {

    DatosCliente aDatos() {
        return new DatosCliente(nombre, apellido, telefono, correo);
    }
}
