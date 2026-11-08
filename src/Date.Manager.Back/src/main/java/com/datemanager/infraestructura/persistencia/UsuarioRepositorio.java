package com.datemanager.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepositorio extends JpaRepository<UsuarioEntidad, UUID> {

    /** {@code usuario} debe llegar ya normalizado ({@code Validaciones.normalizarUsuario}). */
    Optional<UsuarioEntidad> findByUsuario(String usuario);

    List<UsuarioEntidad> findByRolId(UUID rolId);

    /** Usuarios distintos de {@code id} cuyo rol está en {@code rolIds} (la colección no puede ir vacía). */
    long countByIdNotAndRolIdIn(UUID id, Collection<UUID> rolIds);
}
