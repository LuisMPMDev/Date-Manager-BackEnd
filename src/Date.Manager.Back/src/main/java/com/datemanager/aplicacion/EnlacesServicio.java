package com.datemanager.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.datemanager.dominio.Cita;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.DatosCliente;
import com.datemanager.dominio.Enlace;
import com.datemanager.dominio.Enlaces;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.EstadoEnlace;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.Usuario;
import com.datemanager.infraestructura.persistencia.EnlaceRepositorio;
import com.datemanager.infraestructura.persistencia.Transaccion;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;

/**
 * Enlaces de invitación: el panel los crea, lista y desactiva; la zona del cliente los usa para ver servicios
 * activos, horarios libres y reservar. Un uso = una cita: se gasta en la MISMA transacción que la reserva, después
 * de que la reserva pasó todas sus validaciones, así que una reserva fallida no gasta usos.
 * Orden de bloqueo: primero el servicio (lo toma {@link CitasServicio#reservar}), después el enlace (el UPDATE
 * condicional de {@link EnlaceRepositorio#consumirUso}).
 */
public class EnlacesServicio {

    /** Enlace con su estado en el momento de leerlo y el nombre de quien lo creó (para el panel). */
    public record EnlaceVista(Enlace enlace, EstadoEnlace estado, String creadoPorNombre) {
    }

    /** Cita recién reservada desde un enlace, con el nombre de su servicio. */
    public record CitaReservada(Cita cita, String servicio) {
    }

    private static final String SERVICIO_NO_ENCONTRADO = "El servicio no existe.";

    private final Transaccion transaccion;
    private final EnlaceRepositorio enlaces;
    private final UsuarioRepositorio usuarios;
    private final ServiciosServicio servicios;
    private final CitasServicio citas;
    private final Clock reloj;

    public EnlacesServicio(Transaccion transaccion, EnlaceRepositorio enlaces, UsuarioRepositorio usuarios,
            ServiciosServicio servicios, CitasServicio citas, Clock reloj) {
        this.transaccion = Objects.requireNonNull(transaccion);
        this.enlaces = Objects.requireNonNull(enlaces);
        this.usuarios = Objects.requireNonNull(usuarios);
        this.servicios = Objects.requireNonNull(servicios);
        this.citas = Objects.requireNonNull(citas);
        this.reloj = Objects.requireNonNull(reloj);
    }

    // ---------- Panel ----------

    /** Crea un enlace que dura {@code duracionMinutos} (nulo = 24 h) con {@code maxUsos} opcional. */
    public EnlaceVista crear(Integer duracionMinutos, Integer maxUsos, UUID actorId) {
        int duracion = Enlaces.duracionMinutos(duracionMinutos);
        Enlaces.validarMaxUsos(maxUsos);
        return transaccion.escribir(() -> {
            Usuario actor = usuarios.buscar(actorId).orElseThrow(
                    () -> new ErrorNegocio(CodigoError.NO_AUTENTICADO, "Inicia sesión para continuar."));
            Instant ahora = ahora();
            Enlace enlace = new Enlace(UUID.randomUUID(), Enlaces.nuevoCodigo(), actor.id(), ahora,
                    ahora.plus(duracion, ChronoUnit.MINUTES), maxUsos, 0, null);
            enlaces.insertar(enlace);
            return vista(enlace, ahora, nombreDe(actor));
        });
    }

    /** Todos los enlaces, los más nuevos primero. */
    public List<EnlaceVista> listar() {
        return transaccion.leer(() -> {
            List<Enlace> todos = enlaces.todosRecientesPrimero();
            Map<UUID, String> nombres = new HashMap<>();
            for (Usuario u : usuarios.porIds(todos.stream().map(Enlace::creadoPor).distinct().toList())) {
                nombres.put(u.id(), nombreDe(u));
            }
            Instant ahora = ahora();
            return todos.stream().map(e -> vista(e, ahora, nombres.get(e.creadoPor()))).toList();
        });
    }

    /** Desactiva el enlace; si ya lo estaba no cambia nada (conserva la primera fecha). */
    public EnlaceVista desactivar(UUID id) {
        return transaccion.escribir(() -> {
            Instant ahora = ahora();
            enlaces.desactivar(id, utc(ahora));
            Enlace enlace = enlaces.buscar(id)
                    .orElseThrow(() -> new ErrorNegocio(CodigoError.NO_ENCONTRADO, "El enlace no existe."));
            String creador = usuarios.buscar(enlace.creadoPor()).map(EnlacesServicio::nombreDe).orElse(null);
            return vista(enlace, ahora, creador);
        });
    }

    // ---------- Zona del cliente ----------

    /** Estado de un enlace existente (sea cual sea); un código desconocido es {@code ENLACE_NO_ENCONTRADO}. */
    public EnlaceVista estado(String codigo) {
        return transaccion.leer(() -> vista(buscar(codigo), ahora(), null));
    }

    /** Servicios activos, por nombre. Exige un enlace {@code Activo}. */
    public List<Servicio> serviciosActivos(String codigo) {
        return transaccion.leer(() -> {
            exigirActivo(codigo);
            return servicios.listar().stream().filter(Servicio::activo).toList();
        });
    }

    /** Horarios libres de un servicio activo. Exige un enlace {@code Activo}; servicio inactivo = inexistente. */
    public List<LocalTime> horariosLibres(String codigo, UUID servicioId, LocalDate fecha) {
        return transaccion.leer(() -> {
            exigirActivo(codigo);
            exigirServicioActivo(servicioId);
            return citas.horariosLibres(servicioId, fecha, null);
        });
    }

    /**
     * Reserva con el enlace y gasta un uso, todo en una transacción. El enlace se comprueba antes (para fallar
     * pronto) y otra vez al gastar el uso, con el UPDATE condicional, que es lo que decide de verdad.
     */
    public CitaReservada reservar(String codigo, UUID servicioId, LocalDate fecha, LocalTime inicio,
            DatosCliente cliente) {
        return transaccion.escribir(() -> {
            Enlace enlace = exigirActivo(codigo);
            Servicio servicio = exigirServicioActivo(servicioId);
            // Bloquea el servicio, valida el horario y los datos, crea la cita (todavía sin confirmar la transacción).
            Cita cita = citas.reservar(servicioId, fecha, inicio, cliente, enlace.id());
            consumirUso(enlace.id());
            return new CitaReservada(cita, servicio.nombre());
        });
    }

    /**
     * Gasta un uso dentro de la transacción en curso; si el enlace dejó de valer mientras tanto, lanza su
     * {@code ENLACE_*} y la transacción entera (la cita incluida) se deshace.
     */
    private void consumirUso(UUID enlaceId) {
        Instant ahora = ahora();
        if (enlaces.consumirUso(enlaceId, utc(ahora)) == 1) {
            return;
        }
        Enlace actual = enlaces.buscar(enlaceId).orElseThrow(Enlaces::noEncontrado);
        EstadoEnlace estado = Enlaces.estado(actual, ahora);
        // Con el mismo "ahora" el UPDATE y el estado coinciden; si no, lo único que pudo pasar es otro uso.
        throw Enlaces.errorDe(estado == EstadoEnlace.Activo ? EstadoEnlace.Agotado : estado);
    }

    private Enlace exigirActivo(String codigo) {
        Enlace enlace = buscar(codigo);
        EstadoEnlace estado = Enlaces.estado(enlace, ahora());
        if (estado != EstadoEnlace.Activo) {
            throw Enlaces.errorDe(estado);
        }
        return enlace;
    }

    /** Lo que no tiene forma de código ni siquiera llega a la base; todo lo desconocido da el mismo error. */
    private Enlace buscar(String codigo) {
        if (!Enlaces.esCodigoBienFormado(codigo)) {
            throw Enlaces.noEncontrado();
        }
        return enlaces.porCodigo(codigo).orElseThrow(Enlaces::noEncontrado);
    }

    /** Un servicio inactivo responde igual que uno inexistente: lo público no lo ve. */
    private Servicio exigirServicioActivo(UUID servicioId) {
        // buscar() ya responde NO_ENCONTRADO con este mismo mensaje si no existe.
        Servicio servicio = servicios.buscar(servicioId);
        if (!servicio.activo()) {
            throw new ErrorNegocio(CodigoError.NO_ENCONTRADO, SERVICIO_NO_ENCONTRADO);
        }
        return servicio;
    }

    private static EnlaceVista vista(Enlace enlace, Instant ahora, String creadoPorNombre) {
        return new EnlaceVista(enlace, Enlaces.estado(enlace, ahora), creadoPorNombre);
    }

    private static String nombreDe(Usuario usuario) {
        return (usuario.nombre() + " " + usuario.apellido()).strip();
    }

    /** "Ahora" con la precisión de las columnas (milisegundos), para que lo devuelto sea lo guardado. */
    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static LocalDateTime utc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
