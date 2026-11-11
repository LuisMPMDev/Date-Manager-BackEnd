package com.datemanager.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.datemanager.aplicacion.ServiciosServicio;
import com.datemanager.infraestructura.http.DocumentoRuta;
import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.http.Solicitud;

/** Servicios y ventanas de disponibilidad del panel. Sin reglas: delega en {@link ServiciosServicio}. */
public final class ServiciosControlador {

    private static final String ETIQUETA = "Servicios";

    private final ServiciosServicio servicios;

    public ServiciosControlador(ServiciosServicio servicios) {
        this.servicios = Objects.requireNonNull(servicios);
    }

    public List<Ruta> rutas() {
        return List.of(
                new Ruta("GET", "/api/panel/servicios", Set.of("ver_panel", "ver_citas", "gestionar_servicios"),
                        false, this::listar,
                        new DocumentoRuta(ETIQUETA, "Lista los servicios con sus ventanas", null, ServicioDto.class,
                                true, 200)),
                new Ruta("PUT", "/api/panel/servicios/{id}", Set.of("gestionar_servicios"), false, this::guardar,
                        new DocumentoRuta(ETIQUETA, "Crea o edita un servicio (reemplaza sus ventanas)",
                                ServicioPeticion.class, ServicioDto.class, false, 200)));
    }

    Respuesta listar(Solicitud solicitud) {
        return Respuesta.ok(servicios.listar().stream().map(ServicioDto::de).toList());
    }

    Respuesta guardar(Solicitud solicitud) {
        UUID id = solicitud.parametroUuid("id");
        ServicioPeticion peticion = Json.leer(solicitud.cuerpoObjeto(), ServicioPeticion.class);
        peticion.validar();
        return Respuesta.ok(ServicioDto.de(servicios.guardar(peticion.aDominio(id))));
    }
}
