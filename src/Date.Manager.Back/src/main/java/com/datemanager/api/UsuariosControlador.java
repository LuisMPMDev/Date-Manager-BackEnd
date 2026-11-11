package com.datemanager.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.datemanager.aplicacion.UsuariosServicio;
import com.datemanager.infraestructura.http.DocumentoRuta;
import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.http.Solicitud;

/** Usuarios del panel. Sin reglas: delega en {@link UsuariosServicio}. Nunca devuelve la clave ni su hash. */
public final class UsuariosControlador {

    private static final String ETIQUETA = "Usuarios";
    private static final Set<String> PERMISO = Set.of("gestionar_usuarios");

    private final UsuariosServicio usuarios;

    public UsuariosControlador(UsuariosServicio usuarios) {
        this.usuarios = Objects.requireNonNull(usuarios);
    }

    public List<Ruta> rutas() {
        return List.of(
                new Ruta("GET", "/api/panel/usuarios", PERMISO, false, this::listar,
                        new DocumentoRuta(ETIQUETA, "Lista los usuarios", null, UsuarioDto.class, true, 200)),
                new Ruta("PUT", "/api/panel/usuarios/{id}", PERMISO, false, this::guardar,
                        new DocumentoRuta(ETIQUETA, "Crea o edita un usuario", UsuarioPeticion.class,
                                UsuarioDto.class, false, 200)),
                new Ruta("DELETE", "/api/panel/usuarios/{id}", PERMISO, false, this::eliminar,
                        new DocumentoRuta(ETIQUETA, "Elimina un usuario", null, null, false, 204)));
    }

    Respuesta listar(Solicitud solicitud) {
        return Respuesta.ok(usuarios.listar().stream().map(UsuarioDto::de).toList());
    }

    /** El id del cuerpo, si viene, se ignora: manda el de la ruta. */
    Respuesta guardar(Solicitud solicitud) {
        UUID id = solicitud.parametroUuid("id");
        UsuarioPeticion peticion = Json.leer(solicitud.cuerpoObjeto(), UsuarioPeticion.class);
        peticion.validar();
        return Respuesta.ok(UsuarioDto.de(usuarios.guardar(id, peticion.aDatos(), solicitud.usuario().id())));
    }

    Respuesta eliminar(Solicitud solicitud) {
        usuarios.eliminar(solicitud.parametroUuid("id"), solicitud.usuario().id());
        return Respuesta.sinContenido();
    }
}
