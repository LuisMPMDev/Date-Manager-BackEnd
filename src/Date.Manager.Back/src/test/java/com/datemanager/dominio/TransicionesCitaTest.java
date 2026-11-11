package com.datemanager.dominio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransicionesCitaTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 9);
    private static final LocalDateTime INICIO = LocalDateTime.of(FECHA, LocalTime.of(10, 0));
    private static final Instant AHORA = Instant.parse("2026-11-09T16:00:00Z");

    private static Cita cita(EstadoCita estado, Instant canceladaEn) {
        return new Cita(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), FECHA,
                LocalTime.of(10, 0), LocalTime.of(11, 0), estado, Instant.parse("2026-11-01T12:00:00Z"), canceladaEn);
    }

    private static void assertError(Runnable accion, CodigoError esperado) {
        ErrorNegocio e = assertThrows(ErrorNegocio.class, accion::run);
        assertEquals(esperado, e.codigo());
        assertNotNull(e.getMessage());
        assertFalse(e.getMessage().isBlank());
    }

    @Test
    void atenderUnaConfirmadaYaIniciadaLaMarcaAtendida() {
        Cita original = cita(EstadoCita.Confirmada, null);

        Cita atendida = TransicionesCita.atender(original, INICIO.plusMinutes(5));

        assertEquals(EstadoCita.Atendida, atendida.estado());
        assertEquals(new Cita(original.id(), original.servicioId(), original.clienteId(), original.fecha(),
                original.inicio(), original.fin(), EstadoCita.Atendida, original.creadaEn(), original.canceladaEn()),
                atendida);
    }

    @Test
    void atenderEnElInstanteExactoDeInicioEsValido() {
        assertEquals(EstadoCita.Atendida, TransicionesCita.atender(cita(EstadoCita.Confirmada, null), INICIO).estado());
    }

    @Test
    void atenderUnaCitaFuturaFalla() {
        assertError(() -> TransicionesCita.atender(cita(EstadoCita.Confirmada, null), INICIO.minusNanos(1)),
                CodigoError.CITA_NO_HA_INICIADO);
        assertError(() -> TransicionesCita.atender(cita(EstadoCita.Confirmada, null), INICIO.minusDays(1)),
                CodigoError.CITA_NO_HA_INICIADO);
    }

    @Test
    void atenderUnaCanceladaOUnaAtendidaFallaPorEstado() {
        assertError(() -> TransicionesCita.atender(cita(EstadoCita.Cancelada, AHORA), INICIO.plusHours(1)),
                CodigoError.ESTADO_INVALIDO);
        assertError(() -> TransicionesCita.atender(cita(EstadoCita.Atendida, null), INICIO.plusHours(1)),
                CodigoError.ESTADO_INVALIDO);
    }

    @Test
    void elEstadoInvalidoTienePrioridadSobreNoHaIniciado() {
        assertError(() -> TransicionesCita.atender(cita(EstadoCita.Cancelada, AHORA), INICIO.minusDays(1)),
                CodigoError.ESTADO_INVALIDO);
    }

    @Test
    void cancelarUnaConfirmadaLaCancelaYRegistraCuando() {
        Cita original = cita(EstadoCita.Confirmada, null);

        Cita cancelada = TransicionesCita.cancelar(original, AHORA);

        assertEquals(EstadoCita.Cancelada, cancelada.estado());
        assertEquals(AHORA, cancelada.canceladaEn());
        assertEquals(new Cita(original.id(), original.servicioId(), original.clienteId(), original.fecha(),
                original.inicio(), original.fin(), EstadoCita.Cancelada, original.creadaEn(), AHORA), cancelada);
    }

    @Test
    void cancelarUnaYaCanceladaLaDevuelveIgualSinError() {
        Cita yaCancelada = cita(EstadoCita.Cancelada, Instant.parse("2026-11-05T09:00:00Z"));

        assertSame(yaCancelada, TransicionesCita.cancelar(yaCancelada, AHORA));
    }

    @Test
    void noSePuedeCancelarUnaAtendida() {
        assertError(() -> TransicionesCita.cancelar(cita(EstadoCita.Atendida, null), AHORA),
                CodigoError.ESTADO_INVALIDO);
    }

    @Test
    void soloUnaConfirmadaEsMovible() {
        assertDoesNotThrow(() -> TransicionesCita.validarMovible(cita(EstadoCita.Confirmada, null)));
        assertError(() -> TransicionesCita.validarMovible(cita(EstadoCita.Cancelada, AHORA)), CodigoError.ESTADO_INVALIDO);
        assertError(() -> TransicionesCita.validarMovible(cita(EstadoCita.Atendida, null)), CodigoError.ESTADO_INVALIDO);
    }
}
