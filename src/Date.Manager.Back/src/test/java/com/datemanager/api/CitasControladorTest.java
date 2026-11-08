package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

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
        jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, ?, ?, ?, 0)",
                id, nombre, duracion, activo);
        for (int dia = 1; dia <= 7; dia++) {
            jdbc.update("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
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

    private ResultActions enviar(String ruta, UUID actor, Object cuerpo) throws Exception {
        var peticion = post(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(actor));
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(peticion);
    }

    private ResultActions reservar(UUID servicio, String fecha, String inicio) throws Exception {
        return enviar(CITAS, gestor, reserva(servicio, fecha, inicio, cliente("Ana", "ana@x.co", "300 1")));
    }

    private UUID reservarOk(UUID servicio, String fecha, String inicio) throws Exception {
        String cuerpo = reservar(servicio, fecha, inicio).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(cuerpo).get("id").asText());
    }

    private ResultActions mover(UUID cita, String fecha, String inicio) throws Exception {
        return enviar(CITAS + "/" + cita + "/mover", gestor, Map.of("fecha", fecha, "inicio", inicio));
    }

    private ResultActions horarios(UUID servicio, String fecha) throws Exception {
        return obtener("/api/panel/servicios/" + servicio + "/horarios?fecha=" + fecha, gestor);
    }

    private void esHorarioNoDisponible(ResultActions r) throws Exception {
        r.andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("HORARIO_NO_DISPONIBLE"));
    }

    // ---------- Reservar ----------

    @Test
    void reservarDevuelve201ConfirmadaConElFinSegunLaDuracion() throws Exception {
        String cuerpo = reservar(masaje, MANANA, "09:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.servicioId").value(masaje.toString()))
                .andExpect(jsonPath("$.clienteId").isNotEmpty())
                .andExpect(jsonPath("$.fecha").value(MANANA))
                .andExpect(jsonPath("$.inicio").value("09:00"))
                .andExpect(jsonPath("$.fin").value("10:00"))
                .andExpect(jsonPath("$.estado").value("Confirmada"))
                // 08:00 en Bogotá (UTC-5) son las 13:00 UTC.
                .andExpect(jsonPath("$.creadaEn").value("2026-11-09T13:00:00Z"))
                .andExpect(jsonPath("$.canceladaEn").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode cita = json.readTree(cuerpo);
        assertThat(contar("cita")).isEqualTo(1);
        assertThat(contar("cliente")).isEqualTo(1);
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cita");
        assertThat(fila.get("estado")).isEqualTo("Confirmada");
        assertThat(fila.get("enlace_id")).isNull();
        assertThat(fila.get("cliente_id").toString()).isEqualToIgnoringCase(cita.get("clienteId").asText());
    }

    @Test
    void elMismoHorarioOtraVezEs409() throws Exception {
        reservarOk(corte, MANANA, "09:00");

        esHorarioNoDisponible(reservar(corte, MANANA, "09:00"));
        assertThat(contar("cita")).isEqualTo(1);
    }

    @Test
    void unaCitaQueSeSolapaParcialmenteConOtraEs409() throws Exception {
        reservarOk(masaje, MANANA, "09:00");

        esHorarioNoDisponible(reservar(masaje, MANANA, "09:30"));
        reservar(masaje, MANANA, "10:00").andExpect(status().isCreated());
    }

    @Test
    void unaHoraPasadaDeHoyEs409() throws Exception {
        fijarAhora(LocalDateTime.of(2026, 11, 9, 10, 15));

        esHorarioNoDisponible(reservar(corte, "2026-11-09", "10:00"));
        esHorarioNoDisponible(reservar(corte, "2026-11-08", "10:00"));
        reservar(corte, "2026-11-09", "10:30").andExpect(status().isCreated());
    }

    @Test
    void elDia30SePuedeReservarYElDia31No() throws Exception {
        esHorarioNoDisponible(reservar(corte, "2026-12-10", "09:00"));
        reservar(corte, "2026-12-09", "09:00").andExpect(status().isCreated());
    }

    @Test
    void unServicioInactivoNoSeReserva() throws Exception {
        UUID inactivo = crearServicio("Viejo", 30, false, "09:00", "12:00");

        esHorarioNoDisponible(reservar(inactivo, MANANA, "09:00"));
        assertThat(contar("cita")).isZero();
        assertThat(contar("cliente")).isZero();
    }

    @Test
    void unServicioDesconocidoEs404() throws Exception {
        reservar(UUID.randomUUID(), MANANA, "09:00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        assertThat(contar("cliente")).isZero();
    }

    @Test
    void unInicioNoAlineadoOFueraDeLaVentanaEs409() throws Exception {
        esHorarioNoDisponible(reservar(corte, MANANA, "09:15"));
        esHorarioNoDisponible(reservar(corte, MANANA, "08:30"));
        // 12:00 + 30 min se sale de la ventana 09:00–12:00; 11:30 cabe justo.
        esHorarioNoDisponible(reservar(corte, MANANA, "12:00"));
        esHorarioNoDisponible(reservar(masaje, MANANA, "11:30"));
        reservar(corte, MANANA, "11:30").andExpect(status().isCreated());
        assertThat(contar("cita")).isEqualTo(1);
    }

    @Test
    void unDiaSinVentanaEs409() throws Exception {
        UUID soloLunes = UUID.randomUUID();
        jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'Lunes', 30, 1, 0)",
                soloLunes);
        jdbc.update("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                + "VALUES (?, ?, 1, '09:00', '12:00')", soloLunes, UUID.randomUUID());

        esHorarioNoDisponible(reservar(soloLunes, MANANA, "09:00"));
        reservar(soloLunes, "2026-11-16", "09:00").andExpect(status().isCreated());
    }

    @Test
    void a_las_23_30_en_Bogota_hoyEsElDiaDeBogota() throws Exception {
        // 23:30 del lunes 9 en Bogotá = 04:30 UTC del martes 10.
        fijarAhora(LocalDateTime.of(2026, 11, 9, 23, 30));
        UUID todoElDia = crearServicio("Todo el día", 30, true, "00:00", "23:59");

        esHorarioNoDisponible(reservar(todoElDia, "2026-11-09", "23:00"));
        reservar(todoElDia, MANANA, "09:00").andExpect(status().isCreated());
        // Si «hoy» fuera el martes (UTC), las 00:00 del martes ya habrían pasado.
        reservar(todoElDia, MANANA, "00:00").andExpect(status().isCreated());
        // El día 30 se cuenta desde el lunes 9 (Bogotá): el 9 de diciembre sí, el 10 no.
        reservar(todoElDia, "2026-12-09", "09:00").andExpect(status().isCreated());
        esHorarioNoDisponible(reservar(todoElDia, "2026-12-10", "09:00"));
    }

    @Test
    void elMismoHorarioEnOtroServicioSePuedeReservar() throws Exception {
        reservarOk(corte, MANANA, "09:00");

        reservar(masaje, MANANA, "09:00").andExpect(status().isCreated());
        assertThat(contar("cita")).isEqualTo(2);
    }

    @Test
    void unCorreoExistenteConOtrasMayusculasYEspaciosReutilizaAlClienteYActualizaSusDatos() throws Exception {
        String primera = enviar(CITAS, gestor, reserva(corte, MANANA, "09:00", cliente("Ana", "ANA@x.co", "300 1")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String segunda = enviar(CITAS, gestor,
                reserva(corte, MANANA, "10:00", cliente("Ana María", "ana@x.co ", "311 2")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(segunda).get("clienteId")).isEqualTo(json.readTree(primera).get("clienteId"));
        assertThat(contar("cliente")).isEqualTo(1);
        Map<String, Object> fila = jdbc.queryForMap("SELECT nombre, telefono, correo FROM cliente");
        assertThat(fila).containsEntry("nombre", "Ana María").containsEntry("telefono", "311 2")
                .containsEntry("correo", "ana@x.co");
    }

    @Test
    void unCorreoInvalidoEs400YNoGuardaNada() throws Exception {
        enviar(CITAS, gestor, reserva(corte, MANANA, "09:00", cliente("Ana", "ana@", "300 1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.correo").isNotEmpty());
        assertThat(contar("cita")).isZero();
        assertThat(contar("cliente")).isZero();
    }

    @Test
    void faltanDatosDelClienteOLaReservaEs400ConCampos() throws Exception {
        enviar(CITAS, gestor, reserva(corte, MANANA, "09:00", cliente(" ", "ana@x.co", "300 1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos['cliente.nombre']").isNotEmpty());
        enviar(CITAS, gestor, reserva(null, MANANA, "09:00", cliente("Ana", "ana@x.co", "300 1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.servicioId").isNotEmpty());
        enviar(CITAS, gestor, reserva(corte, MANANA, "09:00", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.cliente").isNotEmpty());
        assertThat(contar("cita")).isZero();
    }

    @Test
    void unaFechaOHoraMalEscritaEs400() throws Exception {
        for (String[] malo : new String[][] {{"2026-13-01", "09:00"}, {"2026-02-30", "09:00"}, {"10/11/2026", "09:00"},
                {MANANA, "9:00"}, {MANANA, "24:00"}, {MANANA, "09:00:00"}}) {
            reservar(corte, malo[0], malo[1])
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
        assertThat(contar("cita")).isZero();
    }

    // ---------- Mover ----------

    @Test
    void moverAUnHorarioLibreCambiaFechaInicioYFinYLiberaElAnterior() throws Exception {
        UUID cita = reservarOk(masaje, MANANA, "09:00");

        mover(cita, "2026-11-11", "10:30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cita.toString()))
                .andExpect(jsonPath("$.fecha").value("2026-11-11"))
                .andExpect(jsonPath("$.inicio").value("10:30"))
                .andExpect(jsonPath("$.fin").value("11:30"))
                .andExpect(jsonPath("$.estado").value("Confirmada"));

        horarios(masaje, MANANA).andExpect(jsonPath("$", hasItem("09:00")));
        horarios(masaje, "2026-11-11").andExpect(jsonPath("$", not(hasItem("10:30"))));
        assertThat(contar("cita")).isEqualTo(1);
    }

    @Test
    void moverDentroDeSuPropioHorarioSolapadoVale() throws Exception {
        UUID cita = reservarOk(masaje, MANANA, "09:00");

        // 09:30–10:30 se cruza con la propia cita (09:00–10:00): al moverla se excluye a sí misma.
        mover(cita, MANANA, "09:30").andExpect(status().isOk()).andExpect(jsonPath("$.fin").value("10:30"));
    }

    @Test
    void moverAUnHorarioOcupadoOPasadoEs409YNoCambiaNada() throws Exception {
        UUID cita = reservarOk(masaje, MANANA, "09:00");
        reservarOk(masaje, MANANA, "11:00");

        esHorarioNoDisponible(mover(cita, MANANA, "10:30"));
        esHorarioNoDisponible(mover(cita, "2026-11-08", "09:00"));
        esHorarioNoDisponible(mover(cita, MANANA, "09:10"));

        assertThat(jdbc.queryForObject("SELECT CONVERT(VARCHAR(5), hora_inicio, 108) FROM cita WHERE id = ?",
                String.class, cita)).isEqualTo("09:00");
    }

    @Test
    void moverUnaCitaDeUnServicioInactivoFunciona() throws Exception {
        UUID cita = reservarOk(corte, MANANA, "09:00");
        jdbc.update("UPDATE servicio SET activo = 0 WHERE id = ?", corte);

        mover(cita, MANANA, "11:00").andExpect(status().isOk()).andExpect(jsonPath("$.inicio").value("11:00"));
        // Pero no se puede reservar de nuevo en él.
        esHorarioNoDisponible(reservar(corte, MANANA, "09:00"));
    }

    @Test
    void moverUnaCitaCanceladaOAtendidaEs409EstadoInvalido() throws Exception {
        UUID cancelada = reservarOk(corte, MANANA, "09:00");
        enviar(CITAS + "/" + cancelada + "/cancelar", gestor, null).andExpect(status().isOk());
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 0));
        UUID atendida = reservarOk(corte, "2026-11-09", "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 5));
        enviar(CITAS + "/" + atendida + "/atender", gestor, null).andExpect(status().isOk());

        mover(cancelada, MANANA, "11:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        mover(atendida, MANANA, "11:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
    }

    // ---------- Cancelar y atender ----------

    @Test
    void cancelarLiberaElHorarioYSePuedeVolverAReservar() throws Exception {
        UUID cita = reservarOk(corte, MANANA, "09:00");
        horarios(corte, MANANA).andExpect(jsonPath("$", not(hasItem("09:00"))));
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 30));

        enviar(CITAS + "/" + cita + "/cancelar", gestor, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Cancelada"))
                .andExpect(jsonPath("$.canceladaEn").value("2026-11-09T13:30:00Z"));

        horarios(corte, MANANA).andExpect(status().isOk()).andExpect(jsonPath("$", hasItem("09:00")));
        reservar(corte, MANANA, "09:00").andExpect(status().isCreated());
        assertThat(contar("cita")).isEqualTo(2);
    }

    @Test
    void cancelarDosVecesNoCambiaNada() throws Exception {
        UUID cita = reservarOk(corte, MANANA, "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 30));
        enviar(CITAS + "/" + cita + "/cancelar", gestor, null).andExpect(status().isOk());
        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 0));

        enviar(CITAS + "/" + cita + "/cancelar", gestor, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Cancelada"))
                .andExpect(jsonPath("$.canceladaEn").value("2026-11-09T13:30:00Z"));
    }

    @Test
    void atenderAntesDeSuHoraEs409CitaNoHaIniciadoYDespuesFunciona() throws Exception {
        UUID cita = reservarOk(corte, "2026-11-09", "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 59));

        enviar(CITAS + "/" + cita + "/atender", gestor, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CITA_NO_HA_INICIADO"));

        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 0));
        enviar(CITAS + "/" + cita + "/atender", gestor, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Atendida"));
        assertThat(jdbc.queryForObject("SELECT estado FROM cita", String.class)).isEqualTo("Atendida");
    }

    @Test
    void lasTransicionesInvalidasSon409EstadoInvalido() throws Exception {
        UUID atendida = reservarOk(corte, "2026-11-09", "09:00");
        UUID cancelada = reservarOk(corte, "2026-11-09", "10:00");
        enviar(CITAS + "/" + cancelada + "/cancelar", gestor, null).andExpect(status().isOk());
        fijarAhora(LocalDateTime.of(2026, 11, 9, 11, 0));
        enviar(CITAS + "/" + atendida + "/atender", gestor, null).andExpect(status().isOk());

        enviar(CITAS + "/" + atendida + "/atender", gestor, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        enviar(CITAS + "/" + atendida + "/cancelar", gestor, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        enviar(CITAS + "/" + cancelada + "/atender", gestor, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        assertThat(jdbc.queryForObject("SELECT estado FROM cita WHERE id = ?", String.class, atendida))
                .isEqualTo("Atendida");
    }

    @Test
    void unaCitaDesconocidaEs404() throws Exception {
        UUID nada = UUID.randomUUID();
        for (String accion : new String[] {"cancelar", "atender"}) {
            enviar(CITAS + "/" + nada + "/" + accion, gestor, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        }
        mover(nada, MANANA, "09:00").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    // ---------- Listar y horarios ----------

    @Test
    void listarCitasFiltraPorRangoYEstadoYOrdenaPorFechaEHora() throws Exception {
        UUID c1 = reservarOk(corte, "2026-11-11", "10:00");
        UUID c2 = reservarOk(corte, MANANA, "11:00");
        UUID c3 = reservarOk(masaje, MANANA, "09:00");
        UUID c4 = reservarOk(corte, "2026-11-12", "09:00");
        enviar(CITAS + "/" + c4 + "/cancelar", gestor, null).andExpect(status().isOk());

        obtener(CITAS, gestor).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(c3.toString(), c2.toString(), c1.toString(),
                        c4.toString())));
        obtener(CITAS + "?desde=2026-11-11&hasta=2026-11-12", gestor)
                .andExpect(jsonPath("$[*].id").value(contains(c1.toString(), c4.toString())));
        obtener(CITAS + "?hasta=2026-11-10", gestor)
                .andExpect(jsonPath("$[*].id").value(contains(c3.toString(), c2.toString())));
        obtener(CITAS + "?desde=2026-11-11&estado=Confirmada", gestor)
                .andExpect(jsonPath("$[*].id").value(contains(c1.toString())));
        obtener(CITAS + "?estado=Cancelada", gestor)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].canceladaEn").isNotEmpty());
    }

    @Test
    void filtrosInvalidosSon400() throws Exception {
        obtener(CITAS + "?estado=Perdida", gestor).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        obtener(CITAS + "?desde=ayer", gestor).andExpect(status().isBadRequest());
        obtener(CITAS + "?desde=2026-11-12&hasta=2026-11-11", gestor).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void horariosLibresDevuelveHorasHHmmYExcluirCitaIdLiberaLaPropia() throws Exception {
        UUID cita = reservarOk(masaje, MANANA, "10:00");

        horarios(masaje, MANANA).andExpect(status().isOk())
                .andExpect(jsonPath("$").value(contains("09:00", "11:00")));
        obtener("/api/panel/servicios/" + masaje + "/horarios?fecha=" + MANANA + "&excluirCitaId=" + cita, gestor)
                .andExpect(jsonPath("$").value(contains("09:00", "09:30", "10:00", "10:30", "11:00")));
    }

    @Test
    void horariosDeUnServicioDesconocidoEs404YSinFechaEs400() throws Exception {
        horarios(UUID.randomUUID(), MANANA).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        obtener("/api/panel/servicios/" + corte + "/horarios", gestor).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    // ---------- Permisos ----------

    @Test
    void verCitasLeePeroNoEscribe() throws Exception {
        UUID cita = reservarOk(corte, MANANA, "09:00");
        UUID lector = crearUsuario("lector", crearRol("Lector", "ver_citas"));

        obtener(CITAS, lector).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        enviar(CITAS, lector, reserva(corte, MANANA, "10:00", cliente("Ana", "ana@x.co", "300 1")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        enviar(CITAS + "/" + cita + "/mover", lector, Map.of("fecha", MANANA, "inicio", "11:00"))
                .andExpect(status().isForbidden());
        enviar(CITAS + "/" + cita + "/cancelar", lector, null).andExpect(status().isForbidden());
        enviar(CITAS + "/" + cita + "/atender", lector, null).andExpect(status().isForbidden());
        obtener("/api/panel/servicios/" + corte + "/horarios?fecha=" + MANANA, lector)
                .andExpect(status().isForbidden());
        assertThat(contar("cita")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM cita", String.class)).isEqualTo("Confirmada");
    }

    @Test
    void verPanelTambienLeeCitas() throws Exception {
        UUID panel = crearUsuario("panel", crearRol("Panel", "ver_panel"));

        obtener(CITAS, panel).andExpect(status().isOk());
        obtener("/api/panel/servicios/" + corte + "/horarios?fecha=" + MANANA, panel)
                .andExpect(status().isForbidden());
    }

    @Test
    void atenderCitasSoloAtiende() throws Exception {
        UUID cita = reservarOk(corte, "2026-11-09", "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 30));
        UUID recepcion = crearUsuario("recepcion", crearRol("Recepción", "atender_citas"));

        enviar(CITAS, recepcion, reserva(corte, MANANA, "10:00", cliente("Ana", "ana@x.co", "300 1")))
                .andExpect(status().isForbidden());
        enviar(CITAS + "/" + cita + "/mover", recepcion, Map.of("fecha", MANANA, "inicio", "11:00"))
                .andExpect(status().isForbidden());
        enviar(CITAS + "/" + cita + "/cancelar", recepcion, null).andExpect(status().isForbidden());
        obtener(CITAS, recepcion).andExpect(status().isForbidden());

        enviar(CITAS + "/" + cita + "/atender", recepcion, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Atendida"));
    }

    @Test
    void gestionarCitasNoAtiende() throws Exception {
        UUID cita = reservarOk(corte, "2026-11-09", "09:00");
        fijarAhora(LocalDateTime.of(2026, 11, 9, 9, 30));
        UUID agenda = crearUsuario("agenda", crearRol("Agenda", "gestionar_citas"));

        enviar(CITAS + "/" + cita + "/atender", agenda, null).andExpect(status().isForbidden());
        enviar(CITAS, agenda, reserva(corte, MANANA, "10:00", cliente("Ana", "ana@x.co", "300 1")))
                .andExpect(status().isCreated());
        obtener("/api/panel/servicios/" + corte + "/horarios?fecha=" + MANANA, agenda).andExpect(status().isOk());
    }

    @Test
    void sinPermisosDeCitasEs403() throws Exception {
        UUID otro = crearUsuario("otro", crearRol("Otro", "gestionar_clientes", "gestionar_servicios"));

        obtener(CITAS, otro).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        enviar(CITAS, otro, reserva(corte, MANANA, "10:00", cliente("Ana", "ana@x.co", "300 1")))
                .andExpect(status().isForbidden());
        assertThat(contar("cita")).isZero();
    }

    @Test
    void sinTokenEs401() throws Exception {
        UUID cita = reservarOk(corte, MANANA, "09:00");

        mvc.perform(get(CITAS)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(post(CITAS).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reserva(corte, MANANA, "10:00",
                                cliente("Ana", "ana@x.co", "300 1")))))
                .andExpect(status().isUnauthorized());
        for (String accion : new String[] {"cancelar", "atender"}) {
            mvc.perform(post(CITAS + "/" + cita + "/" + accion)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/panel/servicios/" + corte + "/horarios?fecha=" + MANANA))
                .andExpect(status().isUnauthorized());
        assertThat(contar("cita")).isEqualTo(1);
    }

    // ---------- Restricciones de la base ----------

    private void insertarCita(UUID servicio, UUID cliente, String inicio, String fin, String estado) {
        jdbc.update("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en) "
                + "VALUES (?, ?, ?, '2026-11-10', ?, ?, ?, SYSUTCDATETIME())",
                UUID.randomUUID(), servicio, cliente, inicio, fin, estado);
    }

    private UUID insertarCliente(String correo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, 'A', 'B', '1', ?)",
                id, correo);
        return id;
    }

    @Test
    void laBaseRechazaEstadosDesconocidosYFinNoPosteriorAlInicio() {
        UUID cliente = insertarCliente("a@x.co");

        assertThatThrownBy(() -> insertarCita(corte, cliente, "09:00", "09:30", "Pendiente"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarCita(corte, cliente, "09:00", "09:00", "Confirmada"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarCita(UUID.randomUUID(), cliente, "09:00", "09:30", "Confirmada"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarCita(corte, UUID.randomUUID(), "09:00", "09:30", "Confirmada"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertarCita(corte, cliente, "09:00", "09:30", "Confirmada");
    }

    @Test
    void laBaseRechazaDosClientesConElMismoCorreo() {
        insertarCliente("a@x.co");

        assertThatThrownBy(() -> insertarCliente("a@x.co")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
