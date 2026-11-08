package com.datemanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

import com.datemanager.dominio.Metricas.Alerta;
import com.datemanager.dominio.Metricas.CitasDia;
import com.datemanager.dominio.Metricas.OcupacionServicio;
import com.datemanager.dominio.Metricas.TipoAlerta;
import org.junit.jupiter.api.Test;

/** Porta {@code core/metricas.spec.ts} y los casos de {@code panel-kpis.spec.ts} / {@code panel-datos.spec.ts}. */
class MetricasTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 9);
    private static final Instant AHORA = Instant.parse("2026-11-09T17:00:00Z");
    private static final UUID S1 = UUID.randomUUID();
    private static final UUID OTRO = UUID.randomUUID();
    private static final Instant CREADA = Instant.parse("2026-11-01T10:00:00Z");

    private static Servicio servicio(UUID id, boolean activo, List<VentanaDisponibilidad> ventanas) {
        return new Servicio(id, "Corte", 60, activo, new BigDecimal("10000.00"), ventanas);
    }

    /** Lunes de 09:00 a 13:00 (240 min), 10 000 por cita. */
    private static Servicio servicio() {
        return servicio(S1, true, List.of(ventana(1, "09:00", "13:00")));
    }

    private static VentanaDisponibilidad ventana(int dia, String inicio, String fin) {
        return new VentanaDisponibilidad(UUID.randomUUID(), dia, LocalTime.parse(inicio), LocalTime.parse(fin));
    }

    private static Cita cita(LocalDate fecha, String inicio, String fin, EstadoCita estado) {
        return new Cita(UUID.randomUUID(), S1, UUID.randomUUID(), fecha, LocalTime.parse(inicio),
                LocalTime.parse(fin), estado, CREADA, estado == EstadoCita.Cancelada ? CREADA : null);
    }

    private static Cita cita() {
        return cita(LUNES, "09:00", "10:00", EstadoCita.Confirmada);
    }

    private static Cita conServicio(Cita c, UUID servicioId) {
        return new Cita(c.id(), servicioId, c.clienteId(), c.fecha(), c.inicio(), c.fin(), c.estado(), c.creadaEn(),
                c.canceladaEn());
    }

    private static Cita conCreada(Cita c, Instant creadaEn) {
        return new Cita(c.id(), c.servicioId(), c.clienteId(), c.fecha(), c.inicio(), c.fin(), c.estado(), creadaEn,
                c.canceladaEn());
    }

    private static Cita cancelada(Instant canceladaEn) {
        Cita c = cita();
        return new Cita(c.id(), c.servicioId(), c.clienteId(), c.fecha(), c.inicio(), c.fin(), EstadoCita.Cancelada,
                c.creadaEn(), canceladaEn);
    }

    // ---------- ocupacion ----------

    @Test
    void sesentaMinutosDeCitaSobreDoscientosCuarentaDeVentanaEs025() {
        assertThat(Metricas.ocupacion(List.of(servicio()), List.of(cita()), LUNES)).isEqualTo(0.25);
    }

    @Test
    void unaCitaCanceladaNoCuenta() {
        assertThat(Metricas.ocupacion(List.of(servicio()), List.of(cancelada(CREADA)), LUNES)).isEqualTo(0);
    }

    @Test
    void unaCitaAtendidaCuenta() {
        Cita atendida = cita(LUNES, "09:00", "10:00", EstadoCita.Atendida);
        assertThat(Metricas.ocupacion(List.of(servicio()), List.of(atendida), LUNES)).isEqualTo(0.25);
    }

    @Test
    void sinVentanasEsCeroYNoNaN() {
        double r = Metricas.ocupacion(List.of(servicio(S1, true, List.of())), List.of(cita()), LUNES);
        assertThat(r).isEqualTo(0);
        assertThat(Double.isNaN(r)).isFalse();
    }

    @Test
    void sinServiciosEsCero() {
        assertThat(Metricas.ocupacion(List.of(), List.of(cita()), LUNES)).isEqualTo(0);
    }

    @Test
    void ignoraServiciosInactivos() {
        assertThat(Metricas.ocupacion(List.of(servicio(S1, false, servicio().ventanas())), List.of(cita()), LUNES))
                .isEqualTo(0);
    }

    @Test
    void ignoraLasCitasDeUnServicioInactivoAunqueHayaOtroActivo() {
        UUID inactivo = UUID.randomUUID();
        List<Servicio> servicios = List.of(servicio(), servicio(inactivo, false, servicio().ventanas()));
        List<Cita> citas = List.of(conServicio(cita(LUNES, "09:00", "11:00", EstadoCita.Confirmada), inactivo));
        assertThat(Metricas.ocupacion(servicios, citas, LUNES)).isEqualTo(0);
    }

    @Test
    void unDiaSinVentanasEseDiaDeLaSemanaEsCero() {
        LocalDate martes = LocalDate.of(2026, 11, 10);
        assertThat(Metricas.ocupacion(List.of(servicio()), List.of(cita(martes, "09:00", "10:00",
                EstadoCita.Confirmada)), martes)).isEqualTo(0);
    }

    @Test
    void sumaLosMinutosDeVariosServiciosActivosEnElMismoDia() {
        UUID b = UUID.randomUUID();
        List<Servicio> servicios = List.of(servicio(), servicio(b, true, List.of(ventana(1, "08:00", "10:00"))));
        List<Cita> citas = List.of(cita(), conServicio(cita(LUNES, "08:00", "09:00", EstadoCita.Confirmada), b));
        // 120 ocupados de 240 + 120 = 360 disponibles
        assertThat(Metricas.ocupacion(servicios, citas, LUNES)).isCloseTo(120.0 / 360, within(1e-12));
    }

    @Test
    void unaCitaDeOtroDiaNoCuentaEnLaOcupacionDeHoy() {
        Cita ayer = cita(LUNES.minusDays(1), "09:00", "10:00", EstadoCita.Confirmada);
        Cita lunesPasado = cita(LUNES.minusDays(7), "09:00", "13:00", EstadoCita.Confirmada);
        assertThat(Metricas.ocupacion(List.of(servicio()), List.of(ayer, lunesPasado), LUNES)).isEqualTo(0);
    }

    // ---------- ocupacionServicio ----------

    @Test
    void sumaVariosDiasDelRangoInclusive() {
        List<Cita> citas = List.of(
                cita(LocalDate.of(2026, 11, 9), "09:00", "10:00", EstadoCita.Confirmada),
                cita(LocalDate.of(2026, 11, 16), "09:00", "11:00", EstadoCita.Confirmada));
        // dos lunes: 480 min disponibles, 60 + 120 ocupados
        assertThat(Metricas.ocupacionServicio(servicio(), citas, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16)))
                .isCloseTo(180.0 / 480, within(1e-12));
    }

    @Test
    void soloCuentaCitasDeEseServicio() {
        assertThat(Metricas.ocupacionServicio(servicio(), List.of(conServicio(cita(), OTRO)), LUNES, LUNES))
                .isEqualTo(0);
    }

    @Test
    void laOcupacionDeUnServicioInactivoEsCero() {
        assertThat(Metricas.ocupacionServicio(servicio(S1, false, servicio().ventanas()), List.of(cita()), LUNES,
                LUNES)).isEqualTo(0);
    }

    @Test
    void unRangoInvertidoNoDaNaN() {
        double r = Metricas.ocupacionServicio(servicio(), List.of(cita()), LUNES, LUNES.minusDays(1));
        assertThat(r).isEqualTo(0);
    }

    @Test
    void laCitaFueraDelRangoNoCuenta() {
        Cita fuera = cita(LUNES.plusDays(7), "09:00", "13:00", EstadoCita.Confirmada);
        assertThat(Metricas.ocupacionServicio(servicio(), List.of(cita(), fuera), LUNES, LUNES.plusDays(6)))
                .isEqualTo(0.25);
    }

    // ---------- ingresosEstimados ----------

    @Test
    void sumaLosValoresDeLasCitasNoCanceladasDelRango() {
        List<Cita> citas = List.of(cita(), cita(LocalDate.of(2026, 11, 10), "09:00", "10:00", EstadoCita.Confirmada),
                cancelada(CREADA));
        assertThat(Metricas.ingresosEstimados(List.of(servicio()), citas, LUNES, LocalDate.of(2026, 11, 10)))
                .isEqualByComparingTo("20000");
    }

    @Test
    void incluyeAmbosExtremosYExcluyeLoDeFuera() {
        List<Cita> citas = new ArrayList<>();
        for (int dia = 8; dia <= 11; dia++) {
            citas.add(cita(LocalDate.of(2026, 11, dia), "09:00", "10:00", EstadoCita.Confirmada));
        }
        assertThat(Metricas.ingresosEstimados(List.of(servicio()), citas, LUNES, LocalDate.of(2026, 11, 10)))
                .isEqualByComparingTo("20000");
    }

    @Test
    void ingresosSumaEnBigDecimalSinPerderCentavos() {
        Servicio barato = new Servicio(S1, "X", 60, true, new BigDecimal("0.10"), List.of());
        List<Cita> citas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            citas.add(cita());
        }
        // con double 0.1 + 0.1 + 0.1 = 0.30000000000000004
        assertThat(Metricas.ingresosEstimados(List.of(barato), citas, LUNES, LUNES)).isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void ingresosSinCitasEsCero() {
        assertThat(Metricas.ingresosEstimados(List.of(servicio()), List.of(), LUNES, LUNES)).isEqualByComparingTo("0");
    }

    @Test
    void unaCitaDeUnServicioDesconocidoSumaCero() {
        assertThat(Metricas.ingresosEstimados(List.of(servicio()), List.of(conServicio(cita(), OTRO)), LUNES, LUNES))
                .isEqualByComparingTo("0");
    }

    @Test
    void ingresosCuentaLasAtendidas() {
        Cita atendida = cita(LUNES, "09:00", "10:00", EstadoCita.Atendida);
        assertThat(Metricas.ingresosEstimados(List.of(servicio()), List.of(atendida), LUNES, LUNES))
                .isEqualByComparingTo("10000");
    }

    // ---------- citasPorDia ----------

    @Test
    void devuelveCerosParaDiasVaciosYCuentaLasNoCanceladas() {
        List<Cita> citas = List.of(cita(), cita(), cancelada(CREADA),
                cita(LocalDate.of(2026, 11, 11), "09:00", "10:00", EstadoCita.Confirmada));
        assertThat(Metricas.citasPorDia(citas, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 11))).containsExactly(
                new CitasDia(LocalDate.of(2026, 11, 9), 2),
                new CitasDia(LocalDate.of(2026, 11, 10), 0),
                new CitasDia(LocalDate.of(2026, 11, 11), 1));
    }

    @Test
    void citasPorDiaCruzaElFinDeMes() {
        assertThat(Metricas.citasPorDia(List.of(), LocalDate.of(2026, 11, 29), LocalDate.of(2026, 12, 1)))
                .extracting(CitasDia::fecha)
                .containsExactly(LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 1));
    }

    // ---------- alertas ----------

    @Test
    void unServicioActivoSinVentanasGeneraUnaAlerta() {
        UUID x = UUID.randomUUID();
        List<Alerta> r = Metricas.alertas(List.of(servicio(x, true, List.of())), List.of(), AHORA);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).tipo()).isEqualTo(TipoAlerta.SERVICIO_SIN_HORARIO);
        assertThat(r.get(0).ref()).isEqualTo(x);
        assertThat(r.get(0).titulo()).isEqualTo("Servicio sin horario");
        assertThat(r.get(0).detalle()).isEqualTo("Corte no tiene horarios de atención");
    }

    @Test
    void unServicioInactivoSinVentanasNoGeneraAlerta() {
        assertThat(Metricas.alertas(List.of(servicio(S1, false, List.of())), List.of(), AHORA)).isEmpty();
    }

    @Test
    void unServicioActivoConVentanasPeroSinVentanaHoyNoGeneraAlerta() {
        assertThat(Metricas.alertas(List.of(servicio()), List.of(), AHORA)).isEmpty();
    }

    @Test
    void unaCitaCreadaHace2HEsCitaNueva() {
        Cita c = conCreada(cita(), AHORA.minus(Duration.ofHours(2)));
        List<Alerta> r = Metricas.alertas(List.of(servicio()), List.of(c), AHORA);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).tipo()).isEqualTo(TipoAlerta.CITA_NUEVA);
        assertThat(r.get(0).ref()).isEqualTo(c.id());
        assertThat(r.get(0).titulo()).isEqualTo("Cita nueva");
    }

    @Test
    void elDetalleEscribeLaFechaEnEspanolNoEnIso() {
        Cita c = conCreada(cita(LocalDate.of(2026, 11, 8), "09:00", "10:00", EstadoCita.Confirmada),
                AHORA.minus(Duration.ofHours(2)));
        assertThat(Metricas.alertas(List.of(servicio()), List.of(c), AHORA).get(0).detalle())
                .isEqualTo("Corte, Domingo 8 de noviembre a las 09:00");
    }

    @Test
    void elDetalleUsaServicioSiElServicioNoSeConoce() {
        Cita c = conCreada(conServicio(cita(), OTRO), AHORA.minus(Duration.ofHours(2)));
        assertThat(Metricas.alertas(List.of(servicio()), List.of(c), AHORA).get(0).detalle())
                .isEqualTo("Servicio, Lunes 9 de noviembre a las 09:00");
    }

    @Test
    void unaCitaCreadaHace30HNoGeneraAlerta() {
        Cita c = conCreada(cita(), AHORA.minus(Duration.ofHours(30)));
        assertThat(Metricas.alertas(List.of(servicio()), List.of(c), AHORA)).isEmpty();
    }

    @Test
    void creadaJustoHace24HYaNoEsReciente() {
        Cita limite = conCreada(cita(), AHORA.minus(Duration.ofHours(24)));
        Cita casi = conCreada(cita(), AHORA.minus(Duration.ofHours(24)).plusMillis(1));
        assertThat(Metricas.alertas(List.of(servicio()), List.of(limite), AHORA)).isEmpty();
        assertThat(Metricas.alertas(List.of(servicio()), List.of(casi), AHORA)).hasSize(1);
    }

    @Test
    void unaCitaCreadaEnElFuturoNoGeneraAlerta() {
        Cita c = conCreada(cita(), AHORA.plusSeconds(1));
        assertThat(Metricas.alertas(List.of(servicio()), List.of(c), AHORA)).isEmpty();
    }

    @Test
    void unaCitaCanceladaHace3HEsCitaCancelada() {
        Cita c = cancelada(AHORA.minus(Duration.ofHours(3)));
        List<Alerta> r = Metricas.alertas(List.of(servicio()), List.of(c), AHORA);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).tipo()).isEqualTo(TipoAlerta.CITA_CANCELADA);
        assertThat(r.get(0).ref()).isEqualTo(c.id());
        assertThat(r.get(0).titulo()).isEqualTo("Cita cancelada");
    }

    @Test
    void unaCitaCanceladaHace30HNoGeneraAlertaNiSiSeCreoHaceUnRato() {
        Cita c = conCreada(cancelada(AHORA.minus(Duration.ofHours(30))), AHORA.minus(Duration.ofHours(1)));
        assertThat(Metricas.alertas(List.of(servicio()), List.of(c), AHORA)).isEmpty();
    }

    @Test
    void unaCitaCanceladaSinFechaDeCancelacionNoGeneraAlerta() {
        assertThat(Metricas.alertas(List.of(servicio()), List.of(cancelada(null)), AHORA)).isEmpty();
    }

    @Test
    void lasAlertasDeCitasVanAntesQueLasDeServiciosSinHorario() {
        Cita c = conCreada(cita(), AHORA.minus(Duration.ofHours(1)));
        List<Alerta> r = Metricas.alertas(List.of(servicio(), servicio(UUID.randomUUID(), true, List.of())),
                List.of(c), AHORA);
        assertThat(r).extracting(Alerta::tipo).containsExactly(TipoAlerta.CITA_NUEVA, TipoAlerta.SERVICIO_SIN_HORARIO);
    }

    @Test
    void limitarAlertasMuestraHasta6EnElOrdenRecibidoEIndicaCuantasQuedanFuera() {
        List<Alerta> todas = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            todas.add(new Alerta(TipoAlerta.CITA_NUEVA, "Cita nueva", "d" + i, UUID.randomUUID()));
        }
        assertThat(Metricas.MAX_ALERTAS).isEqualTo(6);
        assertThat(Metricas.limitarAlertas(todas)).isEqualTo(todas.subList(0, 6));
        assertThat(Metricas.limitarAlertas(todas.subList(0, 3))).isEqualTo(todas.subList(0, 3));
        assertThat(Metricas.limitarAlertas(List.of())).isEmpty();
    }

    // ---------- variacion ----------

    @Test
    void variacionConPrevioCeroEsVacia() {
        assertThat(Metricas.variacion(5, 0)).isEqualTo(OptionalDouble.empty());
        assertThat(Metricas.variacion(0, 0)).isEmpty();
    }

    @Test
    void doceContraDiezEs02() {
        assertThat(Metricas.variacion(12, 10).getAsDouble()).isCloseTo(0.2, within(1e-10));
    }

    @Test
    void unaBajaEsNegativa() {
        assertThat(Metricas.variacion(5, 10).getAsDouble()).isCloseTo(-0.5, within(1e-10));
    }

    @Test
    void veinticincoContraDoceYMedioEsCienPorCiento() {
        assertThat(Metricas.variacion(25, 12.5).getAsDouble()).isCloseTo(1.0, within(1e-10));
    }

    @Test
    void variacionDeCeroContraAlgoEsMenosCienPorCiento() {
        assertThat(Metricas.variacion(0, 4).getAsDouble()).isCloseTo(-1.0, within(1e-10));
    }

    // ---------- proximasCitas ----------

    private static Cita confirmada(String fecha, String inicio) {
        return cita(LocalDate.parse(fecha), inicio, "23:59", EstadoCita.Confirmada);
    }

    private static final LocalDateTime LOCAL = LocalDateTime.of(2026, 11, 9, 8, 0);

    @Test
    void devuelveLasProximasConfirmadasDesdeAhoraOrdenadasHastaElLimite() {
        List<Cita> citas = List.of(
                confirmada("2026-11-12", "09:00"),
                confirmada("2026-11-09", "07:30"), // ya empezó
                cita(LUNES, "09:00", "23:59", EstadoCita.Atendida),
                confirmada("2026-11-09", "10:00"),
                cita(LocalDate.of(2026, 11, 10), "09:00", "23:59", EstadoCita.Cancelada),
                confirmada("2026-11-09", "08:00"), // empieza justo ahora: cuenta
                confirmada("2026-11-13", "09:00"),
                confirmada("2026-11-14", "09:00"),
                confirmada("2026-11-15", "09:00"));
        assertThat(Metricas.proximasCitas(citas, LOCAL, 5))
                .extracting(c -> c.fecha() + " " + c.inicio())
                .containsExactly("2026-11-09 08:00", "2026-11-09 10:00", "2026-11-12 09:00", "2026-11-13 09:00",
                        "2026-11-14 09:00");
    }

    @Test
    void sinCitasConfirmadasPorDelanteNoHayProximas() {
        List<Cita> citas = List.of(cita(LocalDate.of(2026, 11, 10), "09:00", "23:59", EstadoCita.Cancelada),
                confirmada("2026-11-08", "09:00"));
        assertThat(Metricas.proximasCitas(citas, LOCAL, 5)).isEmpty();
    }

    // ---------- ocupacionSemana ----------

    private static final List<VentanaDisponibilidad> LUN_A_VIE = List.of(
            ventana(1, "08:00", "12:00"), ventana(2, "08:00", "12:00"), ventana(3, "08:00", "12:00"),
            ventana(4, "08:00", "12:00"), ventana(5, "08:00", "12:00"));

    private static Servicio semanal(UUID id, String nombre, boolean activo) {
        return new Servicio(id, nombre, 60, activo, BigDecimal.TEN, LUN_A_VIE);
    }

    private static Cita deServicio(UUID id, String fecha, String inicio, String fin, EstadoCita estado) {
        return conServicio(cita(LocalDate.parse(fecha), inicio, fin, estado), id);
    }

    @Test
    void calculaLaOcupacionPorServicioActivoDeLunesADomingoDeLaSemanaDeHoy() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<Cita> citas = List.of(
                // 120 min de 1200 = 10 %
                deServicio(a, "2026-11-09", "08:00", "09:00", EstadoCita.Confirmada),
                deServicio(a, "2026-11-13", "08:00", "09:00", EstadoCita.Confirmada),
                // fuera de la semana
                deServicio(a, "2026-11-16", "08:00", "12:00", EstadoCita.Confirmada),
                deServicio(a, "2026-11-06", "08:00", "12:00", EstadoCita.Confirmada),
                // cancelada
                deServicio(a, "2026-11-10", "08:00", "12:00", EstadoCita.Cancelada));
        List<OcupacionServicio> semana = Metricas.ocupacionSemana(
                List.of(semanal(a, "Servicio A", true), semanal(b, "Servicio B", true), semanal(c, "Servicio C", false)),
                citas, LocalDate.of(2026, 11, 11)); // miércoles
        assertThat(semana).extracting(OcupacionServicio::nombre).containsExactly("Servicio A", "Servicio B");
        assertThat(semana).extracting(OcupacionServicio::servicioId).containsExactly(a, b);
        assertThat(semana.get(0).ocupacion()).isCloseTo(0.1, within(1e-12));
        assertThat(semana.get(1).ocupacion()).isEqualTo(0);
    }

    @Test
    void unDomingoSigueContandoLaSemanaQueEmpezoElLunesAnterior() {
        UUID a = UUID.randomUUID();
        List<Cita> citas = List.of(deServicio(a, "2026-11-09", "08:00", "12:00", EstadoCita.Confirmada)); // 20 %
        assertThat(Metricas.ocupacionSemana(List.of(semanal(a, "A", true)), citas, LocalDate.of(2026, 11, 15))
                .get(0).ocupacion()).isCloseTo(0.2, within(1e-12));
        assertThat(Metricas.ocupacionSemana(List.of(semanal(a, "A", true)), citas, LocalDate.of(2026, 11, 16))
                .get(0).ocupacion()).isEqualTo(0);
    }

    @Test
    void sinServiciosActivosLaOcupacionSemanalEstaVacia() {
        assertThat(Metricas.ocupacionSemana(List.of(semanal(UUID.randomUUID(), "C", false)), List.of(), LUNES))
                .isEmpty();
    }

    @Test
    void elLunesDeLaSemanaYElDomingoSeCalculan() {
        assertThat(Metricas.lunesDeLaSemana(LocalDate.of(2026, 11, 15))).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(Metricas.lunesDeLaSemana(LocalDate.of(2026, 11, 9))).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(Metricas.lunesDeLaSemana(LocalDate.of(2026, 11, 16))).isEqualTo(LocalDate.of(2026, 11, 16));
    }
}
