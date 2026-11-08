package com.datemanager.dominio;

import java.time.Instant;
import java.time.LocalDateTime;

/** Transiciones de estado de una cita. {@code Atendida} y {@code Cancelada} son estados finales. */
public final class TransicionesCita {

    private TransicionesCita() {
    }

    /** Solo una cita {@code Confirmada} cuyo inicio ya llegó (inicio == ahora vale) pasa a {@code Atendida}. */
    public static Cita atender(Cita cita, LocalDateTime ahora) {
        if (cita.estado() != EstadoCita.Confirmada) {
            throw new ErrorNegocio(CodigoError.ESTADO_INVALIDO, "Solo se puede atender una cita confirmada.");
        }
        if (LocalDateTime.of(cita.fecha(), cita.inicio()).isAfter(ahora)) {
            throw new ErrorNegocio(CodigoError.CITA_NO_HA_INICIADO, "La cita aún no ha iniciado.");
        }
        return new Cita(cita.id(), cita.servicioId(), cita.clienteId(), cita.fecha(), cita.inicio(), cita.fin(),
                EstadoCita.Atendida, cita.creadaEn(), cita.canceladaEn());
    }

    /** Cancelar una cita ya cancelada es idempotente: se devuelve sin cambios. Una atendida no se cancela. */
    public static Cita cancelar(Cita cita, Instant ahora) {
        if (cita.estado() == EstadoCita.Cancelada) {
            return cita;
        }
        if (cita.estado() == EstadoCita.Atendida) {
            throw new ErrorNegocio(CodigoError.ESTADO_INVALIDO, "No se puede cancelar una cita ya atendida.");
        }
        return new Cita(cita.id(), cita.servicioId(), cita.clienteId(), cita.fecha(), cita.inicio(), cita.fin(),
                EstadoCita.Cancelada, cita.creadaEn(), ahora);
    }

    /** Solo una cita {@code Confirmada} puede moverse a otro horario. */
    public static void validarMovible(Cita cita) {
        if (cita.estado() != EstadoCita.Confirmada) {
            throw new ErrorNegocio(CodigoError.ESTADO_INVALIDO, "Solo se puede mover una cita confirmada.");
        }
    }
}
