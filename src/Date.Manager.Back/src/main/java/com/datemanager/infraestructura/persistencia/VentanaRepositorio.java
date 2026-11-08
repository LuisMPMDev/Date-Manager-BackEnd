package com.datemanager.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VentanaRepositorio extends JpaRepository<VentanaEntidad, VentanaEntidad.Clave> {

    /** Ventanas del servicio por día y luego hora de inicio. */
    List<VentanaEntidad> findByServicioIdOrderByDiaAscInicioAscIdAsc(UUID servicioId);

    /** Ventanas de varios servicios (la colección no puede ir vacía), ya ordenadas por día e inicio. */
    List<VentanaEntidad> findByServicioIdInOrderByDiaAscInicioAscIdAsc(Collection<UUID> servicioIds);
}
