package com.datemanager.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.datemanager.aplicacion.CitasServicio;
import com.datemanager.dominio.EstadoCita;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Citas del panel y horarios libres de un servicio. Sin reglas: delega en {@link CitasServicio}. */
@RestController
@RequestMapping("/api/panel")
public class CitasControlador {

    private final CitasServicio citas;

    public CitasControlador(CitasServicio citas) {
        this.citas = citas;
    }

    @GetMapping("/citas")
    @PreAuthorize("@permisos.tiene('ver_panel', 'ver_citas')")
    public List<CitaDto> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoCita estado) {
        return citas.listar(desde, hasta, estado).stream().map(CitaDto::de).toList();
    }

    @PostMapping("/citas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permisos.tiene('gestionar_citas')")
    public CitaDto reservar(@Valid @RequestBody ReservaPeticion peticion) {
        return CitaDto.de(citas.reservar(peticion.servicioId(), FormatoFechas.fecha("fecha", peticion.fecha()),
                FormatoFechas.hora("inicio", peticion.inicio()), peticion.cliente().aDatos(), null));
    }

    @PostMapping("/citas/{id}/mover")
    @PreAuthorize("@permisos.tiene('gestionar_citas')")
    public CitaDto mover(@PathVariable UUID id, @Valid @RequestBody MoverPeticion peticion) {
        return CitaDto.de(citas.mover(id, FormatoFechas.fecha("fecha", peticion.fecha()),
                FormatoFechas.hora("inicio", peticion.inicio())));
    }

    @PostMapping("/citas/{id}/cancelar")
    @PreAuthorize("@permisos.tiene('gestionar_citas')")
    public CitaDto cancelar(@PathVariable UUID id) {
        return CitaDto.de(citas.cancelar(id));
    }

    @PostMapping("/citas/{id}/atender")
    @PreAuthorize("@permisos.tiene('atender_citas')")
    public CitaDto atender(@PathVariable UUID id) {
        return CitaDto.de(citas.atender(id));
    }

    /** Horas {@code HH:mm} libres; {@code excluirCitaId} libera el horario de esa cita (para moverla). */
    @GetMapping("/servicios/{id}/horarios")
    @PreAuthorize("@permisos.tiene('gestionar_citas')")
    public List<String> horariosLibres(@PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) UUID excluirCitaId) {
        return citas.horariosLibres(id, fecha, excluirCitaId).stream()
                .map(h -> h.format(FormatoFechas.HORA)).toList();
    }
}
