package com.datemanager.infraestructura.persistencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CitaRepositorio extends JpaRepository<CitaEntidad, UUID> {

    /** Citas (de cualquier estado) de un servicio en un día. */
    List<CitaEntidad> findByServicioIdAndFecha(UUID servicioId, LocalDate fecha);

    List<CitaEntidad> findByFechaBetweenOrderByFechaAscInicioAscIdAsc(LocalDate desde, LocalDate hasta);

    List<CitaEntidad> findByFechaBetweenAndEstadoOrderByFechaAscInicioAscIdAsc(LocalDate desde, LocalDate hasta,
            String estado);

    /** Servicio de una cita, sin cargarla (no cambia nunca: sirve para bloquear el servicio antes que la cita). */
    @Query("select c.servicioId from CitaEntidad c where c.id = :id")
    Optional<UUID> servicioDe(@Param("id") UUID id);

    /** La cita, bloqueada para escritura hasta el fin de la transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CitaEntidad c where c.id = :id")
    Optional<CitaEntidad> bloquear(@Param("id") UUID id);
}
