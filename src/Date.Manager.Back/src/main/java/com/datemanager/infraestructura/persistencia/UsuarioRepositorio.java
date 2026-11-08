package com.datemanager.infraestructura.persistencia;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepositorio extends JpaRepository<UsuarioEntidad, UUID> {

    /** {@code usuario} debe llegar ya normalizado ({@code Validaciones.normalizarUsuario}). */
    Optional<UsuarioEntidad> findByUsuario(String usuario);
}
