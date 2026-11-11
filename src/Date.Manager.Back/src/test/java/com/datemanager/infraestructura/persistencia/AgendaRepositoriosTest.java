package com.datemanager.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.datemanager.PruebaAgenda;
import com.datemanager.dominio.Cita;
import com.datemanager.dominio.Cliente;
import com.datemanager.dominio.EstadoCita;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.VentanaDisponibilidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Consultas de los repositorios de servicios, ventanas, clientes y citas contra la base real. */
class AgendaRepositoriosTest extends PruebaAgenda {

    private UUID servicio;
    private UUID cliente;

    @BeforeEach
    void sembrar() {
        servicio = crearServicio("S", 30, true, "09:00", "12:00");
        cliente = insertarCliente("Ana", "Ruiz", "ana@x.co");
    }

    private static Instant utc(String texto) {
        return LocalDateTime.parse(texto).toInstant(ZoneOffset.UTC);
    }

    private Cita cita(String fecha, String inicio, EstadoCita estado, String creada, String cancelada) {
        LocalTime ini = LocalTime.parse(inicio);
        return new Cita(nuevoId(), servicio, cliente, LocalDate.parse(fecha), ini, ini.plusMinutes(30), estado,
                utc(creada), cancelada == null ? null : utc(cancelada));
    }

    private Cita insertar(Cita c) {
        return tx.escribir(() -> citasRepo.insertar(c, null));
    }

    private <T> T leer(java.util.function.Supplier<T> consulta) {
        return tx.leer(consulta);
    }

    private static List<UUID> ids(List<Cita> citas) {
        return citas.stream().map(Cita::id).toList();
    }

    // ---------- Citas ----------

    @Test
    void insertarYBloquearHacenIdaYVueltaConMilisegundosYSinZona() {
        Cita c = cita("2026-11-10", "09:00", EstadoCita.Cancelada, "2026-11-09T13:00:00.123",
                "2026-11-09T14:30:00.456");
        insertar(c);

        Cita leida = tx.escribir(() -> citasRepo.bloquear(c.id()).orElseThrow());

        assertEquals(c, leida);
        assertEquals(Optional.empty(), tx.escribir(() -> citasRepo.bloquear(nuevoId())));
    }

    @Test
    void actualizarCambiaHorarioEstadoYCancelacionYUnaCitaAjenaFalla() {
        Cita c = insertar(cita("2026-11-10", "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));
        Cita movida = new Cita(c.id(), servicio, cliente, LocalDate.of(2026, 11, 11), LocalTime.of(10, 30),
                LocalTime.of(11, 0), EstadoCita.Cancelada, c.creadaEn(), utc("2026-11-09T15:00:00.007"));

        tx.escribir(() -> citasRepo.actualizar(movida));

        assertEquals(movida, tx.escribir(() -> citasRepo.bloquear(c.id()).orElseThrow()));
        assertThrows(IllegalStateException.class, () -> tx.escribir(() -> citasRepo.actualizar(cita("2026-11-10",
                "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null))));
    }

    @Test
    void servicioDeDevuelveElServicioSinLeerLaCita() {
        Cita c = insertar(cita("2026-11-10", "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));

        assertEquals(Optional.of(servicio), leer(() -> citasRepo.servicioDe(c.id())));
        assertEquals(Optional.empty(), leer(() -> citasRepo.servicioDe(nuevoId())));
    }

    @Test
    void entreFechasVaOrdenadoPorFechaInicioEIdYRespetaLosExtremosIncluidos() {
        Cita c3 = insertar(cita("2026-11-12", "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));
        Cita c2 = insertar(cita("2026-11-11", "10:00", EstadoCita.Atendida, "2026-11-09T13:00:00", null));
        Cita c1 = insertar(cita("2026-11-11", "09:30", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));
        insertar(cita("2026-11-13", "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));

        List<Cita> r = leer(() -> citasRepo.entreFechas(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12)));

        assertEquals(List.of(c1.id(), c2.id(), c3.id()), ids(r));
    }

    @Test
    void entreFechasConYSinEstado() {
        Cita confirmada = insertar(cita("2026-11-10", "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));
        Cita atendida = insertar(cita("2026-11-10", "10:00", EstadoCita.Atendida, "2026-11-09T13:00:00", null));
        Cita cancelada = insertar(cita("2026-11-10", "09:00", EstadoCita.Cancelada, "2026-11-09T13:00:00",
                "2026-11-09T14:00:00"));
        LocalDate d = LocalDate.of(2026, 11, 10);

        assertEquals(List.of(cancelada.id()), ids(leer(() -> citasRepo.entreFechasConEstado(d, d,
                EstadoCita.Cancelada))));
        assertEquals(Set.of(confirmada.id(), atendida.id()),
                Set.copyOf(ids(leer(() -> citasRepo.entreFechasSinEstado(d, d, EstadoCita.Cancelada)))));
    }

    @Test
    void delServicioEnFechaYDelDiaConEstado() {
        UUID otro = crearServicio("Otro", 30, true, "09:00", "12:00");
        Cita a = insertar(cita("2026-11-10", "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));
        Cita b = insertar(cita("2026-11-10", "10:00", EstadoCita.Cancelada, "2026-11-09T13:00:00",
                "2026-11-09T14:00:00"));
        insertar(cita("2026-11-11", "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));
        tx.escribir(() -> citasRepo.insertar(new Cita(nuevoId(), otro, cliente, LocalDate.of(2026, 11, 10),
                LocalTime.of(9, 0), LocalTime.of(9, 30), EstadoCita.Confirmada, utc("2026-11-09T13:00:00"), null),
                null));

        assertEquals(Set.of(a.id(), b.id()), Set.copyOf(ids(leer(
                () -> citasRepo.delServicioEnFecha(servicio, LocalDate.of(2026, 11, 10))))));
        assertEquals(2, leer(() -> citasRepo.delDiaConEstado(LocalDate.of(2026, 11, 10), EstadoCita.Confirmada))
                .size());
    }

    @Test
    void primerasDespuesDeRespetaElLimiteElOrdenYLaFechaEstricta() {
        Cita c1 = insertar(cita("2026-11-11", "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));
        Cita c2 = insertar(cita("2026-11-11", "10:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));
        Cita c3 = insertar(cita("2026-11-12", "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));
        insertar(cita("2026-11-10", "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));
        insertar(cita("2026-11-11", "11:00", EstadoCita.Atendida, "2026-11-09T13:00:00", null));
        LocalDate dia = LocalDate.of(2026, 11, 10);

        assertEquals(List.of(c1.id(), c2.id(), c3.id()),
                ids(leer(() -> citasRepo.primerasDespuesDe(dia, EstadoCita.Confirmada, 5))));
        assertEquals(List.of(c1.id(), c2.id()),
                ids(leer(() -> citasRepo.primerasDespuesDe(dia, EstadoCita.Confirmada, 2))));
        assertTrue(leer(() -> citasRepo.primerasDespuesDe(dia, EstadoCita.Confirmada, 0)).isEmpty());
    }

    @Test
    void contarYListarPorMomentoDeCreacionOCancelacionUsanElIntervaloAbiertoPorLaIzquierda() {
        Cita enDesde = insertar(cita("2026-11-10", "09:00", EstadoCita.Confirmada, "2026-11-09T10:00:00", null));
        Cita dentro = insertar(cita("2026-11-10", "10:00", EstadoCita.Confirmada, "2026-11-09T11:00:00", null));
        Cita enHasta = insertar(cita("2026-11-10", "11:00", EstadoCita.Confirmada, "2026-11-09T12:00:00", null));
        Cita fuera = insertar(cita("2026-11-11", "09:00", EstadoCita.Confirmada, "2026-11-09T12:00:01", null));
        Cita canceladaDentro = insertar(cita("2026-11-11", "10:00", EstadoCita.Cancelada, "2026-11-01T00:00:00",
                "2026-11-09T11:30:00"));
        LocalDateTime desde = LocalDateTime.parse("2026-11-09T10:00:00");
        LocalDateTime hasta = LocalDateTime.parse("2026-11-09T12:00:00");

        assertEquals(2, leer(() -> citasRepo.contarCreadas(desde, hasta)));
        assertEquals(1, leer(() -> citasRepo.contarCanceladas(desde, hasta)));
        assertEquals(Set.of(dentro.id(), enHasta.id(), canceladaDentro.id()),
                Set.copyOf(ids(leer(() -> citasRepo.creadasOCanceladasEntre(desde, hasta)))));
        assertFalse(ids(leer(() -> citasRepo.creadasOCanceladasEntre(desde, hasta))).contains(enDesde.id()));
        assertFalse(ids(leer(() -> citasRepo.creadasOCanceladasEntre(desde, hasta))).contains(fuera.id()));
    }

    @Test
    void bloquearCitaEsperaAQueLaOtraTransaccionTermine() throws Exception {
        Cita c = insertar(cita("2026-11-10", "09:00", EstadoCita.Confirmada, "2026-11-09T13:00:00", null));
        CountDownLatch bloqueada = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        Thread dueno = Thread.ofVirtual().start(() -> tx.escribir(() -> {
            citasRepo.bloquear(c.id());
            bloqueada.countDown();
            try {
                soltar.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(bloqueada.await(10, TimeUnit.SECONDS));
        long inicio = System.nanoTime();
        Thread espera = Thread.ofVirtual().start(() -> tx.escribir(() -> citasRepo.bloquear(c.id())));
        Thread.sleep(700);

        assertTrue(espera.isAlive(), "la segunda transacción espera el bloqueo (UPDLOCK)");
        soltar.countDown();
        espera.join(20_000);
        dueno.join(20_000);
        assertFalse(espera.isAlive());
        assertTrue(System.nanoTime() - inicio >= TimeUnit.MILLISECONDS.toNanos(600));
        assertEquals(10, pool.disponibles());
    }

    // ---------- Clientes ----------

    @Test
    void clientesBuscarPorCorreoPorIdsYTodos() {
        UUID beto = insertarCliente("Beto", "Díaz", "beto@x.co");

        assertEquals(new Cliente(cliente, "Ana", "Ruiz", "300", "ana@x.co"),
                leer(() -> clientesRepo.buscar(cliente)).orElseThrow());
        assertEquals(beto, leer(() -> clientesRepo.porCorreo("beto@x.co")).orElseThrow().id());
        assertEquals(Optional.empty(), leer(() -> clientesRepo.porCorreo("nadie@x.co")));
        assertEquals(Set.of(cliente, beto), leer(() -> clientesRepo.porIds(List.of(cliente, beto, nuevoId())))
                .stream().map(Cliente::id).collect(java.util.stream.Collectors.toSet()));
        assertTrue(leer(() -> clientesRepo.porIds(List.of())).isEmpty());
        assertEquals(List.of(cliente, beto), leer(() -> clientesRepo.todos()).stream().map(Cliente::id).toList());
    }

    @Test
    void clientesGuardarInsertaActualizaYNormalizaElCorreoYElUnicoDeCorreoSeRespeta() {
        UUID nuevo = nuevoId();

        Cliente guardado = tx.escribir(() -> clientesRepo.guardar(new Cliente(nuevo, "Peña 😀", "Gómez", "1",
                " NUEVO@X.co ")));
        tx.escribir(() -> clientesRepo.guardar(new Cliente(nuevo, "Peña 😀 II", "Gómez", "2", "nuevo@x.co")));

        assertEquals("nuevo@x.co", guardado.correo());
        assertEquals(new Cliente(nuevo, "Peña 😀 II", "Gómez", "2", "nuevo@x.co"),
                leer(() -> clientesRepo.buscar(nuevo)).orElseThrow());
        assertEquals(2, contar("cliente"));
        exigirRestriccion(() -> tx.escribir(() -> clientesRepo.guardar(new Cliente(nuevoId(), "X", "Y", "1",
                "ANA@x.co"))));
    }

    // ---------- Servicios y ventanas ----------

    @Test
    void serviciosGuardarBuscarYTodosConValorDecimal() {
        UUID id = nuevoId();
        VentanaDisponibilidad v = new VentanaDisponibilidad(nuevoId(), 3, LocalTime.of(14, 0), LocalTime.of(17, 30));

        Servicio guardado = tx.escribir(() -> serviciosRepo.guardar(
                new Servicio(id, "Ñandú ✂", 45, false, new BigDecimal("12500.50"), List.of(v))));

        assertEquals(new Servicio(id, "Ñandú ✂", 45, false, new BigDecimal("12500.50"), List.of(v)), guardado);
        assertEquals(guardado, leer(() -> serviciosRepo.buscar(id)).orElseThrow());
        assertEquals(Optional.empty(), leer(() -> serviciosRepo.buscar(nuevoId())));
        assertEquals(2, leer(() -> serviciosRepo.todos()).size());
    }

    @Test
    void ventanasPorServiciosAgrupaYOrdena() {
        UUID otro = crearServicio("Otro", 30, true, "08:00", "09:00");

        var porServicio = leer(() -> ventanas.porServicios(List.of(servicio, otro, nuevoId())));

        assertEquals(7, porServicio.get(servicio).size());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), porServicio.get(servicio).stream()
                .map(VentanaDisponibilidad::dia).toList());
        assertEquals(LocalTime.of(8, 0), porServicio.get(otro).get(0).inicio());
        assertEquals(2, porServicio.size());
        assertTrue(leer(() -> ventanas.porServicios(List.of())).isEmpty());
    }
}
