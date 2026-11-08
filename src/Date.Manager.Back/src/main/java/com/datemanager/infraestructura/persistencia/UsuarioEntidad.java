package com.datemanager.infraestructura.persistencia;

import java.util.UUID;

import com.datemanager.dominio.Usuario;
import com.datemanager.dominio.Validaciones;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de {@code usuario}. El nombre de usuario se guarda siempre normalizado y la clave solo como hash;
 * {@link #aDominio()} entrega el {@link Usuario} sin el hash.
 */
@Entity
@Table(name = "usuario")
public class UsuarioEntidad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, length = 254)
    private String correo;

    @Column(nullable = false, length = 40)
    private String telefono;

    @Column(nullable = false, length = 100, unique = true)
    private String usuario;

    @Column(name = "clave_hash", nullable = false, length = 100)
    private String claveHash;

    @Column(name = "rol_id", nullable = false)
    private UUID rolId;

    protected UsuarioEntidad() {
    }

    public UsuarioEntidad(String nombre, String apellido, String correo, String telefono, String usuario,
            String claveHash, UUID rolId) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.correo = correo;
        this.telefono = telefono;
        setUsuario(usuario);
        this.claveHash = claveHash;
        this.rolId = rolId;
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

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getUsuario() {
        return usuario;
    }

    /** Se normaliza aquí para que ningún camino guarde un usuario sin normalizar. */
    public void setUsuario(String usuario) {
        this.usuario = Validaciones.normalizarUsuario(usuario);
    }

    public String getClaveHash() {
        return claveHash;
    }

    public void setClaveHash(String claveHash) {
        this.claveHash = claveHash;
    }

    public UUID getRolId() {
        return rolId;
    }

    public void setRolId(UUID rolId) {
        this.rolId = rolId;
    }

    public Usuario aDominio() {
        return new Usuario(id, nombre, apellido, correo, telefono, usuario, rolId);
    }
}
