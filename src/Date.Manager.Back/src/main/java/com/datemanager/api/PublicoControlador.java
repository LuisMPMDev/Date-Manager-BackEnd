package com.datemanager.api;

import java.util.List;
import java.util.UUID;

import com.datemanager.aplicacion.EnlacesServicio;
import com.datemanager.aplicacion.EnlacesServicio.CitaReservada;
import com.datemanager.aplicacion.EnlacesServicio.EnlaceVista;
import com.datemanager.dominio.Cita;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Zona del cliente: sin token, solo con el código del enlace. Solo expone el estado del enlace, los servicios
 * activos, los horarios libres y la cita recién creada (nunca datos de clientes). Sin reglas: delega en
 * {@link EnlacesServicio}.
 */
@RestController
@RequestMapping("/api/publico/invitaciones/{codigo}")
public class PublicoControlador {

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
        this.enlaces = enlaces;
    }

    @GetMapping
    public EstadoInvitacionDto estado(@PathVariable String codigo) {
        return EstadoInvitacionDto.de(enlaces.estado(codigo));
    }

    @GetMapping("/servicios")
    public List<ServicioDto> servicios(@PathVariable String codigo) {
        return enlaces.serviciosActivos(codigo).stream().map(ServicioDto::de).toList();
    }

    @GetMapping("/servicios/{servicioId}/horarios")
    public List<String> horarios(@PathVariable String codigo, @PathVariable UUID servicioId,
            @RequestParam String fecha) {
        return enlaces.horariosLibres(codigo, servicioId, FormatoFechas.fecha("fecha", fecha)).stream()
                .map(h -> h.format(FormatoFechas.HORA)).toList();
    }

    @PostMapping("/citas")
    @ResponseStatus(HttpStatus.CREATED)
    public CitaPublicaDto reservar(@PathVariable String codigo, @Valid @RequestBody ReservaPeticion peticion) {
        return CitaPublicaDto.de(enlaces.reservar(codigo, peticion.servicioId(),
                FormatoFechas.fecha("fecha", peticion.fecha()), FormatoFechas.hora("inicio", peticion.inicio()),
                peticion.cliente().aDatos()));
    }
}
