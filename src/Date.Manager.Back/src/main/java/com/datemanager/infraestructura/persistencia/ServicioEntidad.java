package com.datemanager.infraestructura.persistencia;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.VentanaDisponibilidad;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code servicio}. Las ventanas viven en {@link VentanaEntidad}; se unen con {@link #aDominio(List)}. */
@Entity
@Table(name = "servicio")
public class ServicioEntidad {

    /** Asignado por la aplicación: el panel crea servicios con el id que elige el cliente (PUT con upsert). */
    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "duracion_min", nullable = false)
    private int duracionMin;

    @Column(nullable = false)
    private boolean activo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    protected ServicioEntidad() {
    }

    public ServicioEntidad(UUID id, String nombre, int duracionMin, boolean activo, BigDecimal valor) {
        this.id = id;
        actualizar(nombre, duracionMin, activo, valor);
    }

    public void actualizar(String nombre, int duracionMin, boolean activo, BigDecimal valor) {
        this.nombre = nombre;
        this.duracionMin = duracionMin;
        this.activo = activo;
        this.valor = valor;
    }

    public UUID getId() {
        return id;
    }

    public Servicio aDominio(List<VentanaDisponibilidad> ventanas) {
        return new Servicio(id, nombre, duracionMin, activo, valor, ventanas);
    }
}
