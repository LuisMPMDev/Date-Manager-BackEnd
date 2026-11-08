package com.datemanager.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import com.datemanager.PruebaIntegracion;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.DatosCliente;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.VentanaDisponibilidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * El último uso de un enlace con peticiones simultáneas reales: cada hilo llama al caso de uso con su propia
 * transacción y conexión contra SQL Server, todos arrancan a la vez (latch), y se comprueba el estado final de
 * la base: nunca más citas que usos permitidos, y {@code usos} siempre igual al número de citas del enlace.
 */
class UsoConcurrenteEnlaceTest extends PruebaIntegracion {

    private static final int HILOS = 8;
    private static final LocalDate MANANA = LocalDate.of(2026, 11, 10);

    @Autowired
    private EnlacesServicio enlaces;

    @Autowired
    private ServiciosServicio servicios;

    private UUID admin;

    @BeforeEach
    void preparar() {
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 0));
        UUID rol = UUID.randomUUID();
        jdbc.update("INSERT INTO rol (id, nombre, descripcion) VALUES (?, 'Admin', '')", rol);
        jdbc.update("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, 'gestionar_enlaces')", rol);
        admin = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id) "
                + "VALUES (?, 'Ad', 'Min', 'a@x.co', '1', 'admin', 'x', ?)", admin, rol);
    }

    private UUID crearServicio() {
        List<VentanaDisponibilidad> ventanas = IntStream.rangeClosed(1, 7)
                .mapToObj(dia -> new VentanaDisponibilidad(UUID.randomUUID(), dia, LocalTime.of(9, 0),
                        LocalTime.of(12, 0)))
                .toList();
        return servicios.guardar(new Servicio(UUID.randomUUID(), "S", 30, true, BigDecimal.ONE, ventanas)).id();
    }

    private String crearEnlace(Integer maxUsos) {
        return enlaces.crear(null, maxUsos, admin).enlace().codigo();
    }

    private static DatosCliente cliente(int i) {
        return new DatosCliente("Cliente " + i, "Prueba", "300 " + i, "cliente" + i + "@x.co");
    }

    /** Lanza {@code n} tareas que arrancan a la vez y devuelve, por tarea, el resultado o la excepción lanzada. */
    private static List<Object> aLaVez(int n, IntFunction<Callable<Object>> tarea) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(n);
        CountDownLatch listos = new CountDownLatch(n);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Object>> futuros = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<Object> trabajo = tarea.apply(i);
                futuros.add(hilos.submit(() -> {
                    listos.countDown();
                    salida.await();
                    try {
                        return trabajo.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
            salida.countDown();
            List<Object> resultados = new ArrayList<>();
            for (Future<Object> f : futuros) {
                resultados.add(f.get(60, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    private static long exitos(List<Object> resultados) {
        return resultados.stream().filter(r -> !(r instanceof Exception)).count();
    }

    private static void fallanTodosCon(List<Object> resultados, CodigoError codigo) {
        resultados.stream().filter(r -> r instanceof Exception).forEach(r -> assertThat(r)
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.codigo()).isEqualTo(codigo)));
    }

    private int usos(String codigo) {
        return jdbc.queryForObject("SELECT usos FROM enlace_invitacion WHERE codigo = ?", Integer.class, codigo);
    }

    private int citasDelEnlace(String codigo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cita c JOIN enlace_invitacion e ON e.id = c.enlace_id "
                + "WHERE e.codigo = ?", Integer.class, codigo);
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    /** Lo que pide el plan: máximo 1 uso y dos reservas simultáneas a horarios distintos del mismo servicio. */
    @Test
    void conUnUsoYDosReservasALaVezAHorariosDistintosSoloUnaSeCrea() throws Exception {
        UUID servicio = crearServicio();
        String codigo = crearEnlace(1);

        List<Object> resultados = aLaVez(2, i -> () -> enlaces.reservar(codigo, servicio, MANANA,
                i == 0 ? LocalTime.of(9, 0) : LocalTime.of(10, 0), cliente(i)));

        assertThat(exitos(resultados)).isEqualTo(1);
        fallanTodosCon(resultados, CodigoError.ENLACE_AGOTADO);
        assertThat(contar("cita")).isEqualTo(1);
        assertThat(usos(codigo)).isEqualTo(1);
        assertThat(citasDelEnlace(codigo)).isEqualTo(1);
    }

    /**
     * Servicios distintos: el bloqueo del servicio no los pone en fila, así que solo el UPDATE condicional del
     * enlace decide quién gana el último uso.
     */
    @Test
    void conUnUsoYOchoReservasALaVezEnServiciosDistintosSoloUnaSeCrea() throws Exception {
        List<UUID> distintos = IntStream.range(0, HILOS).mapToObj(i -> crearServicio()).toList();
        String codigo = crearEnlace(1);

        List<Object> resultados = aLaVez(HILOS, i -> () -> enlaces.reservar(codigo, distintos.get(i), MANANA,
                LocalTime.of(9, 0), cliente(i)));

        assertThat(exitos(resultados)).isEqualTo(1);
        fallanTodosCon(resultados, CodigoError.ENLACE_AGOTADO);
        assertThat(contar("cita")).isEqualTo(1);
        assertThat(usos(codigo)).isEqualTo(1);
    }

    @Test
    void conTresUsosYOchoReservasALaVezLosUsosNuncaPasanDelMaximo() throws Exception {
        List<UUID> distintos = IntStream.range(0, HILOS).mapToObj(i -> crearServicio()).toList();
        String codigo = crearEnlace(3);

        List<Object> resultados = aLaVez(HILOS, i -> () -> enlaces.reservar(codigo, distintos.get(i), MANANA,
                LocalTime.of(9, 0), cliente(i)));

        assertThat(exitos(resultados)).isEqualTo(3);
        fallanTodosCon(resultados, CodigoError.ENLACE_AGOTADO);
        assertThat(usos(codigo)).isEqualTo(3);
        assertThat(citasDelEnlace(codigo)).isEqualTo(3);
        assertThat(contar("cita")).isEqualTo(3);
    }

    @Test
    void sinLimiteNingunUsoSePierdeConReservasSimultaneas() throws Exception {
        List<UUID> distintos = IntStream.range(0, HILOS).mapToObj(i -> crearServicio()).toList();
        String codigo = crearEnlace(null);

        List<Object> resultados = aLaVez(HILOS, i -> () -> enlaces.reservar(codigo, distintos.get(i), MANANA,
                LocalTime.of(9, 0), cliente(i)));

        assertThat(resultados).noneMatch(r -> r instanceof Exception);
        assertThat(usos(codigo)).isEqualTo(HILOS);
        assertThat(citasDelEnlace(codigo)).isEqualTo(HILOS);
    }

    /** Mismo horario a la vez: pierden por horario, no por el enlace, y los perdedores no gastan usos. */
    @Test
    void lasReservasQuePierdenElHorarioNoGastanUsos() throws Exception {
        UUID servicio = crearServicio();
        String codigo = crearEnlace(5);

        List<Object> resultados = aLaVez(HILOS, i -> () -> enlaces.reservar(codigo, servicio, MANANA,
                LocalTime.of(9, 0), cliente(i)));

        assertThat(exitos(resultados)).isEqualTo(1);
        fallanTodosCon(resultados, CodigoError.HORARIO_NO_DISPONIBLE);
        assertThat(usos(codigo)).isEqualTo(1);
    }

    /** Desactivar mientras se reserva: lo que se haya reservado cuenta su uso, y después nadie más reserva. */
    @Test
    void desactivarALaVezQueSeReservaDejaUsosYCitasCoherentes() throws Exception {
        List<UUID> distintos = IntStream.range(0, HILOS).mapToObj(i -> crearServicio()).toList();
        var enlace = enlaces.crear(null, null, admin).enlace();

        List<Object> resultados = aLaVez(HILOS + 1, i -> i == HILOS
                ? () -> enlaces.desactivar(enlace.id())
                : () -> enlaces.reservar(enlace.codigo(), distintos.get(i), MANANA, LocalTime.of(9, 0), cliente(i)));

        assertThat(resultados.get(HILOS)).isNotInstanceOf(Exception.class);
        fallanTodosCon(resultados, CodigoError.ENLACE_DESACTIVADO);
        assertThat(usos(enlace.codigo())).isEqualTo(citasDelEnlace(enlace.codigo()));
        assertThat(contar("cita")).isEqualTo((int) exitos(resultados) - 1);
        assertThatThrownBy(() -> enlaces.reservar(enlace.codigo(), distintos.get(0), MANANA, LocalTime.of(11, 0),
                cliente(99))).isInstanceOfSatisfying(ErrorNegocio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoError.ENLACE_DESACTIVADO));
    }

    @Test
    void unaReservaFallidaDentroDelCasoDeUsoNoGastaUnUso() {
        UUID servicio = crearServicio();
        String codigo = crearEnlace(1);
        // Otro enlace ocupa el horario sin tocar el primero.
        enlaces.reservar(crearEnlace(null), servicio, MANANA, LocalTime.of(9, 0), cliente(1));

        assertThatThrownBy(() -> enlaces.reservar(codigo, servicio, MANANA, LocalTime.of(9, 0), cliente(2)))
                .isInstanceOfSatisfying(ErrorNegocio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoError.HORARIO_NO_DISPONIBLE));
        assertThat(usos(codigo)).isZero();

        enlaces.reservar(codigo, servicio, MANANA, LocalTime.of(9, 30), cliente(2));
        assertThat(usos(codigo)).isEqualTo(1);
    }
}
