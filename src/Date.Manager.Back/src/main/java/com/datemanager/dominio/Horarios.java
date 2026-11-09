package com.datemanager.dominio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/** Horarios libres de un servicio (porta {@code core/horarios.ts}). "Ahora" siempre llega por parámetro, en hora del negocio. */
public final class Horarios {

    public static final int PASO_MIN = 30;
    public static final int MAX_DIAS_ANTICIPACION = 30;

    private Horarios() {
    }

    /**
     * Inicios posibles (cada {@link #PASO_MIN} minutos desde el inicio de cada ventana) para reservar {@code servicio}
     * el {@code fecha}. Un servicio inactivo no se reserva, pero sus citas existentes sí se pueden mover
     * ({@code excluirCitaId != null}). {@code excluirCitaId} libera el horario de esa cita.
     */
    public static List<LocalTime> horariosLibres(
            Servicio servicio, LocalDate fecha, List<Cita> citas, LocalDateTime ahora, UUID excluirCitaId) {
        if (!servicio.activo() && excluirCitaId == null) {
            return List.of();
        }

        long dias = ChronoUnit.DAYS.between(ahora.toLocalDate(), fecha);
        if (dias < 0 || dias > MAX_DIAS_ANTICIPACION) {
            return List.of();
        }

        int minutosAhora = aMinutos(ahora.toLocalTime());
        List<Cita> ocupadas = citas.stream()
                .filter(c -> c.servicioId().equals(servicio.id())
                        && c.fecha().equals(fecha)
                        && c.estado() != EstadoCita.Cancelada
                        && !c.id().equals(excluirCitaId))
                .toList();

        int dia = fecha.getDayOfWeek().getValue();
        TreeSet<Integer> inicios = new TreeSet<>();
        for (VentanaDisponibilidad v : servicio.ventanas()) {
            if (v.dia() != dia) {
                continue;
            }
            int finVentana = aMinutos(v.fin());
            // long: con una duración enorme, ini + duracion desbordaría int y el bucle no terminaría pronto.
            for (int ini = aMinutos(v.inicio()); (long) ini + servicio.duracionMin() <= finVentana; ini += PASO_MIN) {
                int fin = ini + servicio.duracionMin();
                if (dias == 0 && ini < minutosAhora) {
                    continue;
                }
                if (!chocaConAlguna(ini, fin, ocupadas)) {
                    inicios.add(ini);
                }
            }
        }
        return inicios.stream().map(m -> LocalTime.of(m / 60, m % 60)).toList();
    }

    private static boolean chocaConAlguna(int ini, int fin, List<Cita> ocupadas) {
        return ocupadas.stream().anyMatch(c -> ini < aMinutos(c.fin()) && fin > aMinutos(c.inicio()));
    }

    static int aMinutos(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }
}
