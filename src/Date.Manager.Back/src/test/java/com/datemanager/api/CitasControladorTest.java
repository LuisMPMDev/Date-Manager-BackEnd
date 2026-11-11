package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /api/panel/citas} y {@code /api/panel/servicios/{id}/horarios}. Las pruebas de las restricciones de la base
 * que en la versión Spring vivían aquí están en {@code CitasServicioTest}.
 */
class CitasControladorTest extends PruebaPanel {

    private static final String CITAS = "/api/panel/citas";
    /** Lunes 9 de noviembre de 2026, 08:00 en Bogotá. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 8, 0);
    private static final String MANANA = "2026-11-10";

    private UUID gestor;
    /** 30 min, todos los días de 09:00 a 12:00. */
    private UUID corte;
    /** 60 min, todos los días de 09:00 a 12:00. */
    private UUID masaje;

    @BeforeEach
    void preparar() {
        fijarAhora(AHORA);
        gestor = crearUsuario("gestor", crearRol("Gestor", "ver_citas", "gestionar_citas", "atender_citas"));
        corte = crearServicio("Corte", 30, true, "09:00", "12:00");
        masaje = crearServicio("Masaje", 60, true, "09:00", "12:00");
    }

    // ---------- Utilidades ----------

    /** Servicio con la misma ventana los siete días. */
    private UUID crearServicio(String nombre, int duracion, boolean activo, String inicio, String fin) {
        UUID id = UUID.randomUUID();
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, ?, ?, ?, 0)",
                id, nombre, duracion, activo);
        for (int dia = 1; dia <= 7; dia++) {
            ejecutar("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                    + "VALUES (?, ?, ?, ?, ?)", id, UUID.randomUUID(), dia, inicio, fin);
        }
        return id;
    }

    private static Map<String, Object> cliente(String nombre, String correo, String telefono) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("nombre", nombre);
        c.put("apellido", "Pérez");
        c.put("telefono", telefono);
        c.put("correo", correo);
        return c;
    }

    private static Map<String, Object> reserva(UUID servicio, String fecha, String inicio, Map<String, Object> cliente) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("servicioId", servicio == null ? null : servicio.toString());
        r.put("fecha", fecha);
        r.put("inicio", inicio);
        r.put("cliente", cliente);
        return r;
    }

    private static Resp enviar(String ruta, UUID actor, Object cuerpo) {
        Peticion peticion = post(ruta).conToken(tokenDe(actor));
        if (cuerpo != null) {
            peticion.cuerpo(cuerpo);
        }
        return peticion.enviar();
    }

    private Resp reservar(UUID servicio, String fecha, String inicio) {
        return enviar(CITAS, gestor, reserva(servicio, fecha, inicio, cliente("Ana", "ana@x.co", "300 1")));
    }

    private UUID reservarOk(UUID servicio, String fecha, String inicio) {
        return UUID.fromString(exigir(201, reservar(servicio, fecha, inicio)).texto("id"));
    }

    private Resp mover(UUID cita, String fecha, String inicio) {
        return enviar(CITAS + "/" + cita + "/mover", gestor, Map.of("fecha", fecha, "inicio", inicio));
    }

    private Resp horarios(UUID servicio, String fecha) {
        return obtener("/api/panel/servicios/" + servicio + "/horarios?fecha=" + fecha, gestor);
    }

    private static void esHorarioNoDisponible(Resp r) {
        exigir(409, "HORARIO_NO_DISPONIBLE", r);
    }

    private static String inicioEnBase(UUID cita) {
        return consultarTexto("SELECT CONVERT(NVARCHAR(5), hora_inicio, 108) FROM cita WHERE id = ?", cita);
    }

    // ---------- Reservar ----------

    @Test
    void reservarDevuelve201ConfirmadaConElFinSegunLaDuracion() {
        Resp r = exigir(201, reservar(masaje, MANANA, "09:00"));
        assertFalse(r.texto("id").isEmpty());
        assertEquals(masaje.toString(), r.en("servicioId"));
        assertFalse(r.texto("clienteId").isEmpty());
        assertEquals(MANANA, r.en("fecha"));
        assertEquals("09:00", r.en("inicio"));
        assertEquals("10:00", r.en("fin"));
        assertEquals("Confirmada", r.en("estado"));
        // 08:00 en Bogotá (UTC-5) son las 13:00 UTC.
        assertEquals("2026-11-09T13:00:00Z", r.en("creadaEn"));
        assertFalse(r.existe("canceladaEn"));

        assertEquals(1, contar("cita"));
        assertEquals(1, contar("cliente"));
        Map<String, Object> fila = filas("SELECT estado, enlace_id, CONVERT(NVARCHAR(36), cliente_id) AS cliente_id "
                + "FROM cita").get(0);
        assertEquals("Confirmada", fila.get("estado"));
        assertEquals(null, fila.get("enlace_id"));
        assertTrue(fila.get("cliente_id").toString().equalsIgnoreCase(r.texto("clienteId")));
    }

    @Test
    void elMismoHorarioOtraVezEs409() {
        reservarOk(corte, MANANA, "09:00");

        esHorarioNoDisponible(reservar(corte, MANANA, "09:00"));
        assertEquals(1, contar("cita"));
    }

    @Test
    void unaCitaQueSeSolapaParcialmenteConOtraEs409() {
        reservarOk(masaje, MANANA, "09:00");

        esHorarioNoDisponible(reservar(masaje, MANANA, "09:30"));
        exigir(201, reservar(masaje, MANANA, "10:00"));
    }

    @Test
    void unaHoraPasadaDeHoyEs409() {
        fijarAhora(LocalDateTime.of(2026, 11, 9, 10, 15));

        esHorarioNoDisponible(reservar(corte, "2026-11-09", "10:00"));
        esHorarioNoDisponible(reservar(corte, "2026-11-08", "10:00"));
        exigir(201, reservar(corte, "2026-11-09", "10:30"));
    }

    @Test
    void elDia30SePuedeReservarYElDia31No() {
        esHorarioNoDisponible(reservar(corte, "2026-12-10", "09:00"));
        exigir(201, reservar(corte, "2026-12-09", "09:00"));
    }

    @Test
    void unServicioInactivoNoSeReserva() {
        UUID inactivo = crearServicio("Viejo", 30, false, "09:00", "12:00");

        esHorarioNoDisponible(reservar(inactivo, MANANA, "09:00"));
        assertEquals(0, contar("cita"));
        assertEquals(0, contar("cliente"));
    }

    @Test
    void unServicioDesconocidoEs404() {
        exigir(404, "NO_ENCONTRADO", reservar(UUID.randomUUID(), MANANA, "09:00"));
        assertEquals(0, contar("cliente"));
    }

    @Test
    void unInicioNoAlineadoOFueraDeLaVentanaEs409() {
        esHorarioNoDisponible(reservar(corte, MANANA, "09:15"));
        esHorarioNoDisponible(reservar(corte, MANANA, "08:30"));
        // 12:00 + 30 min se sale de la ventana 09:00–12:00; 11:30 cabe justo.
        esHorarioNoDisponible(reservar(corte, MANANA, "12:00"));
        esHorarioNoDisponible(reservar(masaje, MANANA, "11:30"));
        exigir(201, reservar(corte, MANANA, "11:30"));
        assertEquals(1, contar("cita"));
    }

    @Test
    void unDiaSinVentanaEs409() {
        UUID soloLunes = UUID.randomUUID();
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'Lunes', 30, 1, 0)",
                soloLunes);
        ejecutar("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                + "VALUES (?, ?, 1, '09:00', '12:00')", soloLunes, UUID.randomUUID());

        esHorarioNoDisponible(reservar(soloLunes, MANANA, "09:00"));
        exigir(201, reservar(soloLunes, "2026-11-16", "09:00"));
    }

    @Test
    void a_las_23_30_en_Bogota_hoyEsElDiaDeBogota() {
        // 23:30 del lunes 9 en Bogotá = 04:30 UTC del martes 10.
        fijarAhora(LocalDateTime.of(2026, 11, 9, 23, 30));
        UUID todoElDia = crearServicio("Todo el día", 30, true, "00:00", "23:59");

        esHorarioNoDisponible(reservar(todoElDia, "2026-11-09", "23:00"));
        exigir(201, reservar(todoElDia, MANANA, "09:00"));
        // Si «hoy» fuera el martes (UTC), las 00:00 del martes ya habrían pasado.
        exigir(201, reservar(todoElDia, MANANA, "00:00"));
        // El día 30 se cuenta desde el lunes 9 (Bogotá): el 9 de diciembre sí, el 10 no.
        exigir(201, reservar(todoElDia, "2026-12-09", "09:00"));
        esHorarioNoDisponible(reservar(todoElDia, "2026-12-10", "09:00"));
    }

    @Test
    void elMismoHorarioEnOtroServicioSePuedeReservar() {
        reservarOk(corte, MANANA, "09:00");

        exigir(201, reservar(masaje, MANANA, "09:00"));
        assertEquals(2, contar("cita"));
    }

    @Test
    void unCorreoExistenteConOtrasMayusculasYEspaciosReutilizaAlClienteYActualizaSusDatos() {
        Resp primera = exigir(201,
                enviar(CITAS, gestor, reserva(corte, MANANA, "09:00", cliente("Ana", "ANA@x.co", "300 1"))));
        Resp segunda = exigir(201, enviar(CITAS, gestor,
                reserva(corte, MANANA, "10:00", cliente("Ana María", "ana@x.co ", "311 2"))));

        assertEquals(primera.en("clienteId"), segunda.en("clienteId"));
        assertEquals(1, contar("cliente"));
        Map<String, Object> fila = filas("SELECT nombre, telefono, correo FROM cliente").get(0);
        assertEquals("Ana María", fila.get("nombre"));
        assertEquals("311 2", fila.get("telefono"));
        assertEquals("ana@x.co", fila.get("correo"));
    }

    @Test
    void unCorreoInvalidoEs400YNoGuardaNada() {
        Resp r = exigir(400, "VALIDACION",
                enviar(CITAS, gestor, reserva(corte, MANANA, "09:00", cliente("Ana", "ana@", "300 1"))));
        assertFalse(r.texto("campos.correo").isEmpty());
        assertEquals(0, contar("cita"));
        assertEquals(0, contar("cliente"));
    }

    @Test
    void faltanDatosDelClienteOLaReservaEs400ConCampos() {
        Resp nombre = exigir(400, "VALIDACION",
                enviar(CITAS, gestor, reserva(corte, MANANA, "09:00", cliente(" ", "ana@x.co", "300 1"))));
        assertEquals("Escribe el nombre.", nombre.en("campos['cliente.nombre']"));
        Resp servicio = exigir(400,
                enviar(CITAS, gestor, reserva(null, MANANA, "09:00", cliente("Ana", "ana@x.co", "300 1"))));
        assertEquals("Elige un servicio.", servicio.en("campos.servicioId"));
        Resp cliente = exigir(400, enviar(CITAS, gestor, reserva(corte, MANANA, "09:00", null)));
        assertEquals("Escribe los datos del cliente.", cliente.en("campos.cliente"));
        assertEquals(0, contar("cita"));
    }

    @Test
    void unaFechaOHoraMalEscritaEs400() {
        for (String[] malo : new String[][] {{"2026-13-01", "09:00"}, {"2026-02-30", "09:00"}, {"10/11/2026", "09:00"},
                {MANANA, "9:00"}, {MANANA, "24:00"}, {MANANA, "09:00:00"}}) {
            Resp r = exigir(400, "VALIDACION", reservar(corte, malo[0], malo[1]));
            assertTrue(r.existe(malo[0].equals(MANANA) ? "campos.inicio" : "campos.fecha"), r.texto());
        }
        assertEquals(0, contar("cita"));
    }

    @Test
    void dosReservasSimultaneasDelMismoHorarioDanUn201YUn409() throws Exception {
        String token = tokenDe(gestor);
        Callable<Resp> tarea = () -> post(CITAS).conToken(token)
                .cuerpo(reserva(corte, MANANA, "09:00", cliente("Ana", "ana@x.co", "300 1"))).enviar();

        List<Resp> resultados = aLaVez(tarea, tarea);

        List<Integer> estados = resultados.stream().map(Resp::estado).sorted().toList();
        assertEquals(List.of(201, 409), estados, () -> resultados.stream().map(Resp::texto).toList().toString());
        resultados.stream().filter(r -> r.estado() == 409)
                .forEach(r -> assertEquals("HORARIO_NO_DISPONIBLE", r.en("codigo"), r.texto()));
        assertEquals(1, contar("cita"));
        assertEquals(1, contar("cliente"));
    }

    // ---------- Mover ----------

    @Test
    void moverAUnHorarioLibreCambiaFechaInicioYFinYLiberaElAnterior() {
        UUID cita = reservarOk(masaje, MANANA, "09:00");

        Resp r = exigir(200, mover(cita, "2026-11-11", "10:30"));
        assertEquals(cita.toString(), r.en("id"));
        assertEquals("2026-11-11", r.en("fecha"));
        assertEquals("10:30", r.en("inicio"));
        assertEquals("11:30", r.en("fin"));
        assertEquals("Confirmada", r.en("estado"));

        assertTrue(horarios(masaje, MANANA).lista("$").contains("09:00"));
        assertFalse(horarios(masaje, "2026-11-11").lista("$").contains("10:30"));
        assertEquals(1, contar("cita"));
    }

    @Test
    void moverDentroDeSuPropioHorarioSolapadoVale() {
        UUID cita = reservarOk(masaje, MANANA, "09:00");

        // 09:30–10:30 se cruza con la propia cita (09:00–10:00): al moverla se excluye a sí misma.
        assertEquals("10:30", exigir(200, mover(cita, MANANA, "09:30")).en("fin"));
    }

    @Test
    void moverAUnHorarioOcupadoOPasadoEs409YNoCambiaNada() {
        UUID cita = reservarOk(masaje, MANANA, "09:00");
        reservarOk(masaje, MANANA, "11:00");

        esHorarioNoDisponible(mover(cita, MANANA, "10:30"));
        esHorarioNoDisponible(mover(cita, "2026-11-08", "09:00"));
        esHorarioNoDisponible(mover(cita, MANANA, "09:10"));

        assertEquals("09:00", inicioEnBase(cita));
    }

    @Test
    void moverUnaCitaDeUnServicioInactivoFunciona() {
        UUID cita = reservarOk(corte, MANANA, "09:00");
        ejecutar("UPDATE servicio SET activo = 0 WHERE id = ?", corte);

        assertEquals("11:00", exigir(200, mover(cita, MANANA, "11:00")).en("inicio"));
        // Pero no se puede reservar de nuevo en él.
        esHorarioNoDisponible(reservar(corte, MANANA, "09:00"));
    }

    @Test
    void moverUnaCitaCanceladaOAtendidaEs409EstadoInvalido() {
        UUID cancelada = reservarOk(corte, MANANA, "09:00");
        exigir(200, enviar(CITAS + "/" + cancelada + "/cancelar", gestor, null));
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 0));
        UUID atendida = reservarOk(corte, "2026-11-09", "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 5));
        exigir(200, enviar(CITAS + "/" + atendida + "/atender", gestor, null));

        exigir(409, "ESTADO_INVALIDO", mover(cancelada, MANANA, "11:00"));
        exigir(409, "ESTADO_INVALIDO", mover(atendida, MANANA, "11:00"));
    }

    @Test
    void moverSinFechaOConUnaHoraMalEscritaEs400PorCampo() {
        UUID cita = reservarOk(corte, MANANA, "09:00");

        Resp sinFecha = exigir(400, "VALIDACION", enviar(CITAS + "/" + cita + "/mover", gestor, Map.of("inicio", "10:00")));
        assertEquals("Elige la fecha.", sinFecha.en("campos.fecha"));
        Resp hora = exigir(400, "VALIDACION", mover(cita, MANANA, "25:00"));
        assertEquals("Escribe una hora válida con el formato HH:mm.", hora.en("campos.inicio"));
        assertEquals("09:00", inicioEnBase(cita));
    }

    // ---------- Cancelar y atender ----------

    @Test
    void cancelarLiberaElHorarioYSePuedeVolverAReservar() {
        UUID cita = reservarOk(corte, MANANA, "09:00");
        assertFalse(horarios(corte, MANANA).lista("$").contains("09:00"));
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 30));

        Resp r = exigir(200, enviar(CITAS + "/" + cita + "/cancelar", gestor, null));
        assertEquals("Cancelada", r.en("estado"));
        assertEquals("2026-11-09T13:30:00Z", r.en("canceladaEn"));

        assertTrue(exigir(200, horarios(corte, MANANA)).lista("$").contains("09:00"));
        exigir(201, reservar(corte, MANANA, "09:00"));
        assertEquals(2, contar("cita"));
    }

    @Test
    void cancelarDosVecesNoCambiaNada() {
        UUID cita = reservarOk(corte, MANANA, "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 30));
        exigir(200, enviar(CITAS + "/" + cita + "/cancelar", gestor, null));
        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 0));

        Resp r = exigir(200, enviar(CITAS + "/" + cita + "/cancelar", gestor, null));
        assertEquals("Cancelada", r.en("estado"));
        assertEquals("2026-11-09T13:30:00Z", r.en("canceladaEn"));
    }

    @Test
    void atenderAntesDeSuHoraEs409CitaNoHaIniciadoYDespuesFunciona() {
        UUID cita = reservarOk(corte, "2026-11-09", "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 59));

        exigir(409, "CITA_NO_HA_INICIADO", enviar(CITAS + "/" + cita + "/atender", gestor, null));

        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 0));
        assertEquals("Atendida", exigir(200, enviar(CITAS + "/" + cita + "/atender", gestor, null)).en("estado"));
        assertEquals("Atendida", consultarTexto("SELECT estado FROM cita"));
    }

    @Test
    void lasTransicionesInvalidasSon409EstadoInvalido() {
        UUID atendida = reservarOk(corte, "2026-11-09", "09:00");
        UUID cancelada = reservarOk(corte, "2026-11-09", "10:00");
        exigir(200, enviar(CITAS + "/" + cancelada + "/cancelar", gestor, null));
        fijarAhora(LocalDateTime.of(2026, 11, 9, 11, 0));
        exigir(200, enviar(CITAS + "/" + atendida + "/atender", gestor, null));

        exigir(409, "ESTADO_INVALIDO", enviar(CITAS + "/" + atendida + "/atender", gestor, null));
        exigir(409, "ESTADO_INVALIDO", enviar(CITAS + "/" + atendida + "/cancelar", gestor, null));
        exigir(409, "ESTADO_INVALIDO", enviar(CITAS + "/" + cancelada + "/atender", gestor, null));
        assertEquals("Atendida", consultarTexto("SELECT estado FROM cita WHERE id = ?", atendida));
    }

    @Test
    void unaCitaDesconocidaEs404() {
        UUID nada = UUID.randomUUID();
        for (String accion : new String[] {"cancelar", "atender"}) {
            exigir(404, "NO_ENCONTRADO", enviar(CITAS + "/" + nada + "/" + accion, gestor, null));
        }
        exigir(404, "NO_ENCONTRADO", mover(nada, MANANA, "09:00"));
    }

    @Test
    void unIdDeCitaQueNoEsUuidEs400() {
        for (String accion : new String[] {"cancelar", "atender"}) {
            exigir(400, "VALIDACION", enviar(CITAS + "/no-es-uuid/" + accion, gestor, null));
        }
        exigir(400, "VALIDACION", enviar(CITAS + "/1-1-1-1-1/mover", gestor, Map.of("fecha", MANANA, "inicio", "09:00")));
    }

    // ---------- Listar y horarios ----------

    @Test
    void listarCitasFiltraPorRangoYEstadoYOrdenaPorFechaEHora() {
        UUID c1 = reservarOk(corte, "2026-11-11", "10:00");
        UUID c2 = reservarOk(corte, MANANA, "11:00");
        UUID c3 = reservarOk(masaje, MANANA, "09:00");
        UUID c4 = reservarOk(corte, "2026-11-12", "09:00");
        exigir(200, enviar(CITAS + "/" + c4 + "/cancelar", gestor, null));

        assertEquals(ids(c3, c2, c1, c4), exigir(200, obtener(CITAS, gestor)).en("$[*].id"));
        assertEquals(ids(c1, c4), obtener(CITAS + "?desde=2026-11-11&hasta=2026-11-12", gestor).en("$[*].id"));
        assertEquals(ids(c3, c2), obtener(CITAS + "?hasta=2026-11-10", gestor).en("$[*].id"));
        assertEquals(ids(c1), obtener(CITAS + "?desde=2026-11-11&estado=Confirmada", gestor).en("$[*].id"));
        Resp canceladas = obtener(CITAS + "?estado=Cancelada", gestor);
        assertEquals(1, canceladas.lista("$").size());
        assertFalse(canceladas.texto("$[0].canceladaEn").isEmpty());
    }

    private static List<String> ids(UUID... ids) {
        return java.util.Arrays.stream(ids).map(UUID::toString).toList();
    }

    @Test
    void filtrosInvalidosSon400() {
        exigir(400, "VALIDACION", obtener(CITAS + "?estado=Perdida", gestor));
        exigir(400, obtener(CITAS + "?desde=ayer", gestor));
        exigir(400, "VALIDACION", obtener(CITAS + "?desde=2026-11-12&hasta=2026-11-11", gestor));
    }

    @Test
    void unEstadoDesconocidoEs400Validacion() {
        Resp r = exigir(400, "VALIDACION", obtener(CITAS + "?estado=XYZ", gestor));
        assertEquals("La petición no es válida.", r.en("mensaje"));
    }

    @Test
    void unaFechaImposibleEnElFiltroEs400ConElCampo() {
        Resp desde = exigir(400, "VALIDACION", obtener(CITAS + "?desde=2026-02-30", gestor));
        assertEquals("Escribe una fecha válida con el formato AAAA-MM-DD.", desde.en("campos.desde"));
        Resp hasta = exigir(400, "VALIDACION", obtener(CITAS + "?hasta=2026-13-01", gestor));
        assertEquals("Escribe una fecha válida con el formato AAAA-MM-DD.", hasta.en("campos.hasta"));
        Resp fecha = exigir(400, "VALIDACION", horarios(corte, "2026-02-30"));
        assertEquals("Escribe una fecha válida con el formato AAAA-MM-DD.", fecha.en("campos.fecha"));
    }

    @Test
    void filtrosVaciosSeIgnoran() {
        reservarOk(corte, MANANA, "09:00");

        assertEquals(1, exigir(200, obtener(CITAS + "?desde=&hasta=&estado=", gestor)).lista("$").size());
    }

    @Test
    void horariosLibresDevuelveHorasHHmmYExcluirCitaIdLiberaLaPropia() {
        UUID cita = reservarOk(masaje, MANANA, "10:00");

        assertEquals(List.of("09:00", "11:00"), exigir(200, horarios(masaje, MANANA)).en("$"));
        assertEquals(List.of("09:00", "09:30", "10:00", "10:30", "11:00"),
                obtener("/api/panel/servicios/" + masaje + "/horarios?fecha=" + MANANA + "&excluirCitaId=" + cita,
                        gestor).en("$"));
    }

    @Test
    void horariosDeUnServicioDesconocidoEs404YSinFechaEs400() {
        exigir(404, "NO_ENCONTRADO", horarios(UUID.randomUUID(), MANANA));
        exigir(400, "VALIDACION", obtener("/api/panel/servicios/" + corte + "/horarios", gestor));
        exigir(400, "VALIDACION", obtener("/api/panel/servicios/" + corte + "/horarios?fecha=" + MANANA
                + "&excluirCitaId=no-es-uuid", gestor));
    }

    // ---------- Permisos ----------

    @Test
    void verCitasLeePeroNoEscribe() {
        UUID cita = reservarOk(corte, MANANA, "09:00");
        UUID lector = crearUsuario("lector", crearRol("Lector", "ver_citas"));

        assertEquals(1, exigir(200, obtener(CITAS, lector)).lista("$").size());
        exigir(403, "SIN_PERMISO",
                enviar(CITAS, lector, reserva(corte, MANANA, "10:00", cliente("Ana", "ana@x.co", "300 1"))));
        exigir(403, enviar(CITAS + "/" + cita + "/mover", lector, Map.of("fecha", MANANA, "inicio", "11:00")));
        exigir(403, enviar(CITAS + "/" + cita + "/cancelar", lector, null));
        exigir(403, enviar(CITAS + "/" + cita + "/atender", lector, null));
        exigir(403, obtener("/api/panel/servicios/" + corte + "/horarios?fecha=" + MANANA, lector));
        assertEquals(1, contar("cita"));
        assertEquals("Confirmada", consultarTexto("SELECT estado FROM cita"));
    }

    @Test
    void verPanelTambienLeeCitas() {
        UUID panel = crearUsuario("panel", crearRol("Panel", "ver_panel"));

        exigir(200, obtener(CITAS, panel));
        exigir(403, obtener("/api/panel/servicios/" + corte + "/horarios?fecha=" + MANANA, panel));
    }

    @Test
    void atenderCitasSoloAtiende() {
        UUID cita = reservarOk(corte, "2026-11-09", "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 30));
        UUID recepcion = crearUsuario("recepcion", crearRol("Recepción", "atender_citas"));

        exigir(403, enviar(CITAS, recepcion, reserva(corte, MANANA, "10:00", cliente("Ana", "ana@x.co", "300 1"))));
        exigir(403, enviar(CITAS + "/" + cita + "/mover", recepcion, Map.of("fecha", MANANA, "inicio", "11:00")));
        exigir(403, enviar(CITAS + "/" + cita + "/cancelar", recepcion, null));
        exigir(403, obtener(CITAS, recepcion));

        assertEquals("Atendida", exigir(200, enviar(CITAS + "/" + cita + "/atender", recepcion, null)).en("estado"));
    }

    @Test
    void gestionarCitasNoAtiende() {
        UUID cita = reservarOk(corte, "2026-11-09", "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 30));
        UUID agenda = crearUsuario("agenda", crearRol("Agenda", "gestionar_citas"));

        exigir(403, enviar(CITAS + "/" + cita + "/atender", agenda, null));
        exigir(201, enviar(CITAS, agenda, reserva(corte, MANANA, "10:00", cliente("Ana", "ana@x.co", "300 1"))));
        exigir(200, obtener("/api/panel/servicios/" + corte + "/horarios?fecha=" + MANANA, agenda));
    }

    @Test
    void sinPermisosDeCitasEs403() {
        UUID otro = crearUsuario("otro", crearRol("Otro", "gestionar_clientes", "gestionar_servicios"));

        exigir(403, "SIN_PERMISO", obtener(CITAS, otro));
        exigir(403, enviar(CITAS, otro, reserva(corte, MANANA, "10:00", cliente("Ana", "ana@x.co", "300 1"))));
        assertEquals(0, contar("cita"));
    }

    @Test
    void sinTokenEs401() {
        UUID cita = reservarOk(corte, MANANA, "09:00");

        exigir(401, "NO_AUTENTICADO", get(CITAS).enviar());
        exigir(401, post(CITAS).cuerpo(reserva(corte, MANANA, "10:00", cliente("Ana", "ana@x.co", "300 1"))).enviar());
        for (String accion : new String[] {"cancelar", "atender"}) {
            exigir(401, post(CITAS + "/" + cita + "/" + accion).enviar());
        }
        exigir(401, get("/api/panel/servicios/" + corte + "/horarios?fecha=" + MANANA).enviar());
        assertEquals(1, contar("cita"));
    }

    // ---------- Texto y conexiones ----------

    @Test
    void unClienteConEnieYEmojiVaYVuelveIntactoAlReservar() {
        UUID lector = crearUsuario("lector", crearRol("Lector", "ver_citas"));

        exigir(201, enviar(CITAS, gestor, reserva(corte, MANANA, "09:00", cliente("Peña 😀", "pena@x.co", "300 1"))));

        assertEquals("Peña 😀", consultarTexto("SELECT nombre FROM cliente"));
        Resp clientes = exigir(200, obtener("/api/panel/clientes", lector));
        assertEquals("Peña 😀", clientes.en("$[0].nombre"));
        assertTrue(clientes.texto().contains("Peña 😀"), "el texto viaja sin escapar: " + clientes.texto());
    }

    @Test
    void trasTreintaPeticionesConErrorElPoolSigueEntero() {
        int tamano = pool.disponibles();
        UUID cita = reservarOk(corte, MANANA, "09:00");

        for (int i = 0; i < 30; i++) {
            switch (i % 5) {
                case 0 -> esHorarioNoDisponible(reservar(corte, MANANA, "09:00"));
                case 1 -> exigir(404, enviar(CITAS + "/" + UUID.randomUUID() + "/cancelar", gestor, null));
                case 2 -> exigir(400, obtener(CITAS + "?desde=2026-02-30", gestor));
                case 3 -> exigir(409, enviar(CITAS + "/" + cita + "/atender", gestor, null));
                default -> exigir(400, enviar(CITAS, gestor, reserva(corte, MANANA, "09:00", cliente("Ana", "ana@", "1"))));
            }
        }

        assertEquals(tamano, pool.disponibles());
        assertEquals(1, contar("cita"));
    }

    /** Lanza las tareas a la vez, cada una en su hilo, y devuelve sus resultados en el mismo orden. */
    @SafeVarargs
    private static <T> List<T> aLaVez(Callable<T>... tareas) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.length);
        CountDownLatch listos = new CountDownLatch(tareas.length);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> tarea : tareas) {
                futuros.add(hilos.submit(() -> {
                    listos.countDown();
                    salida.await();
                    return tarea.call();
                }));
            }
            assertTrue(listos.await(10, TimeUnit.SECONDS));
            salida.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }
}
