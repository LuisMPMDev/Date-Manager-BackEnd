package com.datemanager.infraestructura.persistencia;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicioRepositorio extends JpaRepository<ServicioEntidad, UUID> {
}
