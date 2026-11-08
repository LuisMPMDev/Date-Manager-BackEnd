package com.datemanager.infraestructura.persistencia;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** Fila de {@code rol} con sus permisos ({@code rol_permiso}). Se traduce a {@link Rol} con {@link #aDominio()}. */
@Entity
@Table(name = "rol")
public class RolEntidad {

    /** Asignado por la aplicación: el panel crea roles con el id que elige el cliente (PUT con upsert). */
    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rol_permiso", joinColumns = @JoinColumn(name = "rol_id"))
    @Column(name = "permiso", nullable = false, length = 40)
    @Convert(converter = PermisoConvertidor.class)
    private Set<Permiso> permisos = new HashSet<>();

    protected RolEntidad() {
    }

    public RolEntidad(String nombre, String descripcion, Set<Permiso> permisos) {
        this(UUID.randomUUID(), nombre, descripcion, permisos);
    }

    public RolEntidad(UUID id, String nombre, String descripcion, Set<Permiso> permisos) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.permisos = new HashSet<>(permisos);
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Set<Permiso> getPermisos() {
        return permisos.isEmpty() ? EnumSet.noneOf(Permiso.class) : EnumSet.copyOf(permisos);
    }

    public void setPermisos(Set<Permiso> permisos) {
        this.permisos.clear();
        this.permisos.addAll(permisos);
    }

    public Rol aDominio() {
        return new Rol(id, nombre, descripcion, permisos);
    }
}
