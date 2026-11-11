package com.datemanager.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class HorariosTest {

    private static final UUID SERVICIO_ID = UUID.randomUUID();
    private static final UUID OTRO_SERVICIO_ID = UUID.randomUUID();
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 8, 8, 0); // domingo
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 9);

    private static LocalTime t(String hhmm) {
        return LocalTime.parse(hhmm);
    }

    private static List<LocalTime> ts(String... horas) {
        return Arrays.stream(horas).map(LocalTime::parse).toList();
    }

    private static VentanaDisponibilidad ventana(int dia, String inicio, String fin) {
        return new VentanaDisponibilidad(UUID.randomUUID(), dia, t(inicio), t(fin));
    }

    private static Servicio servicio(int duracionMin, boolean activo, VentanaDisponibilidad... ventanas) {
        return new Servicio(SERVICIO_ID, "Corte", duracionMin, activo, new BigDecimal("10000"), List.of(ventanas));
    }

    private static Servicio servicioLunes() {
        return servicio(60, true, ventana(1, "09:00", "12:00"));
    }

    private static Cita cita(UUID id, UUID servicioId, String inicio, String fin, EstadoCita estado) {
        return new Cita(id, servicioId, UUID.randomUUID(), LUNES, t(inicio), t(fin), estado,
                Instant.parse("2026-11-01T10:00:00Z"), null);
    }

    private static Cita citaDeLas10() {
        return cita(UUID.randomUUID(), SERVICIO_ID, "10:00", "11:00", EstadoCita.Confirmada);
    }

    @Test
    void unaDuracionEnormeNoDesbordaNiDevuelveHorarios() {
        // Sin pasar por Validaciones: ini + duracion desbordaría int y el bucle recorrería millones de pasos.
        Servicio enorme = servicio(Integer.MAX_VALUE, true, ventana(1, "09:00", "12:00"));

        assertTrue(assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> Horarios.horariosLibres(enorme, LUNES, List.of(), AHORA, null)).isEmpty());
    }

    @Test
    void exponeLasConstantes() {
        assertEquals(30, Horarios.PASO_MIN);
        assertEquals(30, Horarios.MAX_DIAS_ANTICIPACION);
    }

    @Test
    void ofreceInicioCadaTreintaMinutosMientrasLaCitaQuepa() {
        assertEquals(ts("09:00", "09:30", "10:00", "10:30", "11:00"), Horarios.horariosLibres(servicioLunes(), LUNES, List.of(), AHORA, null));
    }

    @Test
    void unaCitaContiguaNoBloquea() {
        assertEquals(ts("09:00", "11:00"), Horarios.horariosLibres(servicioLunes(), LUNES, List.of(citaDeLas10()), AHORA, null));
    }

    @Test
    void ignoraCitasCanceladasYDeOtrosServicios() {
        List<Cita> citas = List.of(
                cita(UUID.randomUUID(), SERVICIO_ID, "10:00", "11:00", EstadoCita.Cancelada),
                cita(UUID.randomUUID(), OTRO_SERVICIO_ID, "10:00", "11:00", EstadoCita.Confirmada));
        assertEquals(ts("09:00", "09:30", "10:00", "10:30", "11:00"), Horarios.horariosLibres(servicioLunes(), LUNES, citas, AHORA, null));
    }

    @Test
    void unaCitaAtendidaSiBloquea() {
        List<Cita> citas = List.of(cita(UUID.randomUUID(), SERVICIO_ID, "10:00", "11:00", EstadoCita.Atendida));
        assertEquals(ts("09:00", "11:00"), Horarios.horariosLibres(servicioLunes(), LUNES, citas, AHORA, null));
    }

    @Test
    void ventanaMasCortaQueLaDuracionNoOfreceNada() {
        Servicio s = servicio(90, true, ventana(1, "09:00", "10:00"));
        assertTrue(Horarios.horariosLibres(s, LUNES, List.of(), AHORA, null).isEmpty());
    }

    @Test
    void usaDiasIsoParaElDomingo() {
        Servicio s = servicio(60, true, ventana(7, "09:00", "12:00"));
        LocalDateTime antes = LocalDateTime.of(2026, 11, 1, 10, 0);
        assertFalse(Horarios.horariosLibres(s, LocalDate.of(2026, 11, 8), List.of(), antes, null).isEmpty());
        assertTrue(Horarios.horariosLibres(s, LUNES, List.of(), antes, null).isEmpty());
    }

    @Test
    void hoyNoOfreceHorasPasadas() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 9, 10, 15); // lunes 10:15
        assertEquals(ts("10:30", "11:00"), Horarios.horariosLibres(servicioLunes(), LUNES, List.of(), ahora, null));
    }

    @Test
    void aLas2330DelDiaAnteriorElDiaSiguienteCuentaComoManana() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 8, 23, 30); // domingo 23:30
        assertEquals(ts("09:00", "09:30", "10:00", "10:30", "11:00"), Horarios.horariosLibres(servicioLunes(), LUNES, List.of(), ahora, null));
    }

    @Test
    void limiteDeAnticipacion() {
        Servicio s = servicio(60, true, IntStream.rangeClosed(1, 7)
                .mapToObj(d -> ventana(d, "09:00", "12:00")).toArray(VentanaDisponibilidad[]::new));
        assertFalse(Horarios.horariosLibres(s, LocalDate.of(2026, 12, 8), List.of(), AHORA, null).isEmpty());
        assertTrue(Horarios.horariosLibres(s, LocalDate.of(2026, 12, 9), List.of(), AHORA, null).isEmpty());
        assertTrue(Horarios.horariosLibres(s, LocalDate.of(2026, 11, 7), List.of(), AHORA, null).isEmpty());
    }

    @Test
    void servicioInactivoNoOfreceNada() {
        Servicio s = servicio(60, false, ventana(1, "09:00", "12:00"));
        assertTrue(Horarios.horariosLibres(s, LUNES, List.of(), AHORA, null).isEmpty());
    }

    @Test
    void servicioInactivoCalculaAlMoverUnaCita() {
        Servicio inactivo = servicio(60, false, ventana(1, "09:00", "12:00"));
        Cita propia = citaDeLas10();
        assertEquals(ts("09:00", "09:30", "10:00", "10:30", "11:00"), Horarios.horariosLibres(inactivo, LUNES, List.of(propia), AHORA, propia.id()));
        assertTrue(Horarios.horariosLibres(inactivo, LUNES, List.of(propia), AHORA, null).isEmpty());
    }

    @Test
    void excluirCitaIdLiberaElHorarioPropio() {
        Cita propia = citaDeLas10();
        assertFalse(Horarios.horariosLibres(servicioLunes(), LUNES, List.of(propia), AHORA, null).contains(t("10:00")));
        assertTrue(Horarios.horariosLibres(servicioLunes(), LUNES, List.of(propia), AHORA, propia.id()).contains(t("10:00")));
    }

    @Test
    void ventanaQueNoEmpiezaEnPuntoOMediaConservaSuDesfase() {
        Servicio s = servicio(60, true, ventana(1, "09:15", "11:15"));
        assertEquals(ts("09:15", "09:45", "10:15"), Horarios.horariosLibres(s, LUNES, List.of(), AHORA, null));
    }

    @Test
    void duracionQueNoEsMultiploDeTreintaNoSeSalePorElFinDeLaVentana() {
        Servicio s = servicio(45, true, ventana(1, "09:00", "11:00"));
        assertEquals(ts("09:00", "09:30", "10:00"), Horarios.horariosLibres(s, LUNES, List.of(), AHORA, null));
    }

    @Test
    void ventanasSolapadasNoRepitenHorasYVanOrdenadas() {
        Servicio s = servicio(60, true, ventana(1, "10:00", "13:00"), ventana(1, "09:00", "12:00"));
        assertEquals(ts("09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00"), Horarios.horariosLibres(s, LUNES, List.of(), AHORA, null));
    }

    @Test
    void dosVentanasDelMismoDiaSeUnenOrdenadas() {
        Servicio s = servicio(60, true, ventana(1, "14:00", "16:00"), ventana(1, "09:00", "11:00"));
        assertEquals(ts("09:00", "09:30", "10:00", "14:00", "14:30", "15:00"), Horarios.horariosLibres(s, LUNES, List.of(), AHORA, null));
    }
}
