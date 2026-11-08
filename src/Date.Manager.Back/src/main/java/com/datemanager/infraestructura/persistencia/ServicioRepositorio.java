package com.datemanager.infraestructura.persistencia;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicioRepositorio extends JpaRepository<ServicioEntidad, UUID> {

    /**
     * El servicio, bloqueado para escritura hasta el fin de la transacción ({@code UPDLOCK, HOLDLOCK} en SQL Server).
     * Es el bloqueo de la agenda del servicio: reservar, mover y guardar el servicio lo toman siempre primero.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ServicioEntidad s where s.id = :id")
    Optional<ServicioEntidad> bloquear(@Param("id") UUID id);
}
