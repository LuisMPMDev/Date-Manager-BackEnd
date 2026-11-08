package com.datemanager.aplicacion;

import java.util.UUID;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Rol;
import com.datemanager.dominio.Usuario;
import com.datemanager.dominio.Validaciones;
import com.datemanager.infraestructura.persistencia.RolEntidad;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioEntidad;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import com.datemanager.infraestructura.seguridad.ServicioJwt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inicio de sesión y datos del usuario actual. */
@Service
public class AutenticacionServicio {

    /** Un solo mensaje para clave mala y usuario inexistente: no revela qué usuarios existen. */
    static final String MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos.";

    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final PasswordEncoder codificador;
    private final ServicioJwt servicioJwt;
    /** Hash de relleno: con un usuario inexistente también se paga un cálculo BCrypt (mismo tiempo de respuesta). */
    private final String hashDeRelleno;

    public AutenticacionServicio(UsuarioRepositorio usuarios, RolRepositorio roles, PasswordEncoder codificador,
            ServicioJwt servicioJwt) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.codificador = codificador;
        this.servicioJwt = servicioJwt;
        this.hashDeRelleno = codificador.encode(UUID.randomUUID().toString());
    }

    /** Sesión iniciada: el token solo en el login; en {@link #yo(UUID)} va nulo. */
    public record Sesion(String token, Usuario usuario, Rol rol) {
    }

    @Transactional(readOnly = true)
    public Sesion iniciarSesion(String usuario, String clave) {
        UsuarioEntidad encontrado = usuarios.findByUsuario(Validaciones.normalizarUsuario(usuario)).orElse(null);
        String hash = encontrado != null ? encontrado.getClaveHash() : hashDeRelleno;
        boolean claveCorrecta = clave != null && codificador.matches(clave, hash);
        if (encontrado == null || !claveCorrecta) {
            throw new ErrorNegocio(CodigoError.CREDENCIALES_INVALIDAS, MENSAJE_CREDENCIALES);
        }
        return new Sesion(servicioJwt.emitir(encontrado.getId()), encontrado.aDominio(), rolDe(encontrado));
    }

    @Transactional(readOnly = true)
    public Sesion yo(UUID usuarioId) {
        UsuarioEntidad usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.NO_AUTENTICADO, "Inicia sesión para continuar."));
        return new Sesion(null, usuario.aDominio(), rolDe(usuario));
    }

    private Rol rolDe(UsuarioEntidad usuario) {
        return roles.findById(usuario.getRolId()).map(RolEntidad::aDominio)
                .orElseThrow(() -> new IllegalStateException("Usuario sin rol: " + usuario.getId()));
    }
}
