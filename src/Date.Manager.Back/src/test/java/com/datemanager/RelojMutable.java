package com.datemanager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Reloj de pruebas: por defecto marca la hora real, y {@link #fijarAhora(LocalDateTime)} lo congela
 * en una hora de pared de la zona del negocio.
 */
public class RelojMutable extends Clock {

    private final ZoneId zona;
    private volatile Instant fijo;

    public RelojMutable(ZoneId zona) {
        this.zona = zona;
    }

    /** Congela "ahora" en esa hora de pared interpretada en la zona del negocio. */
    public void fijarAhora(LocalDateTime ahora) {
        this.fijo = ahora.atZone(zona).toInstant();
    }

    /** Vuelve a la hora real. */
    public void restablecer() {
        this.fijo = null;
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId otraZona) {
        return fijo == null ? Clock.system(otraZona) : Clock.fixed(fijo, otraZona);
    }

    @Override
    public Instant instant() {
        Instant congelado = fijo;
        return congelado != null ? congelado : Instant.now();
    }
}
