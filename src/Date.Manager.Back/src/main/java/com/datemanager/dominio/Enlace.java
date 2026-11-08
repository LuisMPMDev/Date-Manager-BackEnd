package com.datemanager.dominio;

import java.time.Instant;
import java.util.UUID;

/**
 * Enlace de invitación. Los instantes son UTC. {@code maxUsos} nulo = sin límite; {@code desactivadoEn} nulo =
 * no desactivado. El código es secreto: quien lo tiene puede agendar.
 */
public record Enlace(
        UUID id,
        String codigo,
        UUID creadoPor,
        Instant creadoEn,
        Instant venceEn,
        Integer maxUsos,
        int usos,
        Instant desactivadoEn) {

    /** Sin el código, para que no acabe en un log por accidente. */
    @Override
    public String toString() {
        return "Enlace[id=" + id + ", venceEn=" + venceEn + ", maxUsos=" + maxUsos + ", usos=" + usos
                + ", desactivadoEn=" + desactivadoEn + "]";
    }
}
