package com.datemanager.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.datemanager.aplicacion.EnlacesServicio;
import com.datemanager.infraestructura.http.DocumentoRuta;
import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.http.Solicitud;

/** Enlaces de invitación del panel ({@code gestionar_enlaces}). Sin reglas: delega en {@link EnlacesServicio}. */
public final class EnlacesControlador {

    private static final String ETIQUETA = "Enlaces";
    private static final Set<String> PERMISO = Set.of("gestionar_enlaces");

    private final EnlacesServicio enlaces;

    public EnlacesControlador(EnlacesServicio enlaces) {
        this.enlaces = Objects.requireNonNull(enlaces);
    }

    public List<Ruta> rutas() {
        return List.of(
                new Ruta("POST", "/api/panel/enlaces", PERMISO, false, this::crear,
                        new DocumentoRuta(ETIQUETA, "Crea un enlace de invitación", EnlacePeticion.class,
                                EnlaceDto.class, false, 201)),
                new Ruta("GET", "/api/panel/enlaces", PERMISO, false, this::listar,
                        new DocumentoRuta(ETIQUETA, "Lista los enlaces, los más nuevos primero", null,
                                EnlaceDto.class, true, 200)),
                new Ruta("POST", "/api/panel/enlaces/{id}/desactivar", PERMISO, false, this::desactivar,
                        new DocumentoRuta(ETIQUETA, "Desactiva un enlace (idempotente)", null, EnlaceDto.class,
                                false, 200)));
    }

    /** El cuerpo es opcional: sin él, 24 h y sin límite de usos. */
    Respuesta crear(Solicitud solicitud) {
        EnlacePeticion datos = solicitud.tieneCuerpo()
                ? Json.leer(solicitud.cuerpoObjeto(), EnlacePeticion.class)
                : new EnlacePeticion(null, null);
        return Respuesta.creada(EnlaceDto.de(enlaces.crear(datos.duracionMinutos(), datos.maxUsos(),
                solicitud.usuario().id())));
    }

    Respuesta listar(Solicitud solicitud) {
        return Respuesta.ok(enlaces.listar().stream().map(EnlaceDto::de).toList());
    }

    /** Idempotente: desactivar uno ya desactivado responde igual y conserva la primera fecha. */
    Respuesta desactivar(Solicitud solicitud) {
        return Respuesta.ok(EnlaceDto.de(enlaces.desactivar(solicitud.parametroUuid("id"))));
    }
}
