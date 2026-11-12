package com.datemanager.aplicacion;

import java.util.Objects;
import java.util.UUID;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Rol;
import com.datemanager.dominio.Usuario;
import com.datemanager.dominio.Validaciones;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.Transaccion;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio.UsuarioConClave;
import com.datemanager.infraestructura.seguridad.ServicioJwt;

/** Inicio de sesión y datos del usuario actual. */
public class AutenticacionServicio {

    /** Un solo mensaje para clave mala y usuario inexistente: no revela qué usuarios existen. */
    static final String MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos.";

    private final Transaccion transaccion;
    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final CodificadorClave codificador;
    private final ServicioJwt servicioJwt;
    /** Hash de relleno: con un usuario inexistente también se paga un cálculo BCrypt (mismo tiempo de respuesta). */
    private final String hashDeRelleno;

    public AutenticacionServicio(Transaccion transaccion, UsuarioRepositorio usuarios, RolRepositorio roles,
            CodificadorClave codificador, ServicioJwt servicioJwt) {
        this.transaccion = Objects.requireNonNull(transaccion);
        this.usuarios = Objects.requireNonNull(usuarios);
        this.roles = Objects.requireNonNull(roles);
        this.codificador = Objects.requireNonNull(codificador);
        this.servicioJwt = Objects.requireNonNull(servicioJwt);
        this.hashDeRelleno = codificador.codificar(UUID.randomUUID().toString());
    }

    /** Sesión iniciada: el token solo en el login; en {@link #yo(UUID)} va nulo. */
    public record Sesion(String token, Usuario usuario, Rol rol) {
    }

    /**
     * La clave se comprueba fuera de toda transacción: BCrypt tarda y, mientras calcula, el hilo no retiene una
     * conexión del pool (un aluvión de logins no deja sin conexiones al resto de la API).
     */
    public Sesion iniciarSesion(String usuario, String clave) {
        String normalizado = Validaciones.normalizarUsuario(usuario);
        UsuarioConClave encontrado = transaccion.leer(() -> usuarios.buscarPorUsuario(normalizado).orElse(null));
        String hash = encontrado != null ? encontrado.claveHash() : hashDeRelleno;
        boolean claveCorrecta = clave != null && codificador.coincide(clave, hash);
        if (encontrado == null || !claveCorrecta) {
            throw new ErrorNegocio(CodigoError.CREDENCIALES_INVALIDAS, MENSAJE_CREDENCIALES);
        }
        Usuario encontradoUsuario = encontrado.usuario();
        Rol rol = transaccion.leer(() -> rolDe(encontradoUsuario));
        return new Sesion(servicioJwt.emitir(encontradoUsuario.id()), encontradoUsuario, rol);
    }

    public Sesion yo(UUID usuarioId) {
        return transaccion.leer(() -> {
            Usuario usuario = usuarios.buscar(usuarioId).orElseThrow(
                    () -> new ErrorNegocio(CodigoError.NO_AUTENTICADO, "Inicia sesión para continuar."));
            return new Sesion(null, usuario, rolDe(usuario));
        });
    }

    private Rol rolDe(Usuario usuario) {
        return roles.buscar(usuario.rolId())
                .orElseThrow(() -> new IllegalStateException("Usuario sin rol: " + usuario.id()));
    }
}
