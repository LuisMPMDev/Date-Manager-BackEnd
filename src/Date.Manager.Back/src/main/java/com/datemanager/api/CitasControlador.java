package com.datemanager.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.datemanager.aplicacion.CitasServicio;
import com.datemanager.dominio.EstadoCita;
import com.datemanager.infraestructura.http.DocumentoRuta;
import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.http.Solicitud;

/** Citas del panel y horarios libres de un servicio. Sin reglas: delega en {@link CitasServicio}. */
public final class CitasControlador {

    private static final String ETIQUETA = "Citas";

    private final CitasServicio citas;

    public CitasControlador(CitasServicio citas) {
        this.citas = Objects.requireNonNull(citas);
    }

    public List<Ruta> rutas() {
        return List.of(
                new Ruta("GET", "/api/panel/citas", Set.of("ver_panel", "ver_citas"), false, this::listar,
                        new DocumentoRuta(ETIQUETA, "Lista las citas (filtros desde, hasta y estado)", null,
                                CitaDto.class, true, 200)),
                new Ruta("POST", "/api/panel/citas", Set.of("gestionar_citas"), false, this::reservar,
                        new DocumentoRuta(ETIQUETA, "Reserva una cita", ReservaPeticion.class, CitaDto.class, false,
                                201)),
                new Ruta("POST", "/api/panel/citas/{id}/mover", Set.of("gestionar_citas"), false, this::mover,
                        new DocumentoRuta(ETIQUETA, "Mueve una cita a otro horario", MoverPeticion.class,
                                CitaDto.class, false, 200)),
                new Ruta("POST", "/api/panel/citas/{id}/cancelar", Set.of("gestionar_citas"), false, this::cancelar,
                        new DocumentoRuta(ETIQUETA, "Cancela una cita", null, CitaDto.class, false, 200)),
                new Ruta("POST", "/api/panel/citas/{id}/atender", Set.of("atender_citas"), false, this::atender,
                        new DocumentoRuta(ETIQUETA, "Marca una cita como atendida", null, CitaDto.class, false, 200)),
                new Ruta("GET", "/api/panel/servicios/{id}/horarios", Set.of("gestionar_citas"), false,
                        this::horariosLibres,
                        new DocumentoRuta(ETIQUETA, "Horas HH:mm libres de un servicio en una fecha", null,
                                String.class, true, 200)));
    }

    Respuesta listar(Solicitud solicitud) {
        LocalDate desde = FormatoFechas.fechaOpcional("desde", solicitud.consulta("desde").orElse(null));
        LocalDate hasta = FormatoFechas.fechaOpcional("hasta", solicitud.consulta("hasta").orElse(null));
        EstadoCita estado = solicitud.consultaEnum("estado", EstadoCita.class);
        return Respuesta.ok(citas.listar(desde, hasta, estado).stream().map(CitaDto::de).toList());
    }

    Respuesta reservar(Solicitud solicitud) {
        ReservaPeticion peticion = Json.leer(solicitud.cuerpoObjeto(), ReservaPeticion.class);
        peticion.validar();
        return Respuesta.creada(CitaDto.de(citas.reservar(peticion.servicioId(),
                FormatoFechas.fecha("fecha", peticion.fecha()), FormatoFechas.hora("inicio", peticion.inicio()),
                peticion.cliente().aDatos(), null)));
    }

    Respuesta mover(Solicitud solicitud) {
        UUID id = solicitud.parametroUuid("id");
        MoverPeticion peticion = Json.leer(solicitud.cuerpoObjeto(), MoverPeticion.class);
        peticion.validar();
        return Respuesta.ok(CitaDto.de(citas.mover(id, FormatoFechas.fecha("fecha", peticion.fecha()),
                FormatoFechas.hora("inicio", peticion.inicio()))));
    }

    Respuesta cancelar(Solicitud solicitud) {
        return Respuesta.ok(CitaDto.de(citas.cancelar(solicitud.parametroUuid("id"))));
    }

    Respuesta atender(Solicitud solicitud) {
        return Respuesta.ok(CitaDto.de(citas.atender(solicitud.parametroUuid("id"))));
    }

    /** Horas {@code HH:mm} libres; {@code excluirCitaId} libera el horario de esa cita (para moverla). */
    Respuesta horariosLibres(Solicitud solicitud) {
        UUID id = solicitud.parametroUuid("id");
        LocalDate fecha = FormatoFechas.fecha("fecha", solicitud.consulta("fecha").orElse(null));
        UUID excluirCitaId = solicitud.consultaUuid("excluirCitaId");
        return Respuesta.ok(citas.horariosLibres(id, fecha, excluirCitaId).stream()
                .map(h -> h.format(FormatoFechas.HORA)).toList());
    }
}
