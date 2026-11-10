package com.datemanager.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.datemanager.dominio.Cita;
import com.datemanager.dominio.Cliente;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.DatosCliente;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.EstadoCita;
import com.datemanager.dominio.Horarios;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.TransicionesCita;
import com.datemanager.infraestructura.persistencia.CitaEntidad;
import com.datemanager.infraestructura.persistencia.CitaRepositorio;
import com.datemanager.infraestructura.persistencia.ErroresBase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de citas. Reservar y mover se validan en el servidor dentro de una transacción que primero bloquea
 * la fila del servicio (su agenda): dos peticiones simultáneas para el mismo servicio se ponen en fila y la segunda
 * ya ve la cita de la primera. El índice único filtrado de la base es la última línea de defensa.
 * Orden de bloqueo, siempre: servicio → cliente / cita.
 */
@Service
public class CitasServicio {

    private static final String MENSAJE_NO_DISPONIBLE = "Ese horario ya no está disponible, elige otro.";
    private static final LocalDate FECHA_MINIMA = LocalDate.of(1, 1, 1);
    private static final LocalDate FECHA_MAXIMA = LocalDate.of(9999, 12, 31);

    private final CitaRepositorio citas;
    private final ServiciosServicio servicios;
    private final ClientesServicio clientes;
    private final Clock reloj;

    public CitasServicio(CitaRepositorio citas, ServiciosServicio servicios, ClientesServicio clientes, Clock reloj) {
        this.citas = citas;
        this.servicios = servicios;
        this.clientes = clientes;
        this.reloj = reloj;
    }

    /**
     * Reserva {@code inicio} el {@code fecha} en el servicio a nombre del cliente (buscado o creado por correo) y la
     * cita nace {@code Confirmada}. {@code enlaceId} es el enlace de invitación usado, o {@code null} desde el panel.
     */
    @Transactional
    public Cita reservar(UUID servicioId, LocalDate fecha, LocalTime inicio, DatosCliente datos, UUID enlaceId) {
        ClientesServicio.validar(datos);
        Servicio servicio = servicios.bloquear(servicioId);
        exigirLibre(servicio, fecha, inicio, null);
        Cliente cliente = clientes.obtenerOCrear(datos);
        Cita cita = new Cita(UUID.randomUUID(), servicio.id(), cliente.id(), fecha, inicio,
                inicio.plusMinutes(servicio.duracionMin()), EstadoCita.Confirmada, ahoraUtc(), null);
        return guardar(CitaEntidad.de(cita, enlaceId));
    }

    /** Mueve una cita {@code Confirmada} a otro horario libre de su servicio (aunque el servicio esté inactivo). */
    @Transactional
    public Cita mover(UUID id, LocalDate fecha, LocalTime inicio) {
        UUID servicioId = citas.servicioDe(id).orElseThrow(CitasServicio::noEncontrada);
        Servicio servicio = servicios.bloquear(servicioId);
        CitaEntidad entidad = citas.bloquear(id).orElseThrow(CitasServicio::noEncontrada);
        Cita actual = entidad.aDominio();
        TransicionesCita.validarMovible(actual);
        exigirLibre(servicio, fecha, inicio, id);
        entidad.copiar(new Cita(actual.id(), actual.servicioId(), actual.clienteId(), fecha, inicio,
                inicio.plusMinutes(servicio.duracionMin()), actual.estado(), actual.creadaEn(), actual.canceladaEn()));
        return guardar(entidad);
    }

    /** Cancela una cita {@code Confirmada}; cancelar una ya cancelada no cambia nada. Libera el horario. */
    @Transactional
    public Cita cancelar(UUID id) {
        CitaEntidad entidad = citas.bloquear(id).orElseThrow(CitasServicio::noEncontrada);
        Cita actual = entidad.aDominio();
        Cita cancelada = TransicionesCita.cancelar(actual, ahoraUtc());
        if (cancelada == actual) {
            return actual;
        }
        entidad.copiar(cancelada);
        return citas.saveAndFlush(entidad).aDominio();
    }

    /** Marca como atendida una cita {@code Confirmada} cuya hora de inicio ya llegó. */
    @Transactional
    public Cita atender(UUID id) {
        CitaEntidad entidad = citas.bloquear(id).orElseThrow(CitasServicio::noEncontrada);
        entidad.copiar(TransicionesCita.atender(entidad.aDominio(), LocalDateTime.now(reloj)));
        return citas.saveAndFlush(entidad).aDominio();
    }

    /** Citas entre {@code desde} y {@code hasta} (incluidos; nulos = sin límite), opcionalmente de un estado. */
    @Transactional(readOnly = true)
    public List<Cita> listar(LocalDate desde, LocalDate hasta, EstadoCita estado) {
        LocalDate inicio = desde == null ? FECHA_MINIMA : desde;
        LocalDate fin = hasta == null ? FECHA_MAXIMA : hasta;
        if (inicio.isAfter(fin)) {
            throw new ErrorNegocio(CodigoError.VALIDACION, "La fecha «desde» no puede ser posterior a «hasta».");
        }
        List<CitaEntidad> encontradas = estado == null
                ? citas.findByFechaBetweenOrderByFechaAscInicioAscIdAsc(inicio, fin)
                : citas.findByFechaBetweenAndEstadoOrderByFechaAscInicioAscIdAsc(inicio, fin, estado.name());
        return encontradas.stream().map(CitaEntidad::aDominio).toList();
    }

    /** Horarios libres del servicio ese día; {@code excluirCitaId} libera el horario de esa cita (al moverla). */
    @Transactional(readOnly = true)
    public List<LocalTime> horariosLibres(UUID servicioId, LocalDate fecha, UUID excluirCitaId) {
        Servicio servicio = servicios.buscar(servicioId);
        return libres(servicio, fecha, excluirCitaId);
    }

    /** La elección del hueco es de {@link Horarios}: aquí solo se comprueba que el inicio pedido esté entre los libres. */
    private void exigirLibre(Servicio servicio, LocalDate fecha, LocalTime inicio, UUID excluirCitaId) {
        if (!libres(servicio, fecha, excluirCitaId).contains(inicio)) {
            throw new ErrorNegocio(CodigoError.HORARIO_NO_DISPONIBLE, MENSAJE_NO_DISPONIBLE);
        }
    }

    private List<LocalTime> libres(Servicio servicio, LocalDate fecha, UUID excluirCitaId) {
        List<Cita> delDia = citas.findByServicioIdAndFecha(servicio.id(), fecha).stream()
                .map(CitaEntidad::aDominio).toList();
        return Horarios.horariosLibres(servicio, fecha, delDia, LocalDateTime.now(reloj), excluirCitaId);
    }

    /**
     * Escribe ya (no al confirmar) para traducir aquí el choque con el índice único filtrado o con el disparador de
     * citas solapadas (tr_cita_sin_solape, que llega como otro tipo de excepción).
     */
    private Cita guardar(CitaEntidad entidad) {
        try {
            return citas.saveAndFlush(entidad).aDominio();
        } catch (DataIntegrityViolationException e) {
            throw new ErrorNegocio(CodigoError.HORARIO_NO_DISPONIBLE, MENSAJE_NO_DISPONIBLE);
        } catch (RuntimeException e) {
            if (ErroresBase.esViolacionDeDisparador(e)) {
                throw new ErrorNegocio(CodigoError.HORARIO_NO_DISPONIBLE, MENSAJE_NO_DISPONIBLE);
            }
            throw e;
        }
    }

    /** "Ahora" en UTC con la precisión de la columna (milisegundos), para que lo devuelto sea lo guardado. */
    private Instant ahoraUtc() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static ErrorNegocio noEncontrada() {
        return new ErrorNegocio(CodigoError.NO_ENCONTRADO, "La cita no existe.");
    }
}
