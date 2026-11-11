package com.datemanager.infraestructura.seguridad;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;
import com.datemanager.dominio.Usuario;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.Transaccion;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;

/**
 * La {@link Autenticacion} de la aplicación: identifica a quien llama por el token {@code Bearer} y decide sus
 * permisos. Lee el usuario y su rol de la base en cada llamada (sin caché), así que borrar un usuario o cambiarle el
 * rol o los permisos al rol surte efecto en la siguiente petición, aunque su token siga vigente.
 */
public final class Autenticador implements Autenticacion {

    private static final String ESQUEMA = "Bearer ";

    private static final Set<String> CATALOGO =
            Arrays.stream(Permiso.values()).map(Permiso::id).collect(Collectors.toUnmodifiableSet());

    private final ServicioJwt jwt;
    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final Transaccion tx;

    public Autenticador(ServicioJwt jwt, UsuarioRepositorio usuarios, RolRepositorio roles, Transaccion tx) {
        this.jwt = Objects.requireNonNull(jwt);
        this.usuarios = Objects.requireNonNull(usuarios);
        this.roles = Objects.requireNonNull(roles);
        this.tx = Objects.requireNonNull(tx);
    }

    /**
     * Acepta {@code Bearer <token>} (el esquema sin distinguir mayúsculas, como Spring) con un solo espacio y un token
     * no vacío y sin espacios. Un token inválido o de un usuario que ya no existe es vacío, no un error.
     */
    @Override
    public Optional<UsuarioActual> autenticar(String cabeceraAuthorization) {
        if (cabeceraAuthorization == null || cabeceraAuthorization.length() <= ESQUEMA.length()
                || !cabeceraAuthorization.regionMatches(true, 0, ESQUEMA, 0, ESQUEMA.length())) {
            return Optional.empty();
        }
        String token = cabeceraAuthorization.substring(ESQUEMA.length());
        for (int i = 0; i < token.length(); i++) {
            if (Character.isWhitespace(token.charAt(i)) || Character.isISOControl(token.charAt(i))) {
                return Optional.empty();
            }
        }
        Optional<UUID> id = jwt.validar(token);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        return tx.leer(() -> usuarios.buscar(id.get())).map(usuario -> new UsuarioActual(usuario.id(), usuario.rolId()));
    }

    /**
     * @throws IllegalArgumentException si algún id no está en el catálogo {@link Permiso}: es un error del código que
     *     declaró la ruta, no del usuario (la {@code Ruta} ya lo rechaza al crearla)
     */
    @Override
    public boolean tiene(UsuarioActual usuario, Set<String> permisosAlgunoDe) {
        validarCatalogo(permisosAlgunoDe);
        if (permisosAlgunoDe.isEmpty()) {
            return false;
        }
        return tx.leer(() -> usuarios.buscar(usuario.id())
                .map(Usuario::rolId)
                .flatMap(roles::buscar)
                .map(Rol::permisos)
                .map(permisos -> permisos.stream().map(Permiso::id).anyMatch(permisosAlgunoDe::contains))
                .orElse(false));
    }

    /** Falla con {@link IllegalArgumentException} si algún id no es un permiso del catálogo. */
    public static void validarCatalogo(Set<String> permisos) {
        for (String id : permisos) {
            if (!CATALOGO.contains(id)) {
                throw new IllegalArgumentException("Permiso desconocido: " + id);
            }
        }
    }
}
