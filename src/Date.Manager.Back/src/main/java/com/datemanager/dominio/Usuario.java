package com.datemanager.dominio;

import java.util.UUID;

/** Usuario del panel. La clave no forma parte del dominio: solo la ve la capa de autenticación. */
public record Usuario(
        UUID id,
        String nombre,
        String apellido,
        String correo,
        String telefono,
        String usuario,
        UUID rolId) {
}
