package com.datemanager.infraestructura.persistencia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
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

    /** Citas del rango cuyo estado no es {@code estado} (el tablero ignora las canceladas). */
    List<CitaEntidad> findByFechaBetweenAndEstadoNot(LocalDate desde, LocalDate hasta, String estado);

    /** Cuántas citas se crearon en {@code (desde, hasta]} (hora UTC sin zona, como se guardan). */
    @Query("select count(c) from CitaEntidad c where c.creadaEnUtc > :desde and c.creadaEnUtc <= :hasta")
    long contarCreadas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Cuántas citas se cancelaron en {@code (desde, hasta]} (hora UTC sin zona). */
    @Query("select count(c) from CitaEntidad c where c.canceladaEnUtc > :desde and c.canceladaEnUtc <= :hasta")
    long contarCanceladas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Citas creadas o canceladas en {@code (desde, hasta]}: lo único que puede dar una alerta de cita. */
    @Query("""
            select c from CitaEntidad c
            where (c.creadaEnUtc > :desde and c.creadaEnUtc <= :hasta)
               or (c.canceladaEnUtc > :desde and c.canceladaEnUtc <= :hasta)
            """)
    List<CitaEntidad> creadasOCanceladasEntre(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    /** Citas de un estado en un día (el tablero las filtra por hora en el dominio: la hora no viaja como parámetro). */
    List<CitaEntidad> findByFechaAndEstado(LocalDate fecha, String estado);

    /** Las primeras citas de un estado después de un día, de la más cercana a la más lejana. */
    List<CitaEntidad> findByFechaGreaterThanAndEstadoOrderByFechaAscInicioAscIdAsc(LocalDate fecha, String estado,
            Pageable limite);

    /** Servicio de una cita, sin cargarla (no cambia nunca: sirve para bloquear el servicio antes que la cita). */
    @Query("select c.servicioId from CitaEntidad c where c.id = :id")
    Optional<UUID> servicioDe(@Param("id") UUID id);

    /** La cita, bloqueada para escritura hasta el fin de la transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CitaEntidad c where c.id = :id")
    Optional<CitaEntidad> bloquear(@Param("id") UUID id);
}
