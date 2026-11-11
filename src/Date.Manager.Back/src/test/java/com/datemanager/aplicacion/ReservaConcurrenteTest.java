package com.datemanager.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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

import com.datemanager.PruebaAgenda;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.DatosCliente;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.VentanaDisponibilidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Concurrencia real contra SQL Server: varios hilos llaman a la vez a los casos de uso (cada uno con su propia
 * transacción y conexión) y se comprueba que la base termina en un estado coherente y que el pool queda entero.
 */
class ReservaConcurrenteTest extends PruebaAgenda {

    private static final int HILOS = 8;

    @BeforeEach
    void fijarReloj() {
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 0));
    }

    private static List<VentanaDisponibilidad> todaLaSemana() {
        return IntStream.rangeClosed(1, 7)
                .mapToObj(dia -> new VentanaDisponibilidad(UUID.randomUUID(), dia, LocalTime.of(9, 0),
                        LocalTime.of(12, 0)))
                .toList();
    }

    private UUID crearServicio(int duracion) {
        return serviciosServicio.guardar(
                new Servicio(UUID.randomUUID(), "S", duracion, true, BigDecimal.ONE, todaLaSemana())).id();
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

    private static void sonTodosHorarioNoDisponible(List<Object> resultados) {
        resultados.stream().filter(r -> r instanceof Exception).forEach(r -> {
            ErrorNegocio error = assertInstanceOf(ErrorNegocio.class, r, String.valueOf(r));
            assertEquals(CodigoError.HORARIO_NO_DISPONIBLE, error.codigo());
        });
    }

    private static void sinErrores(List<Object> resultados) {
        List<Object> fallos = resultados.stream().filter(r -> r instanceof Exception).toList();
        assertTrue(fallos.isEmpty(), "fallaron: " + fallos);
    }

    @Test
    void ochoHilosReservanElMismoHorarioYSoloUnoGana() throws Exception {
        UUID servicio = crearServicio(30);

        List<Object> resultados = aLaVez(HILOS,
                i -> () -> citasServicio.reservar(servicio, MANANA, LocalTime.of(9, 0), cliente(i), null));

        assertEquals(1, exitos(resultados));
        sonTodosHorarioNoDisponible(resultados);
        assertEquals(1, contar("cita"));
        assertEquals(10, pool.disponibles(), "el pool quedó entero");
    }

    /**
     * Inicios distintos que se solapan (09:00 y 09:30 de un servicio de 60 min): el índice único no los
     * distingue, así que solo el bloqueo de la fila del servicio impide que ganen dos.
     */
    @Test
    void ochoHilosReservanHorariosQueSeSolapanYSoloUnoGana() throws Exception {
        UUID servicio = crearServicio(60);

        List<Object> resultados = aLaVez(HILOS, i -> () -> citasServicio.reservar(servicio, MANANA,
                i % 2 == 0 ? LocalTime.of(9, 0) : LocalTime.of(9, 30), cliente(i), null));

        assertEquals(1, exitos(resultados));
        sonTodosHorarioNoDisponible(resultados);
        assertEquals(1, contar("cita"));
        assertEquals(10, pool.disponibles());
    }

    @Test
    void elMismoHorarioEnServiciosDistintosALaVezGananTodos() throws Exception {
        List<UUID> distintos = IntStream.range(0, 4).mapToObj(i -> crearServicio(30)).toList();

        List<Object> resultados = aLaVez(distintos.size(),
                i -> () -> citasServicio.reservar(distintos.get(i), MANANA, LocalTime.of(9, 0), cliente(i), null));

        sinErrores(resultados);
        assertEquals(4, contar("cita"));
    }

    @Test
    void elMismoCorreoNuevoALaVezCreaUnSoloCliente() throws Exception {
        List<UUID> distintos = IntStream.range(0, HILOS).mapToObj(i -> crearServicio(30)).toList();

        List<Object> resultados = aLaVez(HILOS, i -> () -> citasServicio.reservar(distintos.get(i), MANANA,
                LocalTime.of(9, 0), new DatosCliente("Ana " + i, "Ruiz", "300", i % 2 == 0 ? "ANA@x.co" : "ana@x.co "),
                null));

        sinErrores(resultados);
        assertEquals(HILOS, contar("cita"));
        assertEquals(1, contar("cliente"));
        assertEquals("ana@x.co", consultarTexto("SELECT correo FROM cliente"));
        assertEquals(10, pool.disponibles());
    }

    @Test
    void reservarGuardaElEnlace() {
        UUID servicio = crearServicio(30);
        UUID enlace = crearEnlace();

        citasServicio.reservar(servicio, MANANA, LocalTime.of(9, 0), cliente(1), enlace);

        assertEquals(enlace.toString(),
                consultarTexto("SELECT CONVERT(NVARCHAR(36), enlace_id) FROM cita").toLowerCase());
    }

    // ---------- Índice único filtrado (última línea de defensa) ----------

    private void insertarCita(UUID servicio, UUID cliente, String estado) {
        // Una cita Cancelada lleva su fecha de cancelación (ck_cita_cancelada_coherente).
        ejecutar("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en, "
                + "cancelada_en) VALUES (?, ?, ?, '2026-11-10', '09:00', '09:30', ?, SYSUTCDATETIME(), "
                + "CASE WHEN ? = 'Cancelada' THEN SYSUTCDATETIME() END)",
                UUID.randomUUID(), servicio, cliente, estado, estado);
    }

    @Test
    void elIndiceFiltradoRechazaUnaSegundaCitaActivaConElMismoInicioInsertadaPorSql() {
        UUID servicio = crearServicio(30);
        UUID cliente = insertarCliente("A", "B", "a@x.co");
        insertarCita(servicio, cliente, "Cancelada");
        insertarCita(servicio, cliente, "Cancelada");
        insertarCita(servicio, cliente, "Confirmada");

        exigirRestriccion(() -> insertarCita(servicio, cliente, "Confirmada"));
        exigirRestriccion(() -> insertarCita(servicio, cliente, "Atendida"));
        assertEquals(3, contar("cita"));
    }

    // ---------- Guardar un servicio existente a la vez ----------

    /** Cada hilo envía su propio juego de ventanas (ids nuevos); al final debe quedar exactamente uno de ellos. */
    @Test
    void variosPutSimultaneosSobreUnServicioExistenteDejanUnSoloJuegoDeVentanas() throws Exception {
        UUID servicio = crearServicio(30);
        List<List<VentanaDisponibilidad>> juegos = IntStream.range(0, HILOS)
                .mapToObj(i -> List.of(
                        new VentanaDisponibilidad(UUID.randomUUID(), 1 + i % 7, LocalTime.of(8, 0), LocalTime.of(9, 0)),
                        new VentanaDisponibilidad(UUID.randomUUID(), 1 + i % 7, LocalTime.of(10, 0),
                                LocalTime.of(11, 0))))
                .toList();

        List<Object> resultados = aLaVez(HILOS, i -> () -> serviciosServicio.guardar(
                new Servicio(servicio, "S" + i, 30, true, BigDecimal.ONE, juegos.get(i))));

        sinErrores(resultados);
        List<String> quedan = columna("SELECT CONVERT(NVARCHAR(36), id) FROM ventana_disponibilidad "
                + "WHERE servicio_id = ?", servicio).stream().map(String::toLowerCase).sorted().toList();
        String nombre = consultarTexto("SELECT nombre FROM servicio WHERE id = ?", servicio);
        int ganador = Integer.parseInt(nombre.substring(1));
        List<String> delGanador = juegos.get(ganador).stream().map(v -> v.id().toString()).sorted().toList();
        assertEquals(delGanador, quedan, "las ventanas son exactamente las del último guardado, sin mezclar");
        assertEquals(10, pool.disponibles());
    }

    /** Reservar y guardar el mismo servicio a la vez: ambos toman el bloqueo del servicio primero, sin interbloqueo. */
    @Test
    void reservarYGuardarElServicioALaVezNoSeInterbloquean() throws Exception {
        UUID servicio = crearServicio(30);
        List<VentanaDisponibilidad> ventanas = todaLaSemana();

        List<Object> resultados = aLaVez(HILOS, i -> i % 2 == 0
                ? () -> citasServicio.reservar(servicio, MANANA, LocalTime.of(9, 0), cliente(i), null)
                : () -> serviciosServicio.guardar(new Servicio(servicio, "S", 30, true, BigDecimal.ONE, ventanas)));

        long reservas = IntStream.range(0, HILOS).filter(i -> i % 2 == 0)
                .filter(i -> !(resultados.get(i) instanceof Exception)).count();
        assertEquals(1, reservas);
        resultados.stream().filter(r -> r instanceof Exception).forEach(r -> assertEquals(
                CodigoError.HORARIO_NO_DISPONIBLE, assertInstanceOf(ErrorNegocio.class, r, String.valueOf(r)).codigo()));
        assertEquals(1, contar("cita"));
        assertEquals(10, pool.disponibles());
    }
}
