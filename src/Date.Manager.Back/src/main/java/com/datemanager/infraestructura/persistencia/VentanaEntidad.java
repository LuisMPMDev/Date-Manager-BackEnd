package com.datemanager.infraestructura.persistencia;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.UUID;

import com.datemanager.dominio.VentanaDisponibilidad;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Fila de {@code ventana_disponibilidad}. Su llave es (servicio, id): el id lo elige el cliente y puede repetirse
 * entre servicios sin que choquen.
 */
@Entity
@Table(name = "ventana_disponibilidad")
@IdClass(VentanaEntidad.Clave.class)
public class VentanaEntidad {

    public record Clave(UUID servicioId, UUID id) implements Serializable {
    }

    @Id
    @Column(name = "servicio_id")
    private UUID servicioId;

    @Id
    private UUID id;

    @Column(nullable = false)
    private int dia;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime inicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime fin;

    protected VentanaEntidad() {
    }

    public VentanaEntidad(UUID servicioId, UUID id, int dia, LocalTime inicio, LocalTime fin) {
        this.servicioId = servicioId;
        this.id = id;
        actualizar(dia, inicio, fin);
    }

    public void actualizar(int dia, LocalTime inicio, LocalTime fin) {
        this.dia = dia;
        this.inicio = inicio;
        this.fin = fin;
    }

    public UUID getServicioId() {
        return servicioId;
    }

    public UUID getId() {
        return id;
    }

    public VentanaDisponibilidad aDominio() {
        return new VentanaDisponibilidad(id, dia, inicio, fin);
    }
}
