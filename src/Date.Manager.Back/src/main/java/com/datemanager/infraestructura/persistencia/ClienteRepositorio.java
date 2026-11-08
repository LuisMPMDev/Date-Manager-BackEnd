package com.datemanager.infraestructura.persistencia;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ClienteRepositorio extends JpaRepository<ClienteEntidad, UUID> {

    /** {@code correo} debe llegar ya normalizado. */
    Optional<ClienteEntidad> findByCorreo(String correo);

    /**
     * Como {@link #findByCorreo}, pero bloquea hasta el fin de la transacción el correo, exista o no (en SQL Server,
     * {@code UPDLOCK, HOLDLOCK} bloquea también el hueco del índice). Dos «buscar o crear» simultáneos con el mismo
     * correo nuevo se ponen en fila: el segundo ve al cliente que creó el primero en vez de chocar con el único.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ClienteEntidad> findWithLockByCorreo(String correo);
}
