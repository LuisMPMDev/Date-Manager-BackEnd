package com.datemanager.infraestructura.persistencia;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.datemanager.dominio.Enlace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de {@code enlace_invitacion}. Los instantes se guardan como hora UTC sin zona ({@code DATETIME2}).
 * Ninguna columna se actualiza a través de la entidad ({@code updatable = false}): {@code usos} y
 * {@code desactivado_en} solo cambian con los UPDATE condicionales de {@link EnlaceRepositorio}, así un guardado de
 * la entidad nunca puede pisar un uso consumido por otra transacción.
 */
@Entity
@Table(name = "enlace_invitacion")
public class EnlaceEntidad {

    @Id
    private UUID id;

    @Column(nullable = false, length = 43, updatable = false)
    private String codigo;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private UUID creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEnUtc;

    @Column(name = "vence_en", nullable = false, updatable = false)
    private LocalDateTime venceEnUtc;

    @Column(name = "max_usos", updatable = false)
    private Integer maxUsos;

    @Column(nullable = false, updatable = false)
    private int usos;

    @Column(name = "desactivado_en", updatable = false)
    private LocalDateTime desactivadoEnUtc;

    protected EnlaceEntidad() {
    }

    public static EnlaceEntidad de(Enlace enlace) {
        EnlaceEntidad e = new EnlaceEntidad();
        e.id = enlace.id();
        e.codigo = enlace.codigo();
        e.creadoPor = enlace.creadoPor();
        e.creadoEnUtc = aUtc(enlace.creadoEn());
        e.venceEnUtc = aUtc(enlace.venceEn());
        e.maxUsos = enlace.maxUsos();
        e.usos = enlace.usos();
        e.desactivadoEnUtc = aUtc(enlace.desactivadoEn());
        return e;
    }

    public Enlace aDominio() {
        return new Enlace(id, codigo, creadoPor, deUtc(creadoEnUtc), deUtc(venceEnUtc), maxUsos, usos,
                deUtc(desactivadoEnUtc));
    }

    public UUID getId() {
        return id;
    }

    public UUID getCreadoPor() {
        return creadoPor;
    }

    static LocalDateTime aUtc(Instant instante) {
        return instante == null ? null : LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static Instant deUtc(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
