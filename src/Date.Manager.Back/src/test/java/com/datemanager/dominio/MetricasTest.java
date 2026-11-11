package com.datemanager.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static void assertIgualPorComparacion(String esperado, BigDecimal actual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(actual), "esperado " + esperado + " pero fue " + actual);
    }

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
        assertEquals(0.25, Metricas.ocupacion(List.of(servicio()), List.of(cita()), LUNES));
    }

    @Test
    void unaCitaCanceladaNoCuenta() {
        assertEquals(0, Metricas.ocupacion(List.of(servicio()), List.of(cancelada(CREADA)), LUNES));
    }

    @Test
    void unaCitaAtendidaCuenta() {
        Cita atendida = cita(LUNES, "09:00", "10:00", EstadoCita.Atendida);
        assertEquals(0.25, Metricas.ocupacion(List.of(servicio()), List.of(atendida), LUNES));
    }

    @Test
    void sinVentanasEsCeroYNoNaN() {
        double r = Metricas.ocupacion(List.of(servicio(S1, true, List.of())), List.of(cita()), LUNES);
        assertEquals(0, r);
        assertFalse(Double.isNaN(r));
    }

    @Test
    void sinServiciosEsCero() {
        assertEquals(0, Metricas.ocupacion(List.of(), List.of(cita()), LUNES));
    }

    @Test
    void ignoraServiciosInactivos() {
        assertEquals(0, Metricas.ocupacion(List.of(servicio(S1, false, servicio().ventanas())), List.of(cita()), LUNES));
    }

    @Test
    void ignoraLasCitasDeUnServicioInactivoAunqueHayaOtroActivo() {
        UUID inactivo = UUID.randomUUID();
        List<Servicio> servicios = List.of(servicio(), servicio(inactivo, false, servicio().ventanas()));
        List<Cita> citas = List.of(conServicio(cita(LUNES, "09:00", "11:00", EstadoCita.Confirmada), inactivo));
        assertEquals(0, Metricas.ocupacion(servicios, citas, LUNES));
    }

    @Test
    void unDiaSinVentanasEseDiaDeLaSemanaEsCero() {
        LocalDate martes = LocalDate.of(2026, 11, 10);
        assertEquals(0, Metricas.ocupacion(List.of(servicio()), List.of(cita(martes, "09:00", "10:00",
                EstadoCita.Confirmada)), martes));
    }

    @Test
    void sumaLosMinutosDeVariosServiciosActivosEnElMismoDia() {
        UUID b = UUID.randomUUID();
        List<Servicio> servicios = List.of(servicio(), servicio(b, true, List.of(ventana(1, "08:00", "10:00"))));
        List<Cita> citas = List.of(cita(), conServicio(cita(LUNES, "08:00", "09:00", EstadoCita.Confirmada), b));
        // 120 ocupados de 240 + 120 = 360 disponibles
        assertEquals(120.0 / 360, Metricas.ocupacion(servicios, citas, LUNES), 1e-12);
    }

    @Test
    void unaCitaDeOtroDiaNoCuentaEnLaOcupacionDeHoy() {
        Cita ayer = cita(LUNES.minusDays(1), "09:00", "10:00", EstadoCita.Confirmada);
        Cita lunesPasado = cita(LUNES.minusDays(7), "09:00", "13:00", EstadoCita.Confirmada);
        assertEquals(0, Metricas.ocupacion(List.of(servicio()), List.of(ayer, lunesPasado), LUNES));
    }

    // ---------- ocupacionServicio ----------

    @Test
    void sumaVariosDiasDelRangoInclusive() {
        List<Cita> citas = List.of(
                cita(LocalDate.of(2026, 11, 9), "09:00", "10:00", EstadoCita.Confirmada),
                cita(LocalDate.of(2026, 11, 16), "09:00", "11:00", EstadoCita.Confirmada));
        // dos lunes: 480 min disponibles, 60 + 120 ocupados
        assertEquals(180.0 / 480,
                Metricas.ocupacionServicio(servicio(), citas, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16)),
                1e-12);
    }

    @Test
    void soloCuentaCitasDeEseServicio() {
        assertEquals(0, Metricas.ocupacionServicio(servicio(), List.of(conServicio(cita(), OTRO)), LUNES, LUNES));
    }

    @Test
    void laOcupacionDeUnServicioInactivoEsCero() {
        assertEquals(0, Metricas.ocupacionServicio(servicio(S1, false, servicio().ventanas()), List.of(cita()), LUNES,
                LUNES));
    }

    @Test
    void unRangoInvertidoNoDaNaN() {
        double r = Metricas.ocupacionServicio(servicio(), List.of(cita()), LUNES, LUNES.minusDays(1));
        assertEquals(0, r);
    }

    @Test
    void laCitaFueraDelRangoNoCuenta() {
        Cita fuera = cita(LUNES.plusDays(7), "09:00", "13:00", EstadoCita.Confirmada);
        assertEquals(0.25, Metricas.ocupacionServicio(servicio(), List.of(cita(), fuera), LUNES, LUNES.plusDays(6)));
    }

    // ---------- ingresosEstimados ----------

    @Test
    void sumaLosValoresDeLasCitasNoCanceladasDelRango() {
        List<Cita> citas = List.of(cita(), cita(LocalDate.of(2026, 11, 10), "09:00", "10:00", EstadoCita.Confirmada),
                cancelada(CREADA));
        assertIgualPorComparacion("20000", Metricas.ingresosEstimados(List.of(servicio()), citas, LUNES, LocalDate.of(2026, 11, 10)));
    }

    @Test
    void incluyeAmbosExtremosYExcluyeLoDeFuera() {
        List<Cita> citas = new ArrayList<>();
        for (int dia = 8; dia <= 11; dia++) {
            citas.add(cita(LocalDate.of(2026, 11, dia), "09:00", "10:00", EstadoCita.Confirmada));
        }
        assertIgualPorComparacion("20000", Metricas.ingresosEstimados(List.of(servicio()), citas, LUNES, LocalDate.of(2026, 11, 10)));
    }

    @Test
    void ingresosSumaEnBigDecimalSinPerderCentavos() {
        Servicio barato = new Servicio(S1, "X", 60, true, new BigDecimal("0.10"), List.of());
        List<Cita> citas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            citas.add(cita());
        }
        // con double 0.1 + 0.1 + 0.1 = 0.30000000000000004
        assertEquals(new BigDecimal("0.30"), Metricas.ingresosEstimados(List.of(barato), citas, LUNES, LUNES));
    }

    @Test
    void ingresosSinCitasEsCero() {
        assertIgualPorComparacion("0", Metricas.ingresosEstimados(List.of(servicio()), List.of(), LUNES, LUNES));
    }

    @Test
    void unaCitaDeUnServicioDesconocidoSumaCero() {
        assertIgualPorComparacion("0", Metricas.ingresosEstimados(List.of(servicio()), List.of(conServicio(cita(), OTRO)), LUNES, LUNES));
    }

    @Test
    void ingresosCuentaLasAtendidas() {
        Cita atendida = cita(LUNES, "09:00", "10:00", EstadoCita.Atendida);
        assertIgualPorComparacion("10000", Metricas.ingresosEstimados(List.of(servicio()), List.of(atendida), LUNES, LUNES));
    }

    // ---------- citasPorDia ----------

    @Test
    void devuelveCerosParaDiasVaciosYCuentaLasNoCanceladas() {
        List<Cita> citas = List.of(cita(), cita(), cancelada(CREADA),
                cita(LocalDate.of(2026, 11, 11), "09:00", "10:00", EstadoCita.Confirmada));
        assertEquals(List.of(
                new CitasDia(LocalDate.of(2026, 11, 9), 2),
                new CitasDia(LocalDate.of(2026, 11, 10), 0),
                new CitasDia(LocalDate.of(2026, 11, 11), 1)),
                Metricas.citasPorDia(citas, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 11)));
    }

    @Test
    void citasPorDiaCruzaElFinDeMes() {
        assertEquals(List.of(LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 1)),
                Metricas.citasPorDia(List.of(), LocalDate.of(2026, 11, 29), LocalDate.of(2026, 12, 1)).stream()
                        .map(CitasDia::fecha).toList());
    }

    // ---------- alertas ----------

    @Test
    void unServicioActivoSinVentanasGeneraUnaAlerta() {
        UUID x = UUID.randomUUID();
        List<Alerta> r = Metricas.alertas(List.of(servicio(x, true, List.of())), List.of(), AHORA);
        assertEquals(1, r.size());
        assertEquals(TipoAlerta.SERVICIO_SIN_HORARIO, r.get(0).tipo());
        assertEquals(x, r.get(0).ref());
        assertEquals("Servicio sin horario", r.get(0).titulo());
        assertEquals("Corte no tiene horarios de atención", r.get(0).detalle());
    }

    @Test
    void unServicioInactivoSinVentanasNoGeneraAlerta() {
        assertTrue(Metricas.alertas(List.of(servicio(S1, false, List.of())), List.of(), AHORA).isEmpty());
    }

    @Test
    void unServicioActivoConVentanasPeroSinVentanaHoyNoGeneraAlerta() {
        assertTrue(Metricas.alertas(List.of(servicio()), List.of(), AHORA).isEmpty());
    }

    @Test
    void unaCitaCreadaHace2HEsCitaNueva() {
        Cita c = conCreada(cita(), AHORA.minus(Duration.ofHours(2)));
        List<Alerta> r = Metricas.alertas(List.of(servicio()), List.of(c), AHORA);
        assertEquals(1, r.size());
        assertEquals(TipoAlerta.CITA_NUEVA, r.get(0).tipo());
        assertEquals(c.id(), r.get(0).ref());
        assertEquals("Cita nueva", r.get(0).titulo());
    }

    @Test
    void elDetalleEscribeLaFechaEnEspanolNoEnIso() {
        Cita c = conCreada(cita(LocalDate.of(2026, 11, 8), "09:00", "10:00", EstadoCita.Confirmada),
                AHORA.minus(Duration.ofHours(2)));
        assertEquals("Corte, Domingo 8 de noviembre a las 09:00", Metricas.alertas(List.of(servicio()), List.of(c), AHORA).get(0).detalle());
    }

    @Test
    void elDetalleUsaServicioSiElServicioNoSeConoce() {
        Cita c = conCreada(conServicio(cita(), OTRO), AHORA.minus(Duration.ofHours(2)));
        assertEquals("Servicio, Lunes 9 de noviembre a las 09:00", Metricas.alertas(List.of(servicio()), List.of(c), AHORA).get(0).detalle());
    }

    @Test
    void unaCitaCreadaHace30HNoGeneraAlerta() {
        Cita c = conCreada(cita(), AHORA.minus(Duration.ofHours(30)));
        assertTrue(Metricas.alertas(List.of(servicio()), List.of(c), AHORA).isEmpty());
    }

    @Test
    void creadaJustoHace24HYaNoEsReciente() {
        Cita limite = conCreada(cita(), AHORA.minus(Duration.ofHours(24)));
        Cita casi = conCreada(cita(), AHORA.minus(Duration.ofHours(24)).plusMillis(1));
        assertTrue(Metricas.alertas(List.of(servicio()), List.of(limite), AHORA).isEmpty());
        assertEquals(1, Metricas.alertas(List.of(servicio()), List.of(casi), AHORA).size());
    }

    @Test
    void unaCitaCreadaEnElFuturoNoGeneraAlerta() {
        Cita c = conCreada(cita(), AHORA.plusSeconds(1));
        assertTrue(Metricas.alertas(List.of(servicio()), List.of(c), AHORA).isEmpty());
    }

    @Test
    void unaCitaCanceladaHace3HEsCitaCancelada() {
        Cita c = cancelada(AHORA.minus(Duration.ofHours(3)));
        List<Alerta> r = Metricas.alertas(List.of(servicio()), List.of(c), AHORA);
        assertEquals(1, r.size());
        assertEquals(TipoAlerta.CITA_CANCELADA, r.get(0).tipo());
        assertEquals(c.id(), r.get(0).ref());
        assertEquals("Cita cancelada", r.get(0).titulo());
    }

    @Test
    void unaCitaCanceladaHace30HNoGeneraAlertaNiSiSeCreoHaceUnRato() {
        Cita c = conCreada(cancelada(AHORA.minus(Duration.ofHours(30))), AHORA.minus(Duration.ofHours(1)));
        assertTrue(Metricas.alertas(List.of(servicio()), List.of(c), AHORA).isEmpty());
    }

    @Test
    void unaCitaCanceladaSinFechaDeCancelacionNoGeneraAlerta() {
        assertTrue(Metricas.alertas(List.of(servicio()), List.of(cancelada(null)), AHORA).isEmpty());
    }

    @Test
    void lasAlertasDeCitasVanAntesQueLasDeServiciosSinHorario() {
        Cita c = conCreada(cita(), AHORA.minus(Duration.ofHours(1)));
        List<Alerta> r = Metricas.alertas(List.of(servicio(), servicio(UUID.randomUUID(), true, List.of())),
                List.of(c), AHORA);
        assertEquals(List.of(TipoAlerta.CITA_NUEVA, TipoAlerta.SERVICIO_SIN_HORARIO),
                r.stream().map(Alerta::tipo).toList());
    }

    @Test
    void limitarAlertasMuestraHasta6EnElOrdenRecibidoEIndicaCuantasQuedanFuera() {
        List<Alerta> todas = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            todas.add(new Alerta(TipoAlerta.CITA_NUEVA, "Cita nueva", "d" + i, UUID.randomUUID()));
        }
        assertEquals(6, Metricas.MAX_ALERTAS);
        assertEquals(todas.subList(0, 6), Metricas.limitarAlertas(todas));
        assertEquals(todas.subList(0, 3), Metricas.limitarAlertas(todas.subList(0, 3)));
        assertTrue(Metricas.limitarAlertas(List.of()).isEmpty());
    }

    // ---------- variacion ----------

    @Test
    void variacionConPrevioCeroEsVacia() {
        assertEquals(OptionalDouble.empty(), Metricas.variacion(5, 0));
        assertTrue(Metricas.variacion(0, 0).isEmpty());
    }

    @Test
    void doceContraDiezEs02() {
        assertEquals(0.2, Metricas.variacion(12, 10).getAsDouble(), 1e-10);
    }

    @Test
    void unaBajaEsNegativa() {
        assertEquals(-0.5, Metricas.variacion(5, 10).getAsDouble(), 1e-10);
    }

    @Test
    void veinticincoContraDoceYMedioEsCienPorCiento() {
        assertEquals(1.0, Metricas.variacion(25, 12.5).getAsDouble(), 1e-10);
    }

    @Test
    void variacionDeCeroContraAlgoEsMenosCienPorCiento() {
        assertEquals(-1.0, Metricas.variacion(0, 4).getAsDouble(), 1e-10);
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
        assertEquals(List.of("2026-11-09 08:00", "2026-11-09 10:00", "2026-11-12 09:00", "2026-11-13 09:00",
                "2026-11-14 09:00"),
                Metricas.proximasCitas(citas, LOCAL, 5).stream().map(c -> c.fecha() + " " + c.inicio()).toList());
    }

    @Test
    void sinCitasConfirmadasPorDelanteNoHayProximas() {
        List<Cita> citas = List.of(cita(LocalDate.of(2026, 11, 10), "09:00", "23:59", EstadoCita.Cancelada),
                confirmada("2026-11-08", "09:00"));
        assertTrue(Metricas.proximasCitas(citas, LOCAL, 5).isEmpty());
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
        assertEquals(List.of("Servicio A", "Servicio B"), semana.stream().map(OcupacionServicio::nombre).toList());
        assertEquals(List.of(a, b), semana.stream().map(OcupacionServicio::servicioId).toList());
        assertEquals(0.1, semana.get(0).ocupacion(), 1e-12);
        assertEquals(0, semana.get(1).ocupacion());
    }

    @Test
    void unDomingoSigueContandoLaSemanaQueEmpezoElLunesAnterior() {
        UUID a = UUID.randomUUID();
        List<Cita> citas = List.of(deServicio(a, "2026-11-09", "08:00", "12:00", EstadoCita.Confirmada)); // 20 %
        assertEquals(0.2, Metricas.ocupacionSemana(List.of(semanal(a, "A", true)), citas, LocalDate.of(2026, 11, 15))
                .get(0).ocupacion(), 1e-12);
        assertEquals(0, Metricas.ocupacionSemana(List.of(semanal(a, "A", true)), citas, LocalDate.of(2026, 11, 16))
                .get(0).ocupacion());
    }

    @Test
    void sinServiciosActivosLaOcupacionSemanalEstaVacia() {
        assertTrue(Metricas.ocupacionSemana(List.of(semanal(UUID.randomUUID(), "C", false)), List.of(), LUNES).isEmpty());
    }

    @Test
    void elLunesDeLaSemanaYElDomingoSeCalculan() {
        assertEquals(LocalDate.of(2026, 11, 9), Metricas.lunesDeLaSemana(LocalDate.of(2026, 11, 15)));
        assertEquals(LocalDate.of(2026, 11, 9), Metricas.lunesDeLaSemana(LocalDate.of(2026, 11, 9)));
        assertEquals(LocalDate.of(2026, 11, 16), Metricas.lunesDeLaSemana(LocalDate.of(2026, 11, 16)));
    }
}
