package com.datemanager.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.datemanager.aplicacion.RolesServicio;
import com.datemanager.infraestructura.http.DocumentoRuta;
import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.http.Solicitud;

/** Roles y catálogo de permisos del panel. Sin reglas: delega en {@link RolesServicio}. */
public final class RolesControlador {

    private static final String ETIQUETA = "Roles";

    private final RolesServicio roles;

    public RolesControlador(RolesServicio roles) {
        this.roles = Objects.requireNonNull(roles);
    }

    public List<Ruta> rutas() {
        return List.of(
                new Ruta("GET", "/api/panel/roles", Set.of("gestionar_roles", "gestionar_usuarios"), false,
                        this::listar, new DocumentoRuta(ETIQUETA, "Lista los roles con sus permisos", null,
                                RolDto.class, true, 200)),
                new Ruta("PUT", "/api/panel/roles/{id}", Set.of("gestionar_roles"), false, this::guardar,
                        new DocumentoRuta(ETIQUETA, "Crea o edita un rol", RolPeticion.class, RolDto.class, false,
                                200)),
                new Ruta("DELETE", "/api/panel/roles/{id}", Set.of("gestionar_roles"), false, this::eliminar,
                        new DocumentoRuta(ETIQUETA, "Elimina un rol sin usuarios", null, null, false, 204)),
                new Ruta("GET", "/api/panel/permisos", Set.of("gestionar_roles"), false, this::permisos,
                        new DocumentoRuta(ETIQUETA, "Catálogo de permisos", null, PermisoDto.class, true, 200)));
    }

    Respuesta listar(Solicitud solicitud) {
        return Respuesta.ok(roles.listar().stream().map(RolDto::de).toList());
    }

    /** El id del cuerpo, si viene, se ignora: manda el de la ruta. */
    Respuesta guardar(Solicitud solicitud) {
        UUID id = solicitud.parametroUuid("id");
        RolPeticion peticion = Json.leer(solicitud.cuerpoObjeto(), RolPeticion.class);
        peticion.validar();
        return Respuesta.ok(RolDto.de(roles.guardar(id, peticion.nombre(), peticion.descripcion(),
                peticion.permisosComoEnum(), solicitud.usuario().id())));
    }

    Respuesta eliminar(Solicitud solicitud) {
        roles.eliminar(solicitud.parametroUuid("id"));
        return Respuesta.sinContenido();
    }

    Respuesta permisos(Solicitud solicitud) {
        return Respuesta.ok(PermisoDto.catalogo());
    }
}
