package com.datemanager.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.datemanager.aplicacion.EnlacesServicio;
import com.datemanager.aplicacion.EnlacesServicio.CitaReservada;
import com.datemanager.aplicacion.EnlacesServicio.EnlaceVista;
import com.datemanager.dominio.Cita;
import com.datemanager.infraestructura.http.DocumentoRuta;
import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.http.Solicitud;

/**
 * Zona del cliente: sin token, solo con el código del enlace (las rutas son públicas: ni siquiera se lee la cabecera
 * {@code Authorization}, así que un token vencido no estorba). Solo expone el estado del enlace, los servicios
 * activos, los horarios libres y la cita recién creada (nunca datos de clientes). Sin reglas: delega en
 * {@link EnlacesServicio}.
 */
public final class PublicoControlador {

    private static final String ETIQUETA = "Zona del cliente";
    private static final String BASE = "/api/publico/invitaciones/{codigo}";

    /** Lo único que se dice de un enlace en la zona del cliente. */
    public record EstadoInvitacionDto(String estado, String venceEn) {

        static EstadoInvitacionDto de(EnlaceVista vista) {
            return new EstadoInvitacionDto(vista.estado().name(), vista.enlace().venceEn().toString());
        }
    }

    /** La cita recién reservada, sin ids de cliente ni datos personales. */
    public record CitaPublicaDto(UUID servicioId, String servicio, String fecha, String inicio, String fin,
            String estado) {

        static CitaPublicaDto de(CitaReservada reservada) {
            Cita c = reservada.cita();
            return new CitaPublicaDto(c.servicioId(), reservada.servicio(), c.fecha().format(FormatoFechas.FECHA),
                    c.inicio().format(FormatoFechas.HORA), c.fin().format(FormatoFechas.HORA), c.estado().name());
        }
    }

    private final EnlacesServicio enlaces;

    public PublicoControlador(EnlacesServicio enlaces) {
        this.enlaces = Objects.requireNonNull(enlaces);
    }

    public List<Ruta> rutas() {
        return List.of(
                new Ruta("GET", BASE, Set.of(), true, this::estado,
                        new DocumentoRuta(ETIQUETA, "Estado y vencimiento de un enlace", null,
                                EstadoInvitacionDto.class, false, 200)),
                new Ruta("GET", BASE + "/servicios", Set.of(), true, this::servicios,
                        new DocumentoRuta(ETIQUETA, "Servicios activos", null, ServicioDto.class, true, 200)),
                new Ruta("GET", BASE + "/servicios/{servicioId}/horarios", Set.of(), true, this::horarios,
                        new DocumentoRuta(ETIQUETA, "Horas HH:mm libres de un servicio en una fecha", null,
                                String.class, true, 200)),
                new Ruta("POST", BASE + "/citas", Set.of(), true, this::reservar,
                        new DocumentoRuta(ETIQUETA, "Reserva una cita con el enlace", ReservaPeticion.class,
                                CitaPublicaDto.class, false, 201)));
    }

    Respuesta estado(Solicitud solicitud) {
        return Respuesta.ok(EstadoInvitacionDto.de(enlaces.estado(solicitud.parametro("codigo"))));
    }

    Respuesta servicios(Solicitud solicitud) {
        return Respuesta.ok(enlaces.serviciosActivos(solicitud.parametro("codigo")).stream()
                .map(ServicioDto::de).toList());
    }

    Respuesta horarios(Solicitud solicitud) {
        String codigo = solicitud.parametro("codigo");
        UUID servicioId = solicitud.parametroUuid("servicioId");
        return Respuesta.ok(enlaces.horariosLibres(codigo, servicioId,
                FormatoFechas.fecha("fecha", solicitud.consulta("fecha").orElse(null))).stream()
                .map(h -> h.format(FormatoFechas.HORA)).toList());
    }

    Respuesta reservar(Solicitud solicitud) {
        String codigo = solicitud.parametro("codigo");
        ReservaPeticion peticion = Json.leer(solicitud.cuerpoObjeto(), ReservaPeticion.class);
        peticion.validar();
        return Respuesta.creada(CitaPublicaDto.de(enlaces.reservar(codigo, peticion.servicioId(),
                FormatoFechas.fecha("fecha", peticion.fecha()), FormatoFechas.hora("inicio", peticion.inicio()),
                peticion.cliente().aDatos())));
    }
}
