package com.datemanager.infraestructura.persistencia;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnlaceRepositorio extends JpaRepository<EnlaceEntidad, UUID> {

    /** Por el índice único de {@code codigo} (intercalación binaria: distingue mayúsculas). */
    Optional<EnlaceEntidad> findByCodigo(String codigo);

    /** Los más nuevos primero. */
    List<EnlaceEntidad> findAllByOrderByCreadoEnUtcDescIdDesc();

    boolean existsByCreadoPor(UUID creadoPor);

    /**
     * Gasta un uso solo si el enlace sigue activo en {@code ahoraUtc}, en una única sentencia: la fila queda
     * bloqueada hasta el fin de la transacción, así que otra reserva simultánea espera y ya ve el uso gastado.
     * Devuelve las filas cambiadas (0 = el enlace ya no vale). Limpia el contexto de persistencia para que una
     * lectura posterior del enlace venga de la base.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE enlace_invitacion SET usos = usos + 1
            WHERE id = :id AND desactivado_en IS NULL AND vence_en > :ahora
              AND (max_usos IS NULL OR usos < max_usos)
            """, nativeQuery = true)
    int consumirUso(@Param("id") UUID id, @Param("ahora") LocalDateTime ahoraUtc);

    /** Desactiva si no lo estaba (idempotente: conserva la primera fecha). Devuelve las filas cambiadas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE enlace_invitacion SET desactivado_en = :ahora
            WHERE id = :id AND desactivado_en IS NULL
            """, nativeQuery = true)
    int desactivar(@Param("id") UUID id, @Param("ahora") LocalDateTime ahoraUtc);
}
