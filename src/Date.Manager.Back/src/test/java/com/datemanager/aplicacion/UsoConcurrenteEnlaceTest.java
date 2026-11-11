package com.datemanager.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.datemanager.CodificadorFalso;
import com.datemanager.PruebaAgenda;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.DatosCliente;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.VentanaDisponibilidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El último uso de un enlace con peticiones simultáneas reales: cada hilo llama al caso de uso con su propia
 * transacción y conexión contra SQL Server, todos arrancan a la vez (latch), y se comprueba el estado final de
 * la base: nunca más citas que usos permitidos, y {@code usos} siempre igual al número de citas del enlace.
 */
class UsoConcurrenteEnlaceTest extends PruebaAgenda {

    private static final int HILOS = 8;

    private EnlacesServicio enlacesServicio;

    private UUID admin;

    @BeforeEach
    void preparar() {
        fijarAhora(AHORA);
        enlacesServicio = new EnlacesServicio(tx, enlaces, usuarios, serviciosServicio, citasServicio, reloj);
        UUID rol = UUID.randomUUID();
        ejecutar("INSERT INTO rol (id, nombre, descripcion) VALUES (?, 'Admin', '')", rol);
        ejecutar("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, 'gestionar_enlaces')", rol);
        admin = UUID.randomUUID();
        ejecutar("INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id) "
                + "VALUES (?, 'Ad', 'Min', 'a@x.co', '1', 'admin', ?, ?)", admin,
                CodificadorFalso.hash("x"), rol);
    }

    private UUID crearServicio() {
        List<VentanaDisponibilidad> ventanas = IntStream.rangeClosed(1, 7)
                .mapToObj(dia -> new VentanaDisponibilidad(UUID.randomUUID(), dia, LocalTime.of(9, 0),
                        LocalTime.of(12, 0)))
                .toList();
        return serviciosServicio.guardar(new Servicio(UUID.randomUUID(), "S", 30, true, BigDecimal.ONE, ventanas)).id();
    }

    private String crearEnlace(Integer maxUsos) {
        return enlacesServicio.crear(null, maxUsos, admin).enlace().codigo();
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
            assertTrue(listos.await(10, TimeUnit.SECONDS));
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
        resultados.stream().filter(r -> r instanceof Exception)
                .forEach(r -> assertEquals(codigo, assertInstanceOf(ErrorNegocio.class, r).codigo(), r::toString));
    }

    private static int usos(String codigo) {
        return consultarInt("SELECT usos FROM enlace_invitacion WHERE codigo = ?", codigo);
    }

    private static int citasDelEnlace(String codigo) {
        return consultarInt("SELECT COUNT(*) FROM cita c JOIN enlace_invitacion e ON e.id = c.enlace_id "
                + "WHERE e.codigo = ?", codigo);
    }

    /** Lo que pide el plan: máximo 1 uso y dos reservas simultáneas a horarios distintos del mismo servicio. */
    @Test
    void conUnUsoYDosReservasALaVezAHorariosDistintosSoloUnaSeCrea() throws Exception {
        UUID servicio = crearServicio();
        String codigo = crearEnlace(1);

        List<Object> resultados = aLaVez(2, i -> () -> enlacesServicio.reservar(codigo, servicio, MANANA,
                i == 0 ? LocalTime.of(9, 0) : LocalTime.of(10, 0), cliente(i)));

        assertEquals(1, exitos(resultados));
        fallanTodosCon(resultados, CodigoError.ENLACE_AGOTADO);
        assertEquals(1, contar("cita"));
        assertEquals(1, usos(codigo));
        assertEquals(1, citasDelEnlace(codigo));
    }

    /**
     * Servicios distintos: el bloqueo del servicio no los pone en fila, así que solo el UPDATE condicional del
     * enlace decide quién gana el último uso.
     */
    @Test
    void conUnUsoYOchoReservasALaVezEnServiciosDistintosSoloUnaSeCrea() throws Exception {
        List<UUID> distintos = IntStream.range(0, HILOS).mapToObj(i -> crearServicio()).toList();
        String codigo = crearEnlace(1);

        List<Object> resultados = aLaVez(HILOS, i -> () -> enlacesServicio.reservar(codigo, distintos.get(i), MANANA,
                LocalTime.of(9, 0), cliente(i)));

        assertEquals(1, exitos(resultados));
        fallanTodosCon(resultados, CodigoError.ENLACE_AGOTADO);
        assertEquals(1, contar("cita"));
        assertEquals(1, usos(codigo));
    }

    @Test
    void conTresUsosYOchoReservasALaVezLosUsosNuncaPasanDelMaximo() throws Exception {
        List<UUID> distintos = IntStream.range(0, HILOS).mapToObj(i -> crearServicio()).toList();
        String codigo = crearEnlace(3);

        List<Object> resultados = aLaVez(HILOS, i -> () -> enlacesServicio.reservar(codigo, distintos.get(i), MANANA,
                LocalTime.of(9, 0), cliente(i)));

        assertEquals(3, exitos(resultados));
        fallanTodosCon(resultados, CodigoError.ENLACE_AGOTADO);
        assertEquals(3, usos(codigo));
        assertEquals(3, citasDelEnlace(codigo));
        assertEquals(3, contar("cita"));
    }

    @Test
    void sinLimiteNingunUsoSePierdeConReservasSimultaneas() throws Exception {
        List<UUID> distintos = IntStream.range(0, HILOS).mapToObj(i -> crearServicio()).toList();
        String codigo = crearEnlace(null);

        List<Object> resultados = aLaVez(HILOS, i -> () -> enlacesServicio.reservar(codigo, distintos.get(i), MANANA,
                LocalTime.of(9, 0), cliente(i)));

        assertTrue(resultados.stream().noneMatch(r -> r instanceof Exception), resultados::toString);
        assertEquals(HILOS, usos(codigo));
        assertEquals(HILOS, citasDelEnlace(codigo));
    }

    /** Mismo horario a la vez: pierden por horario, no por el enlace, y los perdedores no gastan usos. */
    @Test
    void lasReservasQuePierdenElHorarioNoGastanUsos() throws Exception {
        UUID servicio = crearServicio();
        String codigo = crearEnlace(5);

        List<Object> resultados = aLaVez(HILOS, i -> () -> enlacesServicio.reservar(codigo, servicio, MANANA,
                LocalTime.of(9, 0), cliente(i)));

        assertEquals(1, exitos(resultados));
        fallanTodosCon(resultados, CodigoError.HORARIO_NO_DISPONIBLE);
        assertEquals(1, usos(codigo));
    }

    /** Desactivar mientras se reserva: lo que se haya reservado cuenta su uso, y después nadie más reserva. */
    @Test
    void desactivarALaVezQueSeReservaDejaUsosYCitasCoherentes() throws Exception {
        List<UUID> distintos = IntStream.range(0, HILOS).mapToObj(i -> crearServicio()).toList();
        var enlace = enlacesServicio.crear(null, null, admin).enlace();

        List<Object> resultados = aLaVez(HILOS + 1, i -> i == HILOS
                ? () -> enlacesServicio.desactivar(enlace.id())
                : () -> enlacesServicio.reservar(enlace.codigo(), distintos.get(i), MANANA, LocalTime.of(9, 0), cliente(i)));

        assertFalse(resultados.get(HILOS) instanceof Exception, () -> String.valueOf(resultados.get(HILOS)));
        fallanTodosCon(resultados, CodigoError.ENLACE_DESACTIVADO);
        assertEquals(citasDelEnlace(enlace.codigo()), usos(enlace.codigo()));
        assertEquals((int) exitos(resultados) - 1, contar("cita"));
        assertEquals(CodigoError.ENLACE_DESACTIVADO, assertThrows(ErrorNegocio.class,
                () -> enlacesServicio.reservar(enlace.codigo(), distintos.get(0), MANANA, LocalTime.of(11, 0),
                        cliente(99))).codigo());
    }

    @Test
    void unaReservaFallidaDentroDelCasoDeUsoNoGastaUnUso() {
        UUID servicio = crearServicio();
        String codigo = crearEnlace(1);
        // Otro enlace ocupa el horario sin tocar el primero.
        enlacesServicio.reservar(crearEnlace(null), servicio, MANANA, LocalTime.of(9, 0), cliente(1));

        assertEquals(CodigoError.HORARIO_NO_DISPONIBLE, assertThrows(ErrorNegocio.class,
                () -> enlacesServicio.reservar(codigo, servicio, MANANA, LocalTime.of(9, 0), cliente(2))).codigo());
        assertEquals(0, usos(codigo));

        enlacesServicio.reservar(codigo, servicio, MANANA, LocalTime.of(9, 30), cliente(2));
        assertEquals(1, usos(codigo));
    }
}
