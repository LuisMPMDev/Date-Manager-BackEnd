package com.datemanager.infraestructura;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.datemanager.aplicacion.CodificadorClave;
import com.datemanager.dominio.Cita;
import com.datemanager.dominio.Cliente;
import com.datemanager.dominio.EstadoCita;
import com.datemanager.dominio.Horarios;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.Usuario;
import com.datemanager.dominio.Validaciones;
import com.datemanager.dominio.VentanaDisponibilidad;
import com.datemanager.infraestructura.persistencia.CitaRepositorio;
import com.datemanager.infraestructura.persistencia.ClienteRepositorio;
import com.datemanager.infraestructura.persistencia.ErrorSql;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.ServicioRepositorio;
import com.datemanager.infraestructura.persistencia.Transaccion;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;

/**
 * Datos de ejemplo para demostraciones locales (porta {@code core/seed.ts} de la demo Angular): los roles
 * Administrador y Recepción, los usuarios {@code marta} y {@code andres}, tres servicios de 30/60/90 minutos con
 * ventanas distintas, ocho clientes y 23 citas repartidas alrededor de «ahora».
 * <p>
 * {@link Aplicacion} solo la usa con {@code DATEMANAGER_PERFIL=dev}, y solo siembra si la base no tiene ningún dato
 * propio (ni usuarios, roles, servicios, clientes, citas ni enlaces), así que reiniciar no duplica nada y una base con
 * datos reales nunca se toca. Corre antes que {@link AdministradorInicial}: en una base vacía crea a {@code marta} y
 * {@code andres} y el administrador de {@code DATEMANAGER_ADMIN_*} ya no hace falta (ese solo se crea si no hay ningún
 * usuario).
 * <p>
 * La clave de ejemplo es un valor falso de demostración; en la base solo se guarda su hash. Todo «ahora» sale del
 * {@link Clock} inyectado. Las citas pasadas se guardan directamente (reservar rechaza el pasado) pero cumplen las
 * mismas reglas: horarios alineados dentro de una ventana, sin solapes por servicio y estados coherentes con la hora.
 */
public final class SemillaDatos {

    private static final Logger LOG = Logger.getLogger(SemillaDatos.class.getName());

    /** Clave de demostración (falsa, solo para el perfil dev). Nunca se guarda en claro. */
    private static final String CLAVE_DEMO = "demo123";

    private enum Momento {
        PASADA, PROXIMA
    }

    /** Una cita del plan: {@code dia} es el desplazamiento en días respecto a hoy; {@code hueco} el n-ésimo horario libre. */
    private record PlanCita(int dia, int servicio, int hueco, int cliente, EstadoCita estado, Integer creadaHaceH,
            Integer canceladaHaceH, Momento momento) {

        static PlanCita de(int dia, int servicio, int hueco, int cliente, EstadoCita estado) {
            return new PlanCita(dia, servicio, hueco, cliente, estado, null, null, null);
        }

        PlanCita creada(int horas) {
            return new PlanCita(dia, servicio, hueco, cliente, estado, horas, canceladaHaceH, momento);
        }

        PlanCita cancelada(int horas) {
            return new PlanCita(dia, servicio, hueco, cliente, estado, creadaHaceH, horas, momento);
        }

        PlanCita enMomento(Momento nuevo) {
            return new PlanCita(dia, servicio, hueco, cliente, estado, creadaHaceH, canceladaHaceH, nuevo);
        }

        PlanCita con(EstadoCita nuevo, Momento momentoNuevo) {
            return new PlanCita(dia, servicio, hueco, cliente, nuevo, creadaHaceH, canceladaHaceH, momentoNuevo);
        }
    }

    private record Sitio(LocalDate fecha, LocalTime inicio) {
    }

    private static final List<PlanCita> PLAN = List.of(
            // Pasado reciente (últimas tres semanas)
            PlanCita.de(-19, 1, 0, 0, EstadoCita.Atendida),
            PlanCita.de(-18, 0, 2, 1, EstadoCita.Atendida),
            PlanCita.de(-15, 2, 0, 2, EstadoCita.Atendida),
            PlanCita.de(-13, 1, 1, 3, EstadoCita.Atendida),
            PlanCita.de(-10, 0, 0, 4, EstadoCita.Atendida),
            PlanCita.de(-8, 1, 2, 5, EstadoCita.Atendida),
            PlanCita.de(-6, 0, 1, 6, EstadoCita.Cancelada),
            PlanCita.de(-4, 2, 1, 7, EstadoCita.Atendida),
            PlanCita.de(-3, 0, 3, 0, EstadoCita.Atendida),
            PlanCita.de(-2, 1, 0, 1, EstadoCita.Atendida),
            PlanCita.de(-1, 0, 1, 2, EstadoCita.Atendida),
            // Hoy: una ya atendida (si hoy ya terminó algún horario) y otra por venir
            PlanCita.de(0, 1, 2, 4, EstadoCita.Atendida).enMomento(Momento.PASADA),
            PlanCita.de(0, 0, 1, 3, EstadoCita.Confirmada).enMomento(Momento.PROXIMA),
            // Próximos diez días
            PlanCita.de(1, 0, 0, 5, EstadoCita.Confirmada),
            PlanCita.de(1, 1, 4, 6, EstadoCita.Cancelada).creada(72).cancelada(5),
            PlanCita.de(2, 2, 0, 7, EstadoCita.Confirmada).creada(2),
            PlanCita.de(3, 0, 2, 0, EstadoCita.Confirmada).creada(20),
            PlanCita.de(4, 1, 1, 1, EstadoCita.Confirmada),
            PlanCita.de(5, 0, 5, 3, EstadoCita.Confirmada),
            PlanCita.de(6, 2, 1, 2, EstadoCita.Confirmada),
            PlanCita.de(8, 1, 0, 4, EstadoCita.Confirmada),
            PlanCita.de(9, 0, 1, 5, EstadoCita.Confirmada),
            PlanCita.de(10, 1, 2, 6, EstadoCita.Confirmada));

    private final Transaccion transaccion;
    private final RolRepositorio roles;
    private final UsuarioRepositorio usuarios;
    private final ServicioRepositorio servicios;
    private final ClienteRepositorio clientes;
    private final CitaRepositorio citas;
    private final CodificadorClave codificador;
    private final Clock reloj;

    /** Las ventanas se guardan con su servicio ({@link ServicioRepositorio#guardar}). */
    public SemillaDatos(Transaccion transaccion, RolRepositorio roles, UsuarioRepositorio usuarios,
            ServicioRepositorio servicios, ClienteRepositorio clientes, CitaRepositorio citas,
            CodificadorClave codificador, Clock reloj) {
        this.transaccion = transaccion;
        this.roles = roles;
        this.usuarios = usuarios;
        this.servicios = servicios;
        this.clientes = clientes;
        this.citas = citas;
        this.codificador = codificador;
        this.reloj = reloj;
    }

    /** Siembra todo en una transacción si la base no tiene datos propios; devuelve si sembró. */
    public boolean sembrarSiVacia() {
        return transaccion.escribir(() -> {
            if (!estaVacia()) {
                LOG.info("La base ya tiene datos: no se siembran los datos de ejemplo.");
                return false;
            }
            sembrar();
            return true;
        });
    }

    private boolean estaVacia() {
        try (PreparedStatement s = transaccion.conexion().prepareStatement("""
                SELECT CASE WHEN EXISTS (SELECT 1 FROM usuario) OR EXISTS (SELECT 1 FROM rol)
                    OR EXISTS (SELECT 1 FROM servicio) OR EXISTS (SELECT 1 FROM cliente)
                    OR EXISTS (SELECT 1 FROM cita) OR EXISTS (SELECT 1 FROM enlace_invitacion)
                THEN 0 ELSE 1 END
                """);
                ResultSet r = s.executeQuery()) {
            r.next();
            return r.getInt(1) == 1;
        } catch (SQLException e) {
            throw new ErrorSql(e);
        }
    }

    private void sembrar() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Instant instanteAhora = reloj.instant().truncatedTo(ChronoUnit.MILLIS);

        UUID rolAdmin = id("rol-admin");
        UUID rolRecepcion = id("rol-recepcion");
        roles.guardar(new Rol(rolAdmin, "Administrador", "Acceso completo al sistema", EnumSet.allOf(Permiso.class)));
        roles.guardar(new Rol(rolRecepcion, "Recepción", "Atiende la agenda y a los clientes",
                Set.of(Permiso.VER_CITAS, Permiso.GESTIONAR_CITAS, Permiso.ATENDER_CITAS,
                        Permiso.GESTIONAR_CLIENTES)));

        String hash = codificador.codificar(CLAVE_DEMO);
        usuarios.guardar(usuario("usr-marta", "Marta", "Rivas", "300 555 0201", "marta", rolAdmin), hash);
        usuarios.guardar(usuario("usr-andres", "Andrés", "Pardo", "300 555 0202", "andres", rolRecepcion), hash);

        List<Servicio> catalogo = List.of(
                servicio("srv-consulta", "Consulta inicial", 30, 40_000,
                        ventanas("srv-consulta", List.of(1, 2, 3, 4, 5), "09:00-13:00", "15:00-18:00")),
                servicio("srv-estandar", "Sesión estándar", 60, 80_000,
                        ventanas("srv-estandar", List.of(1, 2, 3, 4, 5, 6), "08:00-12:00", "14:00-17:00")),
                servicio("srv-extendida", "Sesión extendida", 90, 110_000, concatenar(
                        ventanas("srv-extendida", List.of(2, 4), "09:00-13:00"),
                        ventanas("srv-extendida", List.of(5), "14:00-18:00"))));
        for (Servicio s : catalogo) {
            servicios.guardar(s);
        }

        List<Cliente> losClientes = List.of(
                cliente("cli-1", "Camila", "Rojas", "300 555 0101", "camila@correo.com"),
                cliente("cli-2", "Daniel", "Ortiz", "300 555 0102", "daniel@correo.com"),
                cliente("cli-3", "Valentina", "Mejía", "300 555 0103", "valentina@correo.com"),
                cliente("cli-4", "Sebastián", "Cruz", "300 555 0104", "sebastian@correo.com"),
                cliente("cli-5", "Isabela", "Torres", "300 555 0105", "isabela@correo.com"),
                cliente("cli-6", "Mateo", "Herrera", "300 555 0106", "mateo@correo.com"),
                cliente("cli-7", "Lucía", "Vargas", "300 555 0107", "lucia@correo.com"),
                cliente("cli-8", "Julián", "Peña", "300 555 0108", "julian@correo.com"));
        losClientes.forEach(clientes::guardar);

        List<Cita> sembradas = new ArrayList<>();
        for (PlanCita base : PLAN) {
            Servicio servicio = catalogo.get(base.servicio());
            PlanCita plan = base;
            Optional<Sitio> sitio = ubicar(servicio, plan, sembradas, ahora);
            if (sitio.isEmpty()) {
                // Todavía no ha terminado ningún horario hoy: en vez de «ya atendida» queda por venir.
                plan = plan.con(EstadoCita.Confirmada, Momento.PROXIMA);
                sitio = ubicar(servicio, plan, sembradas, ahora);
            }
            Sitio donde = sitio.orElseThrow();
            // Si hoy ya no hay horarios y la cita pasó a otro día, no puede figurar como atendida.
            if (plan.estado() == EstadoCita.Atendida && donde.fecha().isAfter(ahora.toLocalDate())) {
                plan = plan.con(EstadoCita.Confirmada, plan.momento());
            }
            sembradas.add(construir(sembradas.size() + 1, servicio, plan, donde,
                    losClientes.get(plan.cliente()).id(), instanteAhora));
        }
        sembradas.forEach(c -> citas.insertar(c, null));

        LOG.log(Level.INFO, "Perfil dev: datos de ejemplo creados (2 roles, 2 usuarios, {0} servicios, {1} clientes, "
                + "{2} citas). Son solo para demostraciones locales: no los uses en producción.",
                new Object[] {catalogo.size(), losClientes.size(), sembradas.size()});
    }

    // ---------- Citas ----------

    private Cita construir(int numero, Servicio servicio, PlanCita plan, Sitio donde, UUID clienteId,
            Instant ahora) {
        Instant creada = plan.creadaHaceH() != null
                ? ahora.minus(plan.creadaHaceH(), ChronoUnit.HOURS)
                : minimo(donde.fecha().atStartOfDay(reloj.getZone()).toInstant().minus(3, ChronoUnit.DAYS),
                        ahora.minus(2, ChronoUnit.DAYS));
        Instant cancelada = null;
        if (plan.estado() == EstadoCita.Cancelada) {
            cancelada = plan.canceladaHaceH() != null
                    ? ahora.minus(plan.canceladaHaceH(), ChronoUnit.HOURS)
                    : creada.plus(1, ChronoUnit.HOURS);
        }
        return new Cita(id("cita-%02d".formatted(numero)), servicio.id(), clienteId,
                donde.fecha(), donde.inicio(), donde.inicio().plusMinutes(servicio.duracionMin()), plan.estado(),
                creada, cancelada);
    }

    private static Instant minimo(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /**
     * Busca el n-ésimo horario libre del día del plan; si ese día el servicio no atiende, avanza (o retrocede, en el
     * pasado) hasta encontrar uno. Todas las citas cuentan como ocupadas, incluso las canceladas, para que ninguna se
     * cruce con otra. Para «hoy» se tiene en cuenta la hora actual: {@code PASADA} busca el último horario que ya
     * terminó (vacío si todavía no hay ninguno) y {@code PROXIMA} uno que aún no empezó; si hoy ya no queda ninguno
     * pasa al siguiente día hábil en vez de quedar en el pasado.
     */
    private Optional<Sitio> ubicar(Servicio servicio, PlanCita plan, List<Cita> existentes, LocalDateTime ahora) {
        int paso = plan.dia() < 0 ? -1 : 1;
        List<Cita> ocupadas = existentes.stream()
                .map(c -> new Cita(c.id(), c.servicioId(), c.clienteId(), c.fecha(), c.inicio(), c.fin(),
                        EstadoCita.Confirmada, c.creadaEn(), null))
                .toList();
        int minutosAhora = ahora.getHour() * 60 + ahora.getMinute();
        for (int i = 0, d = plan.dia(); i < 8; i++, d += paso) {
            LocalDate fecha = ahora.toLocalDate().plusDays(d);
            boolean esHoy = d == 0 && plan.momento() != null;
            if (esHoy && plan.momento() == Momento.PASADA) {
                List<LocalTime> terminados = Horarios
                        .horariosLibres(servicio, fecha, ocupadas, fecha.atStartOfDay(), null).stream()
                        .filter(h -> minutos(h) + servicio.duracionMin() <= minutosAhora).toList();
                return terminados.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new Sitio(fecha, terminados.get(terminados.size() - 1)));
            }
            List<LocalTime> libres = Horarios.horariosLibres(servicio, fecha, ocupadas,
                    esHoy ? ahora : fecha.atStartOfDay(), null);
            if (!libres.isEmpty()) {
                return Optional.of(new Sitio(fecha, libres.get(plan.hueco() % libres.size())));
            }
        }
        throw new IllegalStateException("Sin horarios de ejemplo para " + servicio.nombre());
    }

    private static int minutos(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    // ---------- Constructores de filas ----------

    private static UUID id(String clave) {
        return UUID.nameUUIDFromBytes(("datemanager.semilla." + clave).getBytes(StandardCharsets.UTF_8));
    }

    private static Usuario usuario(String clave, String nombre, String apellido, String telefono, String usuario,
            UUID rolId) {
        return new Usuario(id(clave), nombre, apellido, usuario + "@datemanager.demo", telefono, usuario, rolId);
    }

    private static Cliente cliente(String clave, String nombre, String apellido, String telefono, String correo) {
        return new Cliente(id(clave), nombre, apellido, telefono, Validaciones.normalizarCorreo(correo));
    }

    private static Servicio servicio(String clave, String nombre, int duracion, int valor,
            List<VentanaDisponibilidad> ventanas) {
        return new Servicio(id(clave), nombre, duracion, true, BigDecimal.valueOf(valor), ventanas);
    }

    /** Una ventana por día y tramo (`HH:mm-HH:mm`); su id es estable, derivado del servicio, el día y el tramo. */
    private static List<VentanaDisponibilidad> ventanas(String clave, List<Integer> dias, String... tramos) {
        List<VentanaDisponibilidad> lista = new ArrayList<>();
        for (int dia : dias) {
            for (int i = 0; i < tramos.length; i++) {
                String[] horas = tramos[i].split("-");
                lista.add(new VentanaDisponibilidad(id("%s-d%d-%d".formatted(clave, dia, i + 1)), dia,
                        LocalTime.parse(horas[0]), LocalTime.parse(horas[1])));
            }
        }
        return lista;
    }

    @SafeVarargs
    private static List<VentanaDisponibilidad> concatenar(List<VentanaDisponibilidad>... listas) {
        return Arrays.stream(listas).flatMap(List::stream).toList();
    }
}
