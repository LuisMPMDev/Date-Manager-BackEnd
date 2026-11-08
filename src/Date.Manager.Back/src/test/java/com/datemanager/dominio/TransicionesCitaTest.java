package com.datemanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ErrorNegocio.class, e -> {
            assertThat(e.codigo()).isEqualTo(esperado);
            assertThat(e.getMessage()).isNotBlank();
        });
    }

    @Test
    void atenderUnaConfirmadaYaIniciadaLaMarcaAtendida() {
        Cita original = cita(EstadoCita.Confirmada, null);

        Cita atendida = TransicionesCita.atender(original, INICIO.plusMinutes(5));

        assertThat(atendida.estado()).isEqualTo(EstadoCita.Atendida);
        assertThat(atendida).usingRecursiveComparison().ignoringFields("estado").isEqualTo(original);
    }

    @Test
    void atenderEnElInstanteExactoDeInicioEsValido() {
        assertThat(TransicionesCita.atender(cita(EstadoCita.Confirmada, null), INICIO).estado())
                .isEqualTo(EstadoCita.Atendida);
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

        assertThat(cancelada.estado()).isEqualTo(EstadoCita.Cancelada);
        assertThat(cancelada.canceladaEn()).isEqualTo(AHORA);
        assertThat(cancelada).usingRecursiveComparison().ignoringFields("estado", "canceladaEn").isEqualTo(original);
    }

    @Test
    void cancelarUnaYaCanceladaLaDevuelveIgualSinError() {
        Cita yaCancelada = cita(EstadoCita.Cancelada, Instant.parse("2026-11-05T09:00:00Z"));

        assertThat(TransicionesCita.cancelar(yaCancelada, AHORA)).isSameAs(yaCancelada);
    }

    @Test
    void noSePuedeCancelarUnaAtendida() {
        assertError(() -> TransicionesCita.cancelar(cita(EstadoCita.Atendida, null), AHORA),
                CodigoError.ESTADO_INVALIDO);
    }

    @Test
    void soloUnaConfirmadaEsMovible() {
        assertThatCode(() -> TransicionesCita.validarMovible(cita(EstadoCita.Confirmada, null))).doesNotThrowAnyException();
        assertError(() -> TransicionesCita.validarMovible(cita(EstadoCita.Cancelada, AHORA)), CodigoError.ESTADO_INVALIDO);
        assertError(() -> TransicionesCita.validarMovible(cita(EstadoCita.Atendida, null)), CodigoError.ESTADO_INVALIDO);
    }
}
