package com.datemanager.api;

import java.util.UUID;

import com.datemanager.dominio.Usuario;

/** Usuario tal como sale en el JSON: nunca lleva la clave ni su hash. */
public record UsuarioDto(UUID id, String nombre, String apellido, String correo, String telefono, String usuario,
        UUID rolId) {

    public static UsuarioDto de(Usuario u) {
        return new UsuarioDto(u.id(), u.nombre(), u.apellido(), u.correo(), u.telefono(), u.usuario(), u.rolId());
    }
}
