package com.datemanager.dominio;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Cifras del tablero (porta {@code core/metricas.ts} y las de {@code panel-kpis.ts} / {@code panel-datos.ts}).
 * Java puro: "ahora" y las fechas llegan por parámetro, ya en hora del negocio, y nunca hay NaN ni infinitos.
 */
public final class Metricas {

    /** Cuántas alertas se muestran; el resto se cuenta como excedente. */
    public static final int MAX_ALERTAS = 6;
    /** Una alerta de cita nueva o cancelada dura 24 h. */
    private static final Duration VIGENCIA_ALERTA = Duration.ofHours(24);

    private static final String[] DIAS = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo" };
    private static final String[] MESES = {
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre" };

    private Metricas() {
    }

    public enum TipoAlerta {
        CITA_NUEVA("cita_nueva"),
        CITA_CANCELADA("cita_cancelada"),
        SERVICIO_SIN_HORARIO("servicio_sin_horario");

        private final String codigo;

        TipoAlerta(String codigo) {
            this.codigo = codigo;
        }

        /** Nombre que viaja en el JSON. */
        public String codigo() {
            return codigo;
        }
    }

    /** {@code ref} es la cita o el servicio al que se refiere. */
    public record Alerta(TipoAlerta tipo, String titulo, String detalle, UUID ref) {
    }

    public record CitasDia(LocalDate fecha, int total) {
    }

    /** Ocupación (fracción 0..1) de un servicio. */
    public record OcupacionServicio(UUID servicioId, String nombre, double ocupacion) {
    }

    /** Fracción 0..1 de minutos ocupados sobre minutos de ventanas de servicios activos ese día. */
    public static double ocupacion(List<Servicio> servicios, List<Cita> citas, LocalDate fecha) {
        return ocupacionDe(servicios, citas, fecha, fecha);
    }

    /** Lo mismo para un solo servicio, entre {@code desde} y {@code hasta} (ambos incluidos). */
    public static double ocupacionServicio(Servicio servicio, List<Cita> citas, LocalDate desde, LocalDate hasta) {
        return ocupacionDe(List.of(servicio), citas, desde, hasta);
    }

    /**
     * Suma del valor de las citas no canceladas entre {@code desde} y {@code hasta} (ambos incluidos), en
     * {@link BigDecimal}: una cita de un servicio que no se conoce suma cero.
     */
    public static BigDecimal ingresosEstimados(List<Servicio> servicios, List<Cita> citas, LocalDate desde,
            LocalDate hasta) {
        Map<UUID, BigDecimal> valores = new HashMap<>();
        for (Servicio s : servicios) {
            valores.put(s.id(), s.valor());
        }
        BigDecimal suma = BigDecimal.ZERO;
        for (Cita c : citas) {
            if (c.estado() != EstadoCita.Cancelada && !c.fecha().isBefore(desde) && !c.fecha().isAfter(hasta)) {
                suma = suma.add(valores.getOrDefault(c.servicioId(), BigDecimal.ZERO));
            }
        }
        return suma;
    }

    /** Citas no canceladas de cada día de {@code desde} a {@code hasta}, ambos incluidos (cero en los días vacíos). */
    public static List<CitasDia> citasPorDia(List<Cita> citas, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, Integer> porDia = new HashMap<>();
        for (Cita c : citas) {
            if (c.estado() != EstadoCita.Cancelada) {
                porDia.merge(c.fecha(), 1, Integer::sum);
            }
        }
        List<CitasDia> resultado = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            resultado.add(new CitasDia(dia, porDia.getOrDefault(dia, 0)));
        }
        return resultado;
    }

    /**
     * Citas nuevas y canceladas de las últimas 24 h (en el orden recibido) y, al final, un aviso por cada servicio
     * activo sin ninguna ventana de disponibilidad. Otros casos (sin ventana hoy, todo ocupado) no avisan.
     */
    public static List<Alerta> alertas(List<Servicio> servicios, List<Cita> citas, Instant ahora) {
        Map<UUID, String> nombres = new HashMap<>();
        for (Servicio s : servicios) {
            nombres.put(s.id(), s.nombre());
        }
        List<Alerta> resultado = new ArrayList<>();
        for (Cita c : citas) {
            String detalle = nombres.getOrDefault(c.servicioId(), "Servicio") + ", " + fechaLarga(c.fecha())
                    + " a las " + hhmm(c.inicio());
            if (c.estado() == EstadoCita.Cancelada) {
                if (reciente(c.canceladaEn(), ahora)) {
                    resultado.add(new Alerta(TipoAlerta.CITA_CANCELADA, "Cita cancelada", detalle, c.id()));
                }
            } else if (reciente(c.creadaEn(), ahora)) {
                resultado.add(new Alerta(TipoAlerta.CITA_NUEVA, "Cita nueva", detalle, c.id()));
            }
        }
        for (Servicio s : servicios) {
            if (s.activo() && s.ventanas().isEmpty()) {
                resultado.add(new Alerta(TipoAlerta.SERVICIO_SIN_HORARIO, "Servicio sin horario",
                        s.nombre() + " no tiene horarios de atención", s.id()));
            }
        }
        return resultado;
    }

    /** Las primeras {@link #MAX_ALERTAS} alertas, en el orden recibido. */
    public static List<Alerta> limitarAlertas(List<Alerta> todas) {
        return todas.size() <= MAX_ALERTAS ? todas : List.copyOf(todas.subList(0, MAX_ALERTAS));
    }

    /** Variación relativa como fracción (0.2 = +20 %); vacía si el valor previo es 0. */
    public static OptionalDouble variacion(double actual, double previo) {
        return previo == 0 ? OptionalDouble.empty() : OptionalDouble.of((actual - previo) / previo);
    }

    /**
     * Citas confirmadas que aún no empiezan (una que empieza ahora mismo cuenta), de la más cercana a la más lejana,
     * hasta {@code limite}.
     */
    public static List<Cita> proximasCitas(List<Cita> citas, LocalDateTime ahora, int limite) {
        return citas.stream()
                .filter(c -> c.estado() == EstadoCita.Confirmada
                        && !LocalDateTime.of(c.fecha(), c.inicio()).isBefore(ahora))
                .sorted(Comparator.comparing(Cita::fecha).thenComparing(Cita::inicio))
                .limit(limite)
                .toList();
    }

    /** Ocupación de cada servicio activo en la semana de lunes a domingo que contiene {@code hoy}. */
    public static List<OcupacionServicio> ocupacionSemana(List<Servicio> servicios, List<Cita> citas, LocalDate hoy) {
        LocalDate lunes = lunesDeLaSemana(hoy);
        LocalDate domingo = lunes.plusDays(6);
        return servicios.stream()
                .filter(Servicio::activo)
                .map(s -> new OcupacionServicio(s.id(), s.nombre(), ocupacionServicio(s, citas, lunes, domingo)))
                .toList();
    }

    public static LocalDate lunesDeLaSemana(LocalDate fecha) {
        return fecha.minusDays(fecha.getDayOfWeek().getValue() - 1L);
    }

    /** {@code 2026-11-10} -> {@code Martes 10 de noviembre} (tablas propias: no depende del idioma del entorno). */
    static String fechaLarga(LocalDate fecha) {
        return DIAS[fecha.getDayOfWeek().getValue() - 1] + " " + fecha.getDayOfMonth() + " de "
                + MESES[fecha.getMonthValue() - 1];
    }

    private static String hhmm(LocalTime hora) {
        return "%02d:%02d".formatted(hora.getHour(), hora.getMinute());
    }

    private static boolean reciente(Instant marca, Instant ahora) {
        if (marca == null) {
            return false;
        }
        Duration edad = Duration.between(marca, ahora);
        return !edad.isNegative() && edad.compareTo(VIGENCIA_ALERTA) < 0;
    }

    /**
     * Minutos reservados (citas no canceladas) sobre minutos de ventanas, sumando solo los servicios activos que
     * tengan ventanas en esas fechas: un servicio sin minutos disponibles no aporta ni a un lado ni al otro.
     */
    private static double ocupacionDe(List<Servicio> servicios, List<Cita> citas, LocalDate desde, LocalDate hasta) {
        long disponibles = 0;
        long ocupados = 0;
        for (Servicio s : servicios) {
            long disponiblesDelServicio = minutosDisponibles(s, desde, hasta);
            if (disponiblesDelServicio == 0) {
                continue;
            }
            disponibles += disponiblesDelServicio;
            for (Cita c : citas) {
                if (c.servicioId().equals(s.id()) && c.estado() != EstadoCita.Cancelada
                        && !c.fecha().isBefore(desde) && !c.fecha().isAfter(hasta)) {
                    ocupados += minutos(c.inicio(), c.fin());
                }
            }
        }
        return disponibles == 0 ? 0 : (double) ocupados / disponibles;
    }

    private static long minutosDisponibles(Servicio servicio, LocalDate desde, LocalDate hasta) {
        if (!servicio.activo()) {
            return 0;
        }
        long total = 0;
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            int diaIso = dia.getDayOfWeek().getValue();
            for (VentanaDisponibilidad v : servicio.ventanas()) {
                if (v.dia() == diaIso) {
                    total += minutos(v.inicio(), v.fin());
                }
            }
        }
        return total;
    }

    private static long minutos(LocalTime inicio, LocalTime fin) {
        return Duration.between(inicio, fin).toMinutes();
    }
}
