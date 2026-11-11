package com.datemanager.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.datemanager.aplicacion.ClientesServicio;
import com.datemanager.infraestructura.http.DocumentoRuta;
import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.http.Solicitud;

/** Clientes del panel (listar y editar; nacen al reservar). Sin reglas: delega en {@link ClientesServicio}. */
public final class ClientesControlador {

    private static final String ETIQUETA = "Clientes";

    private final ClientesServicio clientes;

    public ClientesControlador(ClientesServicio clientes) {
        this.clientes = Objects.requireNonNull(clientes);
    }

    public List<Ruta> rutas() {
        return List.of(
                new Ruta("GET", "/api/panel/clientes", Set.of("ver_citas", "gestionar_clientes"), false,
                        this::listar,
                        new DocumentoRuta(ETIQUETA, "Lista los clientes", null, ClienteDto.class, true, 200)),
                new Ruta("PUT", "/api/panel/clientes/{id}", Set.of("gestionar_clientes"), false, this::guardar,
                        new DocumentoRuta(ETIQUETA, "Edita los datos de un cliente", ClientePeticion.class,
                                ClienteDto.class, false, 200)));
    }

    Respuesta listar(Solicitud solicitud) {
        return Respuesta.ok(clientes.listar().stream().map(ClienteDto::de).toList());
    }

    /** El id del cuerpo, si viene, se ignora: manda el de la ruta. */
    Respuesta guardar(Solicitud solicitud) {
        UUID id = solicitud.parametroUuid("id");
        ClientePeticion peticion = Json.leer(solicitud.cuerpoObjeto(), ClientePeticion.class);
        peticion.validar();
        return Respuesta.ok(ClienteDto.de(clientes.guardar(id, peticion.aDatos())));
    }
}
