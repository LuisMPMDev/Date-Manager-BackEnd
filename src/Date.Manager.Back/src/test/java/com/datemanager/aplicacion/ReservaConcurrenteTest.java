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
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Concurrencia real contra SQL Server: varios hilos llaman a la vez a los casos de uso (cada uno con su propia
 * transacción y conexión) y se comprueba que la base termina en un estado coherente.
 */
class ReservaConcurrenteTest extends PruebaIntegracion {

    private static final int HILOS = 8;
    private static final LocalDate MANANA = LocalDate.of(2026, 11, 10);

    @Autowired
    private CitasServicio citas;

    @Autowired
    private ServiciosServicio servicios;

    @BeforeEach
    void fijarReloj() {
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 0));
    }

    private UUID crearServicio(int duracion) {
        List<VentanaDisponibilidad> ventanas = IntStream.rangeClosed(1, 7)
                .mapToObj(dia -> new VentanaDisponibilidad(UUID.randomUUID(), dia, LocalTime.of(9, 0),
                        LocalTime.of(12, 0)))
                .toList();
        return servicios.guardar(new Servicio(UUID.randomUUID(), "S", duracion, true, BigDecimal.ONE, ventanas)).id();
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

    private static void sonTodosHorarioNoDisponible(List<Object> resultados) {
        resultados.stream().filter(r -> r instanceof Exception).forEach(r -> assertThat(r)
                .isInstanceOfSatisfying(ErrorNegocio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoError.HORARIO_NO_DISPONIBLE)));
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    @Test
    void ochoHilosReservanElMismoHorarioYSoloUnoGana() throws Exception {
        UUID servicio = crearServicio(30);

        List<Object> resultados = aLaVez(HILOS,
                i -> () -> citas.reservar(servicio, MANANA, LocalTime.of(9, 0), cliente(i), null));

        assertThat(exitos(resultados)).isEqualTo(1);
        sonTodosHorarioNoDisponible(resultados);
        assertThat(contar("cita")).isEqualTo(1);
    }

    /**
     * Inicios distintos que se solapan (09:00 y 09:30 de un servicio de 60 min): el índice único no los
     * distingue, así que solo el bloqueo de la fila del servicio impide que ganen dos.
     */
    @Test
    void ochoHilosReservanHorariosQueSeSolapanYSoloUnoGana() throws Exception {
        UUID servicio = crearServicio(60);

        List<Object> resultados = aLaVez(HILOS, i -> () -> citas.reservar(servicio, MANANA,
                i % 2 == 0 ? LocalTime.of(9, 0) : LocalTime.of(9, 30), cliente(i), null));

        assertThat(exitos(resultados)).isEqualTo(1);
        sonTodosHorarioNoDisponible(resultados);
        assertThat(contar("cita")).isEqualTo(1);
    }

    @Test
    void elMismoHorarioEnServiciosDistintosALaVezGananTodos() throws Exception {
        List<UUID> distintos = IntStream.range(0, 4).mapToObj(i -> crearServicio(30)).toList();

        List<Object> resultados = aLaVez(distintos.size(),
                i -> () -> citas.reservar(distintos.get(i), MANANA, LocalTime.of(9, 0), cliente(i), null));

        assertThat(resultados).noneMatch(r -> r instanceof Exception);
        assertThat(contar("cita")).isEqualTo(4);
    }

    @Test
    void elMismoCorreoNuevoALaVezCreaUnSoloCliente() throws Exception {
        List<UUID> distintos = IntStream.range(0, HILOS).mapToObj(i -> crearServicio(30)).toList();

        List<Object> resultados = aLaVez(HILOS, i -> () -> citas.reservar(distintos.get(i), MANANA,
                LocalTime.of(9, 0), new DatosCliente("Ana " + i, "Ruiz", "300", i % 2 == 0 ? "ANA@x.co" : "ana@x.co "),
                null));

        assertThat(resultados).noneMatch(r -> r instanceof Exception);
        assertThat(contar("cita")).isEqualTo(HILOS);
        assertThat(contar("cliente")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT correo FROM cliente", String.class)).isEqualTo("ana@x.co");
    }

    @Test
    void reservarGuardaElEnlace() {
        UUID servicio = crearServicio(30);
        UUID enlace = UUID.randomUUID();

        citas.reservar(servicio, MANANA, LocalTime.of(9, 0), cliente(1), enlace);

        assertThat(jdbc.queryForObject("SELECT enlace_id FROM cita", UUID.class)).isEqualTo(enlace);
    }

    // ---------- Índice único filtrado (última línea de defensa) ----------

    private void insertarCita(UUID servicio, UUID cliente, String estado) {
        jdbc.update("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en) "
                + "VALUES (?, ?, ?, '2026-11-10', '09:00', '09:30', ?, SYSUTCDATETIME())",
                UUID.randomUUID(), servicio, cliente, estado);
    }

    @Test
    void elIndiceFiltradoRechazaUnaSegundaCitaActivaConElMismoInicioInsertadaPorSql() {
        UUID servicio = crearServicio(30);
        UUID cliente = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, 'A', 'B', '1', 'a@x.co')",
                cliente);
        insertarCita(servicio, cliente, "Cancelada");
        insertarCita(servicio, cliente, "Cancelada");
        insertarCita(servicio, cliente, "Confirmada");

        assertThatThrownBy(() -> insertarCita(servicio, cliente, "Confirmada"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarCita(servicio, cliente, "Atendida"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(contar("cita")).isEqualTo(3);
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

        List<Object> resultados = aLaVez(HILOS, i -> () -> servicios.guardar(
                new Servicio(servicio, "S" + i, 30, true, BigDecimal.ONE, juegos.get(i))));

        assertThat(resultados).noneMatch(r -> r instanceof Exception);
        List<UUID> quedan = jdbc.queryForList("SELECT id FROM ventana_disponibilidad WHERE servicio_id = ?",
                UUID.class, servicio);
        assertThat(juegos).anySatisfy(juego -> assertThat(quedan)
                .containsExactlyInAnyOrderElementsOf(juego.stream().map(VentanaDisponibilidad::id).toList()));
        String nombre = jdbc.queryForObject("SELECT nombre FROM servicio WHERE id = ?", String.class, servicio);
        int ganador = Integer.parseInt(nombre.substring(1));
        assertThat(quedan).containsExactlyInAnyOrderElementsOf(
                juegos.get(ganador).stream().map(VentanaDisponibilidad::id).toList());
    }
}
