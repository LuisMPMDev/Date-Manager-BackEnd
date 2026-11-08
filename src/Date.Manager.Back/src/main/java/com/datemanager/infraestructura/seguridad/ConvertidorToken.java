package com.datemanager.infraestructura.seguridad;

import java.util.List;
import java.util.UUID;

import com.datemanager.infraestructura.persistencia.UsuarioEntidad;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Convierte un token ya validado (firma y vigencia) en el {@link UsuarioActual}. Lee el usuario de la base en
 * cada petición: si ya no existe, el token deja de valer (401) aunque no haya vencido.
 */
@Component
class ConvertidorToken implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UsuarioRepositorio usuarios;

    ConvertidorToken(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("Token sin usuario válido.");
        }
        UsuarioEntidad usuario = usuarios.findById(id)
                .orElseThrow(() -> new InvalidBearerTokenException("El usuario del token ya no existe."));
        UsuarioActual actual = new UsuarioActual(usuario.getId(), usuario.getRolId());
        return UsernamePasswordAuthenticationToken.authenticated(actual, null, List.of());
    }
}
