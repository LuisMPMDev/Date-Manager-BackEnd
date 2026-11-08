package com.datemanager.api;

import java.util.UUID;

import com.datemanager.dominio.Cliente;

public record ClienteDto(UUID id, String nombre, String apellido, String telefono, String correo) {

    public static ClienteDto de(Cliente c) {
        return new ClienteDto(c.id(), c.nombre(), c.apellido(), c.telefono(), c.correo());
    }
}
