package com.datemanager.infraestructura.seguridad;

import java.util.UUID;

/** Quién hace la petición: el id viene del token y el rol se lee de la base al autenticar la petición. */
public record UsuarioActual(UUID id, UUID rolId) {
}
