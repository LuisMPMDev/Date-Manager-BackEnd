package com.datemanager.dominio;

import java.util.UUID;

public record Cliente(UUID id, String nombre, String apellido, String telefono, String correo) {
}
