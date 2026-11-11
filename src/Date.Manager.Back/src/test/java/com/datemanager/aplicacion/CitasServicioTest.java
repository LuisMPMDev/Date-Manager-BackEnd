package com.datemanager.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.datemanager.PruebaAgenda;
import com.datemanager.dominio.Cita;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.DatosCliente;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.EstadoCita;
import com.datemanager.infraestructura.persistencia.CitaRepositorio;
import com.datemanager.infraestructura.persistencia.ErrorSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Casos de uso de citas (portados de las pruebas del controlador de la aplicación original). */
class CitasServicioTest extends PruebaAgenda {

    /** 30 min, todos los días de 09:00 a 12:00. */
    private UUID corte;
    /** 60 min, todos los días de 09:00 a 12:00. */
    private UUID masaje;

    @BeforeEach
    void preparar() {
        fijarAhora(AHORA);
        corte = crearServicio("Corte", 30, true, "09:00", "12:00");
        masaje = crearServicio("Masaje", 60, true, "09:00", "12:00");
    }

    private Cita reservarEnManana(UUID servicio, String inicio) {
        return reservar(servicio, MANANA, inicio);
    }

    private void esHorarioNoDisponible(org.junit.jupiter.api.function.Executable accion) {
        ErrorNegocio error = exigirError(CodigoError.HORARIO_NO_DISPONIBLE, accion);
        assertEquals("Ese horario ya no está disponible, elige otro.", error.getMessage());
    }

    private void noDisponible(UUID servicio, LocalDate fecha, String inicio) {
        esHorarioNoDisponible(() -> reservar(servicio, fecha, inicio));
    }

    private Cita mover(UUID cita, String fecha, String inicio) {
        return citasServicio.mover(cita, LocalDate.parse(fecha), LocalTime.parse(inicio));
    }

    private static String estadoEnBase(UUID cita) {
        return consultarTexto("SELECT estado FROM cita WHERE id = ?", cita);
    }

    // ---------- Reservar ----------

    @Test
    void reservarDevuelveConfirmadaConElFinSegunLaDuracion() {
        Cita cita = reservarEnManana(masaje, "09:00");

        assertNotNull(cita.id());
        assertEquals(masaje, cita.servicioId());
        assertNotNull(cita.clienteId());
        assertEquals(MANANA, cita.fecha());
        assertEquals(LocalTime.of(9, 0), cita.inicio());
        assertEquals(LocalTime.of(10, 0), cita.fin());
        assertEquals(EstadoCita.Confirmada, cita.estado());
        // 08:00 en Bogotá (UTC-5) son las 13:00 UTC.
        assertEquals(Instant.parse("2026-11-09T13:00:00Z"), cita.creadaEn());
        assertNull(cita.canceladaEn());

        assertEquals(1, contar("cita"));
        assertEquals(1, contar("cliente"));
        Map<String, Object> fila = filas("SELECT * FROM cita").get(0);
        assertEquals("Confirmada", fila.get("estado"));
        assertNull(fila.get("enlace_id"));
        assertEquals(cita.clienteId().toString(), fila.get("cliente_id").toString().toLowerCase());
        // Lo guardado es lo devuelto.
        assertEquals(cita, citasServicio.listar(null, null, null).get(0));
    }

    @Test
    void elMismoHorarioOtraVezEsHorarioNoDisponible() {
        reservarEnManana(corte, "09:00");

        noDisponible(corte, MANANA, "09:00");
        assertEquals(1, contar("cita"));
    }

    @Test
    void unaCitaQueSeSolapaParcialmenteConOtraEsHorarioNoDisponible() {
        reservarEnManana(masaje, "09:00");

        noDisponible(masaje, MANANA, "09:30");
        reservarEnManana(masaje, "10:00");
    }

    @Test
    void unaHoraPasadaDeHoyEsHorarioNoDisponible() {
        fijarAhora(LocalDateTime.of(2026, 11, 9, 10, 15));

        noDisponible(corte, LocalDate.of(2026, 11, 9), "10:00");
        noDisponible(corte, LocalDate.of(2026, 11, 8), "10:00");
        reservar(corte, LocalDate.of(2026, 11, 9), "10:30");
    }

    @Test
    void elDia30SePuedeReservarYElDia31No() {
        noDisponible(corte, LocalDate.of(2026, 12, 10), "09:00");
        reservar(corte, LocalDate.of(2026, 12, 9), "09:00");
    }

    @Test
    void unServicioInactivoNoSeReserva() {
        UUID inactivo = crearServicio("Viejo", 30, false, "09:00", "12:00");

        noDisponible(inactivo, MANANA, "09:00");
        assertEquals(0, contar("cita"));
        assertEquals(0, contar("cliente"));
    }

    @Test
    void unServicioDesconocidoEsNoEncontrado() {
        exigirError(CodigoError.NO_ENCONTRADO, () -> reservar(nuevoId(), MANANA, "09:00"));
        assertEquals(0, contar("cliente"));
    }

    @Test
    void unInicioNoAlineadoOFueraDeLaVentanaEsHorarioNoDisponible() {
        noDisponible(corte, MANANA, "09:15");
        noDisponible(corte, MANANA, "08:30");
        // 12:00 + 30 min se sale de la ventana 09:00–12:00; 11:30 cabe justo.
        noDisponible(corte, MANANA, "12:00");
        noDisponible(masaje, MANANA, "11:30");
        reservarEnManana(corte, "11:30");
        assertEquals(1, contar("cita"));
    }

    @Test
    void unDiaSinVentanaEsHorarioNoDisponible() {
        UUID soloLunes = nuevoId();
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'Lunes', 30, 1, 0)",
                soloLunes);
        ejecutar("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                + "VALUES (?, ?, 1, '09:00', '12:00')", soloLunes, nuevoId());

        noDisponible(soloLunes, MANANA, "09:00");
        reservar(soloLunes, LocalDate.of(2026, 11, 16), "09:00");
    }

    @Test
    void a_las_23_30_en_Bogota_hoyEsElDiaDeBogota() {
        // 23:30 del lunes 9 en Bogotá = 04:30 UTC del martes 10.
        fijarAhora(LocalDateTime.of(2026, 11, 9, 23, 30));
        UUID todoElDia = crearServicio("Todo el día", 30, true, "00:00", "23:59");

        noDisponible(todoElDia, LocalDate.of(2026, 11, 9), "23:00");
        reservarEnManana(todoElDia, "09:00");
        // Si «hoy» fuera el martes (UTC), las 00:00 del martes ya habrían pasado.
        reservarEnManana(todoElDia, "00:00");
        // El día 30 se cuenta desde el lunes 9 (Bogotá): el 9 de diciembre sí, el 10 no.
        reservar(todoElDia, LocalDate.of(2026, 12, 9), "09:00");
        noDisponible(todoElDia, LocalDate.of(2026, 12, 10), "09:00");
    }

    @Test
    void elMismoHorarioEnOtroServicioSePuedeReservar() {
        reservarEnManana(corte, "09:00");

        reservarEnManana(masaje, "09:00");
        assertEquals(2, contar("cita"));
    }

    @Test
    void unCorreoExistenteConOtrasMayusculasYEspaciosReutilizaAlClienteYActualizaSusDatos() {
        Cita primera = citasServicio.reservar(corte, MANANA, LocalTime.of(9, 0),
                cliente("Ana", "ANA@x.co", "300 1"), null);
        Cita segunda = citasServicio.reservar(corte, MANANA, LocalTime.of(10, 0),
                cliente("Ana María", "ana@x.co ", "311 2"), null);

        assertEquals(primera.clienteId(), segunda.clienteId());
        assertEquals(1, contar("cliente"));
        Map<String, Object> fila = filas("SELECT nombre, telefono, correo FROM cliente").get(0);
        assertEquals("Ana María", fila.get("nombre"));
        assertEquals("311 2", fila.get("telefono"));
        assertEquals("ana@x.co", fila.get("correo"));
    }

    @Test
    void unCorreoInvalidoEsValidacionYNoGuardaNada() {
        ErrorNegocio error = exigirError(CodigoError.VALIDACION, () -> citasServicio.reservar(corte, MANANA,
                LocalTime.of(9, 0), cliente("Ana", "ana@", "300 1"), null));

        assertTrue(error.campos().containsKey("correo"));
        assertEquals(0, contar("cita"));
        assertEquals(0, contar("cliente"));
    }

    @Test
    void faltanDatosDelClienteEsValidacionConCampos() {
        ErrorNegocio sinNombre = exigirError(CodigoError.VALIDACION, () -> citasServicio.reservar(corte, MANANA,
                LocalTime.of(9, 0), cliente(" ", "ana@x.co", "300 1"), null));
        ErrorNegocio sinCliente = exigirError(CodigoError.VALIDACION,
                () -> citasServicio.reservar(corte, MANANA, LocalTime.of(9, 0), null, null));

        assertTrue(sinNombre.campos().containsKey("nombre"));
        assertTrue(sinCliente.campos().containsKey("cliente"));
        assertEquals(0, contar("cita"));
    }

    @Test
    void reservarGuardaElEnlaceYUnEnlaceInexistenteFallaPorLaLlaveForanea() {
        UUID enlace = crearEnlace();

        Cita cita = citasServicio.reservar(corte, MANANA, LocalTime.of(9, 0), ana(), enlace);

        assertEquals(enlace.toString(),
                consultarTexto("SELECT CONVERT(NVARCHAR(36), enlace_id) FROM cita WHERE id = ?", cita.id())
                        .toLowerCase());
        // La llave foránea se traduce como el resto de los choques con la base de esta escritura (como en la original).
        esHorarioNoDisponible(() -> citasServicio.reservar(corte, MANANA, LocalTime.of(10, 0), ana(), nuevoId()));
        assertEquals(1, contar("cita"));
    }

    // ---------- Último recurso: el índice filtrado y el disparador ----------

    /** Un repositorio que no ve las citas del día: la validación pasa y solo la base puede frenar el choque. */
    private CitasServicio conLecturaCiega() {
        CitaRepositorio ciego = new CitaRepositorio(tx) {
            @Override
            public List<Cita> delServicioEnFecha(UUID servicioId, LocalDate fecha) {
                return List.of();
            }
        };
        return new CitasServicio(tx, ciego, serviciosServicio, clientesServicio, reloj);
    }

    @Test
    void elIndiceFiltradoYElDisparadorSeTraducenAHorarioNoDisponible() {
        reservarEnManana(masaje, "09:00");
        CitasServicio ciego = conLecturaCiega();

        // Mismo inicio: lo rechaza el índice único filtrado.
        esHorarioNoDisponible(() -> ciego.reservar(masaje, MANANA, LocalTime.of(9, 0), ana(), null));
        // Inicio distinto que se solapa: el índice no lo ve, lo rechaza el disparador tr_cita_sin_solape.
        esHorarioNoDisponible(() -> ciego.reservar(masaje, MANANA, LocalTime.of(9, 30), ana(), null));

        assertEquals(1, contar("cita"));
        assertEquals(10, pool.disponibles(), "ninguna conexión se perdió");
    }

    @Test
    void moverAUnHorarioQueSoloLaBaseVeOcupadoEsHorarioNoDisponible() {
        Cita a = reservarEnManana(masaje, "09:00");
        reservarEnManana(masaje, "11:00");
        CitasServicio ciego = conLecturaCiega();

        esHorarioNoDisponible(() -> ciego.mover(a.id(), MANANA, LocalTime.of(10, 30)));
        esHorarioNoDisponible(() -> ciego.mover(a.id(), MANANA, LocalTime.of(11, 0)));

        assertEquals("09:00", consultarTexto("SELECT CONVERT(NVARCHAR(5), hora_inicio, 108) FROM cita WHERE id = ?",
                a.id()));
    }

    // ---------- Mover ----------

    @Test
    void moverAUnHorarioLibreCambiaFechaInicioYFinYLiberaElAnterior() {
        Cita cita = reservarEnManana(masaje, "09:00");

        Cita movida = mover(cita.id(), "2026-11-11", "10:30");

        assertEquals(cita.id(), movida.id());
        assertEquals(LocalDate.of(2026, 11, 11), movida.fecha());
        assertEquals(LocalTime.of(10, 30), movida.inicio());
        assertEquals(LocalTime.of(11, 30), movida.fin());
        assertEquals(EstadoCita.Confirmada, movida.estado());
        assertEquals(cita.creadaEn(), movida.creadaEn());

        assertTrue(citasServicio.horariosLibres(masaje, MANANA, null).contains(LocalTime.of(9, 0)));
        assertFalse(citasServicio.horariosLibres(masaje, LocalDate.of(2026, 11, 11), null)
                .contains(LocalTime.of(10, 30)));
        assertEquals(1, contar("cita"));
    }

    @Test
    void moverDentroDeSuPropioHorarioSolapadoVale() {
        Cita cita = reservarEnManana(masaje, "09:00");

        // 09:30–10:30 se cruza con la propia cita (09:00–10:00): al moverla se excluye a sí misma.
        assertEquals(LocalTime.of(10, 30), mover(cita.id(), "2026-11-10", "09:30").fin());
    }

    @Test
    void moverAUnHorarioOcupadoOPasadoEsHorarioNoDisponibleYNoCambiaNada() {
        Cita cita = reservarEnManana(masaje, "09:00");
        reservarEnManana(masaje, "11:00");

        esHorarioNoDisponible(() -> mover(cita.id(), "2026-11-10", "10:30"));
        esHorarioNoDisponible(() -> mover(cita.id(), "2026-11-08", "09:00"));
        esHorarioNoDisponible(() -> mover(cita.id(), "2026-11-10", "09:10"));

        assertEquals("09:00", consultarTexto(
                "SELECT CONVERT(NVARCHAR(5), hora_inicio, 108) FROM cita WHERE id = ?", cita.id()));
    }

    @Test
    void moverUnaCitaDeUnServicioInactivoFunciona() {
        Cita cita = reservarEnManana(corte, "09:00");
        ejecutar("UPDATE servicio SET activo = 0 WHERE id = ?", corte);

        assertEquals(LocalTime.of(11, 0), mover(cita.id(), "2026-11-10", "11:00").inicio());
        // Pero no se puede reservar de nuevo en él.
        noDisponible(corte, MANANA, "09:00");
    }

    @Test
    void moverUnaCitaCanceladaOAtendidaEsEstadoInvalido() {
        Cita cancelada = reservarEnManana(corte, "09:00");
        citasServicio.cancelar(cancelada.id());
        Cita atendida = reservar(corte, LocalDate.of(2026, 11, 9), "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 5));
        citasServicio.atender(atendida.id());

        exigirError(CodigoError.ESTADO_INVALIDO, () -> mover(cancelada.id(), "2026-11-10", "11:00"));
        exigirError(CodigoError.ESTADO_INVALIDO, () -> mover(atendida.id(), "2026-11-10", "11:00"));
    }

    // ---------- Cancelar y atender ----------

    @Test
    void cancelarLiberaElHorarioYSePuedeVolverAReservar() {
        Cita cita = reservarEnManana(corte, "09:00");
        assertFalse(citasServicio.horariosLibres(corte, MANANA, null).contains(LocalTime.of(9, 0)));
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 30));

        Cita cancelada = citasServicio.cancelar(cita.id());

        assertEquals(EstadoCita.Cancelada, cancelada.estado());
        assertEquals(Instant.parse("2026-11-09T13:30:00Z"), cancelada.canceladaEn());
        assertTrue(citasServicio.horariosLibres(corte, MANANA, null).contains(LocalTime.of(9, 0)));
        reservarEnManana(corte, "09:00");
        assertEquals(2, contar("cita"));
    }

    @Test
    void cancelarDosVecesNoCambiaNada() {
        Cita cita = reservarEnManana(corte, "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 30));
        citasServicio.cancelar(cita.id());
        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 0));

        Cita otraVez = citasServicio.cancelar(cita.id());

        assertEquals(EstadoCita.Cancelada, otraVez.estado());
        assertEquals(Instant.parse("2026-11-09T13:30:00Z"), otraVez.canceladaEn());
    }

    @Test
    void atenderAntesDeSuHoraEsCitaNoHaIniciadoYDespuesFunciona() {
        Cita cita = reservar(corte, LocalDate.of(2026, 11, 9), "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 59));

        exigirError(CodigoError.CITA_NO_HA_INICIADO, () -> citasServicio.atender(cita.id()));

        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 0));
        assertEquals(EstadoCita.Atendida, citasServicio.atender(cita.id()).estado());
        assertEquals("Atendida", estadoEnBase(cita.id()));
    }

    @Test
    void lasTransicionesInvalidasSonEstadoInvalido() {
        Cita atendida = reservar(corte, LocalDate.of(2026, 11, 9), "09:00");
        Cita cancelada = reservar(corte, LocalDate.of(2026, 11, 9), "10:00");
        citasServicio.cancelar(cancelada.id());
        fijarAhora(LocalDateTime.of(2026, 11, 9, 11, 0));
        citasServicio.atender(atendida.id());

        exigirError(CodigoError.ESTADO_INVALIDO, () -> citasServicio.atender(atendida.id()));
        exigirError(CodigoError.ESTADO_INVALIDO, () -> citasServicio.cancelar(atendida.id()));
        exigirError(CodigoError.ESTADO_INVALIDO, () -> citasServicio.atender(cancelada.id()));
        assertEquals("Atendida", estadoEnBase(atendida.id()));
    }

    @Test
    void unaCitaDesconocidaEsNoEncontrada() {
        UUID nada = nuevoId();

        exigirError(CodigoError.NO_ENCONTRADO, () -> citasServicio.cancelar(nada));
        exigirError(CodigoError.NO_ENCONTRADO, () -> citasServicio.atender(nada));
        ErrorNegocio error = exigirError(CodigoError.NO_ENCONTRADO, () -> mover(nada, "2026-11-10", "09:00"));
        assertEquals("La cita no existe.", error.getMessage());
    }

    // ---------- Listar y horarios ----------

    @Test
    void listarCitasFiltraPorRangoYEstadoYOrdenaPorFechaEHora() {
        Cita c1 = reservar(corte, LocalDate.of(2026, 11, 11), "10:00");
        Cita c2 = reservarEnManana(corte, "11:00");
        Cita c3 = reservarEnManana(masaje, "09:00");
        Cita c4 = reservar(corte, LocalDate.of(2026, 11, 12), "09:00");
        citasServicio.cancelar(c4.id());

        assertEquals(List.of(c3.id(), c2.id(), c1.id(), c4.id()), ids(citasServicio.listar(null, null, null)));
        assertEquals(List.of(c1.id(), c4.id()), ids(citasServicio.listar(LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 11, 12), null)));
        assertEquals(List.of(c3.id(), c2.id()), ids(citasServicio.listar(null, LocalDate.of(2026, 11, 10), null)));
        assertEquals(List.of(c1.id()), ids(citasServicio.listar(LocalDate.of(2026, 11, 11), null,
                EstadoCita.Confirmada)));
        List<Cita> canceladas = citasServicio.listar(null, null, EstadoCita.Cancelada);
        assertEquals(1, canceladas.size());
        assertNotNull(canceladas.get(0).canceladaEn());
    }

    private static List<UUID> ids(List<Cita> citas) {
        return citas.stream().map(Cita::id).toList();
    }

    @Test
    void desdePosteriorAHastaEsValidacion() {
        ErrorNegocio error = exigirError(CodigoError.VALIDACION, () -> citasServicio.listar(
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 11), null));

        assertEquals("La fecha «desde» no puede ser posterior a «hasta».", error.getMessage());
    }

    @Test
    void sinLimitesElListadoAbarcaDelAnio1AlAnio9999() {
        UUID cliente = insertarCliente("A", "B", "a@x.co");
        for (String fecha : new String[] {"0001-01-01", "9999-12-31"}) {
            ejecutar("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en) "
                    + "VALUES (?, ?, ?, ?, '09:00', '09:30', 'Confirmada', SYSUTCDATETIME())", nuevoId(), corte,
                    cliente, fecha);
        }

        assertEquals(List.of(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)),
                citasServicio.listar(null, null, null).stream().map(Cita::fecha).toList());
    }

    @Test
    void horariosLibresDevuelveLasHorasYExcluirCitaIdLiberaLaPropia() {
        Cita cita = reservarEnManana(masaje, "10:00");

        assertEquals(List.of("09:00", "11:00"), horas(citasServicio.horariosLibres(masaje, MANANA, null)));
        assertEquals(List.of("09:00", "09:30", "10:00", "10:30", "11:00"),
                horas(citasServicio.horariosLibres(masaje, MANANA, cita.id())));
    }

    @Test
    void horariosDeUnServicioDesconocidoEsNoEncontrado() {
        exigirError(CodigoError.NO_ENCONTRADO, () -> citasServicio.horariosLibres(nuevoId(), MANANA, null));
    }

    // ---------- Restricciones de la base ----------

    private void insertarCita(UUID servicio, UUID cliente, String inicio, String fin, String estado) {
        ejecutar("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en) "
                + "VALUES (?, ?, ?, '2026-11-10', ?, ?, ?, SYSUTCDATETIME())",
                nuevoId(), servicio, cliente, inicio, fin, estado);
    }

    @Test
    void laBaseRechazaEstadosDesconocidosYFinNoPosteriorAlInicio() {
        UUID cliente = insertarCliente("A", "B", "a@x.co");

        exigirRestriccion(() -> insertarCita(corte, cliente, "09:00", "09:30", "Pendiente"));
        exigirRestriccion(() -> insertarCita(corte, cliente, "09:00", "09:00", "Confirmada"));
        exigirRestriccion(() -> insertarCita(nuevoId(), cliente, "09:00", "09:30", "Confirmada"));
        exigirRestriccion(() -> insertarCita(corte, nuevoId(), "09:00", "09:30", "Confirmada"));
        insertarCita(corte, cliente, "09:00", "09:30", "Confirmada");
    }

    @Test
    void laBaseRechazaDosClientesConElMismoCorreo() {
        insertarCliente("A", "B", "a@x.co");

        exigirRestriccion(() -> insertarCliente("A", "B", "a@x.co"));
    }

    // ---------- Transacciones ----------

    @Test
    void siLaReservaFallaNoQuedaElClienteNuevoNiOtraCita() {
        reservarEnManana(corte, "09:00");
        CitasServicio ciego = conLecturaCiega();

        esHorarioNoDisponible(() -> ciego.reservar(corte, MANANA, LocalTime.of(9, 0),
                cliente("Nuevo", "nuevo@x.co", "1"), null));

        assertEquals(1, contar("cliente"), "el cliente nuevo se deshizo con la reserva");
        assertEquals(1, contar("cita"));
    }

    @Test
    void unErrorDeBaseQueNoEsDeHorarioNoSeTraduce() {
        CitaRepositorio roto = new CitaRepositorio(tx) {
            @Override
            public Cita insertar(Cita cita, UUID enlaceId) {
                throw new ErrorSql(new java.sql.SQLException("objeto no válido", "S0001", 208));
            }
        };
        CitasServicio conFallo = new CitasServicio(tx, roto, serviciosServicio, clientesServicio, reloj);

        assertThrows(ErrorSql.class, () -> conFallo.reservar(corte, MANANA, LocalTime.of(9, 0), ana(), null));

        assertEquals(0, contar("cita"));
        assertEquals(0, contar("cliente"));
        assertEquals(10, pool.disponibles());
    }
}
