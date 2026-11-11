package com.datemanager.infraestructura.seguridad;

import java.util.Optional;
import java.util.Set;

/**
 * Lo que el servidor HTTP necesita de la seguridad: identificar a quien hace la petición a partir de la cabecera
 * {@code Authorization} y comprobar sus permisos. Cualquier excepción que lance es un fallo inesperado (500), no
 * una credencial inválida: esa se expresa con {@link Optional#empty()}.
 */
public interface Autenticacion {

    /**
     * @param cabeceraAuthorization valor de la cabecera tal como llegó, o {@code null} si no vino
     * @return el usuario, o vacío si falta el token o no es válido (el servidor responde 401)
     */
    Optional<UsuarioActual> autenticar(String cabeceraAuthorization);

    /** {@code true} si el usuario tiene al menos uno de los permisos (el servidor responde 403 si no). */
    boolean tiene(UsuarioActual usuario, Set<String> permisosAlgunoDe);
}
