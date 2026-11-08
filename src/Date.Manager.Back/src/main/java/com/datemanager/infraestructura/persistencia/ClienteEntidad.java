package com.datemanager.infraestructura.persistencia;

import java.util.UUID;

import com.datemanager.dominio.Cliente;
import com.datemanager.dominio.Validaciones;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code cliente}. El correo se guarda siempre normalizado. */
@Entity
@Table(name = "cliente")
public class ClienteEntidad {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, length = 40)
    private String telefono;

    @Column(nullable = false, length = 254, unique = true)
    private String correo;

    protected ClienteEntidad() {
    }

    public ClienteEntidad(UUID id, String nombre, String apellido, String telefono, String correo) {
        this.id = id;
        actualizar(nombre, apellido, telefono, correo);
    }

    /** Se normaliza aquí para que ningún camino guarde un correo sin normalizar. */
    public void actualizar(String nombre, String apellido, String telefono, String correo) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.telefono = telefono;
        this.correo = Validaciones.normalizarCorreo(correo);
    }

    public UUID getId() {
        return id;
    }

    public Cliente aDominio() {
        return new Cliente(id, nombre, apellido, telefono, correo);
    }
}
