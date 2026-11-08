package com.datemanager.infraestructura.persistencia;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.datemanager.dominio.Cita;
import com.datemanager.dominio.EstadoCita;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de {@code cita}. Los instantes se guardan como hora UTC sin zona ({@code DATETIME2}); así no dependen de la
 * zona de la JVM ni del driver.
 */
@Entity
@Table(name = "cita")
public class CitaEntidad {

    @Id
    private UUID id;

    @Column(name = "servicio_id", nullable = false)
    private UUID servicioId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime inicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime fin;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEnUtc;

    @Column(name = "cancelada_en")
    private LocalDateTime canceladaEnUtc;

    @Column(name = "enlace_id")
    private UUID enlaceId;

    protected CitaEntidad() {
    }

    /** Nueva fila con los datos de {@code cita} y el enlace con que se reservó (o {@code null}). */
    public static CitaEntidad de(Cita cita, UUID enlaceId) {
        CitaEntidad entidad = new CitaEntidad();
        entidad.id = cita.id();
        entidad.servicioId = cita.servicioId();
        entidad.clienteId = cita.clienteId();
        entidad.enlaceId = enlaceId;
        entidad.copiar(cita);
        return entidad;
    }

    /** Copia lo que puede cambiar de una cita (horario, estado, cancelación). */
    public void copiar(Cita cita) {
        this.fecha = cita.fecha();
        this.inicio = cita.inicio();
        this.fin = cita.fin();
        this.estado = cita.estado().name();
        this.creadaEnUtc = aUtc(cita.creadaEn());
        this.canceladaEnUtc = aUtc(cita.canceladaEn());
    }

    public Cita aDominio() {
        return new Cita(id, servicioId, clienteId, fecha, inicio, fin, EstadoCita.valueOf(estado),
                deUtc(creadaEnUtc), deUtc(canceladaEnUtc));
    }

    private static LocalDateTime aUtc(Instant instante) {
        return instante == null ? null : LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static Instant deUtc(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
