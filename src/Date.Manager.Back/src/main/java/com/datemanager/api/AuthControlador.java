package com.datemanager.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.datemanager.aplicacion.AutenticacionServicio;
import com.datemanager.aplicacion.AutenticacionServicio.Sesion;
import com.datemanager.infraestructura.http.DocumentoRuta;
import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.http.Solicitud;

/** {@code /api/auth}: inicio de sesión (público) y datos de la sesión actual. */
public final class AuthControlador {

    private static final String ETIQUETA = "Autenticación";

    /** Petición de login. La clave es exacta (no se recorta); el usuario se normaliza en el servicio. */
    public record LoginPeticion(String usuario, String clave) {

        void validar() {
            new Validacion()
                    .noEnBlanco("usuario", usuario, "Escribe el usuario.")
                    .noVacio("clave", clave, "Escribe la contraseña.")
                    .lanzarSiHay();
        }
    }

    /** {@code token} solo viene en el login; en {@code /yo} se omite (nulo). */
    public record SesionDto(String token, UsuarioDto usuario, RolDto rol) {

        static SesionDto de(Sesion sesion) {
            return new SesionDto(sesion.token(), UsuarioDto.de(sesion.usuario()), RolDto.de(sesion.rol()));
        }
    }

    private final AutenticacionServicio autenticacion;

    public AuthControlador(AutenticacionServicio autenticacion) {
        this.autenticacion = Objects.requireNonNull(autenticacion);
    }

    public List<Ruta> rutas() {
        return List.of(
                new Ruta("POST", "/api/auth/login", Set.of(), true, this::login,
                        new DocumentoRuta(ETIQUETA, "Inicia sesión y devuelve el token", LoginPeticion.class,
                                SesionDto.class, false, 200)),
                new Ruta("GET", "/api/auth/yo", Set.of(), false, this::yo,
                        new DocumentoRuta(ETIQUETA, "Usuario y rol de la sesión actual", null, SesionDto.class,
                                false, 200)));
    }

    Respuesta login(Solicitud solicitud) {
        LoginPeticion peticion = Json.leer(solicitud.cuerpoObjeto(), LoginPeticion.class);
        peticion.validar();
        return Respuesta.ok(SesionDto.de(autenticacion.iniciarSesion(peticion.usuario(), peticion.clave())));
    }

    Respuesta yo(Solicitud solicitud) {
        return Respuesta.ok(SesionDto.de(autenticacion.yo(solicitud.usuario().id())));
    }
}
