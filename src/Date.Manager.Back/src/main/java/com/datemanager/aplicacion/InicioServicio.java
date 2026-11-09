package com.datemanager.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.datemanager.aplicacion.Tablero.CitasHoy;
import com.datemanager.aplicacion.Tablero.Comparacion;
import com.datemanager.aplicacion.Tablero.ProximaCita;
import com.datemanager.dominio.Cita;
import com.datemanager.dominio.Cliente;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.EstadoCita;
import com.datemanager.dominio.Metricas;
import com.datemanager.dominio.Metricas.Alerta;
import com.datemanager.dominio.Servicio;
import com.datemanager.infraestructura.persistencia.CitaEntidad;
import com.datemanager.infraestructura.persistencia.CitaRepositorio;
import com.datemanager.infraestructura.persistencia.ClienteEntidad;
import com.datemanager.infraestructura.persistencia.ClienteRepositorio;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos del inicio del panel (porta {@code calcularKpis}, {@code serieCitasPorDia} y {@code ocupacionSemana} de la
 * demo). Las cifras las calcula {@link Metricas}; aquí solo se cargan los datos, siempre acotados: un rango de
 * fechas de citas (66 días), cuatro conteos por ventana de tiempo, las citas de las últimas 24 h, las 5 próximas y
 * sus clientes, sin consultas por fila.
 * <p>
 * {@code fecha} es el «hoy» de las cifras por día (citas del día, ocupación, rangos de 30 días y semana). Lo que
 * depende del instante actual (próximas citas, citas nuevas y canceladas de 30 días, alertas) usa siempre el reloj.
 */
@Service
public class InicioServicio {

    private static final int DIAS_PERIODO = 30;
    private static final int PROXIMAS = 5;
    private static final int ANIO_MINIMO = 1900;
    private static final int ANIO_MAXIMO = 2999;

    private final CitaRepositorio citas;
    private final ClienteRepositorio clientes;
    private final ServiciosServicio servicios;
    private final Clock reloj;

    public InicioServicio(CitaRepositorio citas, ClienteRepositorio clientes, ServiciosServicio servicios,
            Clock reloj) {
        this.citas = citas;
        this.clientes = clientes;
        this.servicios = servicios;
        this.reloj = reloj;
    }

    /** El tablero de {@code fecha} (hoy en la zona del negocio si es {@code null}). */
    @Transactional(readOnly = true)
    public Tablero obtener(LocalDate fecha) {
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime ahoraLocal = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MINUTES);
        LocalDate hoy = fecha == null ? ahoraLocal.toLocalDate() : validar(fecha);

        List<Servicio> todos = servicios.listar();
        // Rango [hoy - 59, hoy + 6]: los 60 días de ingresos y la semana (lunes a domingo) de hoy.
        List<Cita> delRango = citas.findByFechaBetweenAndEstadoNot(hoy.minusDays(59), hoy.plusDays(6),
                EstadoCita.Cancelada.name()).stream().map(CitaEntidad::aDominio).toList();

        List<Cita> deHoy = delRango.stream().filter(c -> c.fecha().equals(hoy)).toList();
        CitasHoy citasHoy = new CitasHoy(deHoy.size(),
                (int) deHoy.stream().filter(c -> c.estado() == EstadoCita.Atendida).count());

        double ocupacionHoy = Metricas.ocupacion(todos, delRango, hoy);
        double ocupacionAntes = Metricas.ocupacion(todos, delRango, hoy.minusDays(7));

        Instant hace30 = ahora.minus(DIAS_PERIODO, ChronoUnit.DAYS);
        Instant hace60 = ahora.minus(2L * DIAS_PERIODO, ChronoUnit.DAYS);
        long nuevas = citas.contarCreadas(utc(hace30), utc(ahora));
        long nuevasAntes = citas.contarCreadas(utc(hace60), utc(hace30));
        long canceladas = citas.contarCanceladas(utc(hace30), utc(ahora));
        long canceladasAntes = citas.contarCanceladas(utc(hace60), utc(hace30));

        BigDecimal ingresos = dinero(Metricas.ingresosEstimados(todos, delRango, hoy.minusDays(DIAS_PERIODO - 1L), hoy));
        BigDecimal ingresosAntes = dinero(Metricas.ingresosEstimados(todos, delRango,
                hoy.minusDays(2L * DIAS_PERIODO - 1), hoy.minusDays(DIAS_PERIODO)));

        List<Alerta> alertas = Metricas.alertas(todos, recientes(ahora), ahora);
        List<Alerta> visibles = Metricas.limitarAlertas(alertas);

        List<ProximaCita> proximas = proximas(todos, ahoraLocal);

        return new Tablero(hoy, citasHoy, proximas.isEmpty() ? null : proximas.get(0),
                comparacion(ocupacionHoy, ocupacionAntes, ocupacionHoy, ocupacionAntes),
                comparacion(nuevas, nuevasAntes, nuevas, nuevasAntes),
                comparacion(canceladas, canceladasAntes, canceladas, canceladasAntes),
                comparacion(ingresos.doubleValue(), ingresosAntes.doubleValue(), ingresos, ingresosAntes),
                visibles, alertas.size() - visibles.size(),
                Metricas.citasPorDia(delRango, hoy.minusDays(DIAS_PERIODO - 1L), hoy),
                Metricas.ocupacionSemana(todos, delRango, hoy), proximas);
    }

    /** Citas creadas o canceladas en las últimas 24 h, las más recientes primero (la alerta lleva su propio filtro). */
    private List<Cita> recientes(Instant ahora) {
        Comparator<Cita> porEvento = Comparator
                .comparing((Cita c) -> c.estado() == EstadoCita.Cancelada && c.canceladaEn() != null
                        ? c.canceladaEn() : c.creadaEn())
                .reversed()
                .thenComparing(Cita::id);
        return citas.creadasOCanceladasEntre(utc(ahora.minus(24, ChronoUnit.HOURS)), utc(ahora)).stream()
                .map(CitaEntidad::aDominio).sorted(porEvento).toList();
    }

    /** Las próximas citas confirmadas desde ahora (la hora se compara al minuto, como en la demo), con sus nombres. */
    private List<ProximaCita> proximas(List<Servicio> todos, LocalDateTime ahoraLocal) {
        // Las de hoy se filtran por hora en el dominio; de los días siguientes bastan las 5 primeras.
        String confirmada = EstadoCita.Confirmada.name();
        List<Cita> candidatas = Stream.concat(
                citas.findByFechaAndEstado(ahoraLocal.toLocalDate(), confirmada).stream(),
                citas.findByFechaGreaterThanAndEstadoOrderByFechaAscInicioAscIdAsc(ahoraLocal.toLocalDate(),
                        confirmada, PageRequest.of(0, PROXIMAS)).stream())
                .map(CitaEntidad::aDominio).toList();
        List<Cita> proximas = Metricas.proximasCitas(candidatas, ahoraLocal, PROXIMAS);
        if (proximas.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> nombres = new HashMap<>();
        for (Servicio s : todos) {
            nombres.put(s.id(), s.nombre());
        }
        Map<UUID, Cliente> deClientes = clientes
                .findAllById(proximas.stream().map(Cita::clienteId).distinct().toList()).stream()
                .map(ClienteEntidad::aDominio).collect(Collectors.toMap(Cliente::id, c -> c));
        LocalDate hoy = ahoraLocal.toLocalDate();
        return proximas.stream().map(c -> {
            Cliente cliente = deClientes.get(c.clienteId());
            boolean esHoy = c.fecha().equals(hoy);
            OptionalInt minutosHasta = esHoy
                    ? OptionalInt.of(minutos(c.inicio()) - minutos(ahoraLocal.toLocalTime()))
                    : OptionalInt.empty();
            return new ProximaCita(c, nombres.getOrDefault(c.servicioId(), "Servicio"),
                    cliente == null ? "" : cliente.nombre(), cliente == null ? "" : cliente.apellido(),
                    cliente == null ? "" : cliente.telefono(), esHoy, minutosHasta);
        }).toList();
    }

    private static int minutos(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    /** La variación se calcula sobre los números como {@code double}; {@code actual} y {@code anterior} se publican tal cual. */
    private static <T extends Number> Comparacion<T> comparacion(Number actualNumero, Number anteriorNumero, T actual,
            T anterior) {
        return new Comparacion<>(actual, anterior,
                Metricas.variacion(actualNumero.doubleValue(), anteriorNumero.doubleValue()));
    }

    /** Siempre con 2 decimales, como se guardan los valores de los servicios. */
    private static BigDecimal dinero(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static LocalDateTime utc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    /** Fuera de este rango las consultas por fecha podrían salirse del tipo DATE de la base. */
    private static LocalDate validar(LocalDate fecha) {
        if (fecha.getYear() < ANIO_MINIMO || fecha.getYear() > ANIO_MAXIMO) {
            String mensaje = "La fecha debe estar entre los años " + ANIO_MINIMO + " y " + ANIO_MAXIMO + ".";
            throw new ErrorNegocio(CodigoError.VALIDACION, mensaje, Map.of("fecha", mensaje));
        }
        return fecha;
    }
}
