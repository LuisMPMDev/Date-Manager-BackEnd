package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code GET /api/panel/inicio}. "Hoy" es el lunes 9 de noviembre de 2026, 08:00 en Bogotá (13:00 UTC); todos los
 * datos se siembran por SQL y el reloj está fijo.
 */
class InicioControladorTest extends PruebaPanel {

    private static final String INICIO = "/api/panel/inicio";
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 8, 0);
    /** Las mismas 08:00 de Bogotá en UTC: así se guardan {@code creada_en} y {@code cancelada_en}. */
    private static final LocalDateTime AHORA_UTC = LocalDateTime.of(2026, 11, 9, 13, 0);

    private UUID lector;
    private UUID cliente;

    @BeforeEach
    void preparar() {
        fijarAhora(AHORA);
        lector = crearUsuario("lector", crearRol("Lector", "ver_panel"));
        cliente = crearCliente("Ana", "Pérez");
    }

    // ---------- Siembra ----------

    private UUID crearServicio(String nombre, boolean activo, String valor, String... ventanas) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, ?, 60, ?, ?)",
                id, nombre, activo, new BigDecimal(valor));
        for (String v : ventanas) {
            String[] partes = v.split(" "); // "dia inicio fin"
            jdbc.update("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                    + "VALUES (?, ?, ?, ?, ?)", id, UUID.randomUUID(), Integer.parseInt(partes[0]), partes[1], partes[2]);
        }
        return id;
    }

    /** Servicio activo de 50 000 con el lunes de 08:00 a 12:00 (240 min). */
    private UUID lunesDeOchoADoce(String nombre) {
        return crearServicio(nombre, true, "50000", "1 08:00 12:00");
    }

    private UUID crearCliente(String nombre, String apellido) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, ?, ?, '300 1', ?)",
                id, nombre, apellido, id + "@x.co");
        return id;
    }

    private UUID cita(UUID servicio, String fecha, String inicio, String fin, String estado, LocalDateTime creadaEn,
            LocalDateTime canceladaEn) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en, "
                + "cancelada_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, servicio, cliente, fecha, inicio, fin, estado, creadaEn, canceladaEn);
        return id;
    }

    /** Cita creada hace mucho (no cuenta como «nueva» ni genera alerta). */
    private UUID cita(UUID servicio, String fecha, String inicio, String fin, String estado) {
        LocalDateTime antigua = AHORA_UTC.minusDays(100);
        return cita(servicio, fecha, inicio, fin, estado, antigua, "Cancelada".equals(estado) ? antigua : null);
    }

    private UUID confirmada(UUID servicio, String fecha, String inicio, String fin) {
        return cita(servicio, fecha, inicio, fin, "Confirmada");
    }

    private static LocalDateTime hace(long dias) {
        return AHORA_UTC.minusDays(dias);
    }

    private ResultActions inicio() throws Exception {
        return obtener(INICIO, lector).andExpect(status().isOk());
    }

    // ---------- Día sin citas ----------

    @Test
    void unDiaSinNadaDevuelveCerosSinError() throws Exception {
        inicio()
                .andExpect(jsonPath("$.fecha").value("2026-11-09"))
                .andExpect(jsonPath("$.citasHoy.total").value(0))
                .andExpect(jsonPath("$.citasHoy.atendidas").value(0))
                .andExpect(jsonPath("$.proxima").doesNotExist())
                .andExpect(jsonPath("$.ocupacionHoy.actual").value(0.0))
                .andExpect(jsonPath("$.ocupacionHoy.anterior").value(0.0))
                .andExpect(jsonPath("$.ocupacionHoy.variacion").doesNotExist())
                .andExpect(jsonPath("$.citasNuevas.actual").value(0))
                .andExpect(jsonPath("$.citasNuevas.anterior").value(0))
                .andExpect(jsonPath("$.citasNuevas.variacion").doesNotExist())
                .andExpect(jsonPath("$.citasCanceladas.actual").value(0))
                .andExpect(jsonPath("$.citasCanceladas.variacion").doesNotExist())
                .andExpect(jsonPath("$.ingresos.actual").value(0.0))
                .andExpect(jsonPath("$.ingresos.anterior").value(0.0))
                .andExpect(jsonPath("$.ingresos.variacion").doesNotExist())
                .andExpect(jsonPath("$.alertas", hasSize(0)))
                .andExpect(jsonPath("$.alertasExtra").value(0))
                .andExpect(jsonPath("$.citasPorDia", hasSize(30)))
                .andExpect(jsonPath("$.citasPorDia[*].total", contains(zeros(30))))
                .andExpect(jsonPath("$.ocupacionSemana", hasSize(0)))
                .andExpect(jsonPath("$.proximas", hasSize(0)));
    }

    private static Integer[] zeros(int n) {
        Integer[] z = new Integer[n];
        Arrays.fill(z, 0);
        return z;
    }

    @Test
    void conServiciosPeroSinCitasLaOcupacionEsCeroYNoNaN() throws Exception {
        lunesDeOchoADoce("A");
        String cuerpo = inicio()
                .andExpect(jsonPath("$.ocupacionHoy.actual").value(0.0))
                .andExpect(jsonPath("$.ocupacionSemana", hasSize(1)))
                .andExpect(jsonPath("$.ocupacionSemana[0].ocupacion").value(0.0))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("NaN").doesNotContain("Infinity");
    }

    @Test
    void elJsonNuncaTraeNaNNiInfinitoAunConPrevioCero() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00"); // hoy 25 %, lunes pasado 0
        String cuerpo = inicio().andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("NaN").doesNotContain("Infinity");
    }

    // ---------- Citas hoy y próximas ----------

    @Test
    void citasHoyCuentaLasNoCanceladasYCuantasSeAtendieron() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        cita(a, "2026-11-09", "10:00", "11:00", "Atendida");
        cita(a, "2026-11-09", "11:00", "12:00", "Cancelada");
        confirmada(a, "2026-11-10", "09:00", "10:00"); // mañana: no
        confirmada(a, "2026-11-08", "09:00", "10:00"); // ayer: no
        inicio().andExpect(jsonPath("$.citasHoy.total").value(2)).andExpect(jsonPath("$.citasHoy.atendidas").value(1));
    }

    @Test
    void laProximaYLasProximasSonLasConfirmadasQueAunNoEmpiezanOrdenadasHastaCinco() throws Exception {
        UUID a = lunesDeOchoADoce("Servicio A");
        UUID clienteNuevo = crearCliente("Luis", "Gómez");
        confirmada(a, "2026-11-12", "09:00", "10:00");
        confirmada(a, "2026-11-09", "07:30", "08:30"); // ya empezó
        cita(a, "2026-11-09", "09:00", "10:00", "Atendida");
        UUID diez = confirmada(a, "2026-11-09", "10:00", "11:00");
        cita(a, "2026-11-10", "09:00", "10:00", "Cancelada");
        UUID ahoraMismo = cita(a, "2026-11-09", "08:00", "09:00", "Confirmada", hace(100), null);
        confirmada(a, "2026-11-13", "09:00", "10:00");
        confirmada(a, "2026-11-14", "09:00", "10:00");
        confirmada(a, "2026-11-15", "09:00", "10:00"); // la sexta: queda fuera
        jdbc.update("UPDATE cita SET cliente_id = ? WHERE id = ?", clienteNuevo, diez);

        inicio()
                .andExpect(jsonPath("$.proximas", hasSize(5)))
                .andExpect(jsonPath("$.proximas[*].fecha", contains("2026-11-09", "2026-11-09", "2026-11-12", "2026-11-13",
                        "2026-11-14")))
                .andExpect(jsonPath("$.proximas[*].inicio", contains("08:00", "10:00", "09:00", "09:00", "09:00")))
                .andExpect(jsonPath("$.proximas[0].id").value(ahoraMismo.toString()))
                .andExpect(jsonPath("$.proximas[0].fin").value("09:00"))
                .andExpect(jsonPath("$.proximas[0].esHoy").value(true))
                .andExpect(jsonPath("$.proximas[0].minutosHasta").value(0))
                .andExpect(jsonPath("$.proximas[0].servicioId").value(a.toString()))
                .andExpect(jsonPath("$.proximas[0].servicioNombre").value("Servicio A"))
                .andExpect(jsonPath("$.proximas[0].clienteNombre").value("Ana"))
                .andExpect(jsonPath("$.proximas[0].clienteApellido").value("Pérez"))
                .andExpect(jsonPath("$.proximas[1].minutosHasta").value(120))
                .andExpect(jsonPath("$.proximas[1].clienteNombre").value("Luis"))
                .andExpect(jsonPath("$.proximas[2].esHoy").value(false))
                .andExpect(jsonPath("$.proximas[2].minutosHasta").doesNotExist())
                // la próxima cita es la primera de las próximas
                .andExpect(jsonPath("$.proxima.id").value(ahoraMismo.toString()))
                .andExpect(jsonPath("$.proxima.inicio").value("08:00"))
                .andExpect(jsonPath("$.proxima.minutosHasta").value(0));
    }

    @Test
    void siLaSiguienteEsDeOtroDiaLaProximaNoTrazaMinutos() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-11", "14:00", "15:00");
        confirmada(a, "2026-11-10", "09:00", "10:00");
        inicio()
                .andExpect(jsonPath("$.proxima.fecha").value("2026-11-10"))
                .andExpect(jsonPath("$.proxima.inicio").value("09:00"))
                .andExpect(jsonPath("$.proxima.esHoy").value(false))
                .andExpect(jsonPath("$.proxima.minutosHasta").doesNotExist());
    }

    @Test
    void sinConfirmadasPorDelanteNoHayProximaAunqueHayaCanceladasYPasadas() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        cita(a, "2026-11-10", "09:00", "10:00", "Cancelada");
        confirmada(a, "2026-11-08", "09:00", "10:00");
        cita(a, "2026-11-12", "09:00", "10:00", "Atendida");
        inicio().andExpect(jsonPath("$.proxima").doesNotExist()).andExpect(jsonPath("$.proximas", hasSize(0)));
    }

    // ---------- Ocupación de hoy ----------

    /** Lunes de 08:00 a 12:00 (240 min). */
    @Test
    void laOcupacionDeHoyComparaConElMismoDiaDeLaSemanaPasada() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00"); // 60 de 240 = 25 %
        confirmada(a, "2026-11-02", "09:00", "09:30"); // lunes pasado: 30 de 240 = 12,5 %
        // no se cuelan en la cifra de hoy ni en la del lunes pasado
        confirmada(a, "2026-11-10", "09:00", "12:00"); // mañana
        confirmada(a, "2026-11-08", "09:00", "12:00"); // ayer
        confirmada(a, "2026-11-03", "09:00", "12:00"); // martes pasado
        confirmada(a, "2026-11-01", "09:00", "12:00"); // domingo pasado
        inicio()
                .andExpect(jsonPath("$.ocupacionHoy.actual").value(0.25))
                .andExpect(jsonPath("$.ocupacionHoy.anterior").value(0.125))
                .andExpect(jsonPath("$.ocupacionHoy.variacion").value(1.0));
    }

    @Test
    void siBajaLaVariacionEsNegativa() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(a, "2026-11-02", "09:00", "11:00");
        inicio().andExpect(jsonPath("$.ocupacionHoy.variacion").value(-0.5));
    }

    @Test
    void sinOcupacionLaSemanaPasadaNoHayVariacion() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        inicio()
                .andExpect(jsonPath("$.ocupacionHoy.actual").value(0.25))
                .andExpect(jsonPath("$.ocupacionHoy.anterior").value(0.0))
                .andExpect(jsonPath("$.ocupacionHoy.variacion").doesNotExist());
    }

    @Test
    void sinCambioLaVariacionEsCeroYLaClaveSePublica() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(a, "2026-11-02", "09:00", "10:00");
        inicio().andExpect(jsonPath("$.ocupacionHoy.variacion").value(0.0));
    }

    @Test
    void lasCanceladasNoOcupanPeroLasAtendidasSi() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        cita(a, "2026-11-09", "08:00", "10:00", "Cancelada");
        cita(a, "2026-11-09", "10:00", "11:00", "Atendida");
        inicio().andExpect(jsonPath("$.ocupacionHoy.actual").value(0.25));
    }

    @Test
    void losServiciosInactivosYLosQueNoAtiendenHoyNoEntranEnElDenominador() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        UUID inactivo = crearServicio("Inactivo", false, "1000", "1 08:00 20:00");
        UUID soloMartes = crearServicio("Solo martes", true, "1000", "2 08:00 20:00");
        UUID sinVentanas = crearServicio("Sin ventanas", true, "1000");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(inactivo, "2026-11-09", "09:00", "12:00"); // no suma
        confirmada(soloMartes, "2026-11-09", "09:00", "12:00"); // ese día no atiende: no suma
        confirmada(sinVentanas, "2026-11-09", "09:00", "12:00");
        // solo cuenta A: 60 de 240
        inicio().andExpect(jsonPath("$.ocupacionHoy.actual").value(0.25));
    }

    @Test
    void variosServiciosActivosSumanSusMinutosAntesDeDividir() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        UUID b = crearServicio("B", true, "1000", "1 08:00 10:00");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(b, "2026-11-09", "08:00", "09:00");
        // 120 de 240 + 120 = 360
        inicio().andExpect(jsonPath("$.ocupacionHoy.actual").value(closeTo(120.0 / 360, 1e-12)));
    }

    // ---------- Citas nuevas y canceladas (30 días) ----------

    @Test
    void citasNuevasCuentaLasCreadasEnLosUltimos30DiasYComparaConLos30Anteriores() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        int dia = 20;
        // periodo actual (ahora - 30 d, ahora]
        for (LocalDateTime creada : new LocalDateTime[] { hace(1), hace(29), hace(0), AHORA_UTC.minusMinutes(5) }) {
            cita(a, "2026-11-" + dia++, "09:00", "10:00", "Confirmada", creada, null);
        }
        cita(a, "2026-11-" + dia++, "09:00", "10:00", "Cancelada", hace(15), hace(14)); // también fue una cita nueva
        // periodo anterior (ahora - 60 d, ahora - 30 d]
        cita(a, "2026-11-" + dia++, "09:00", "10:00", "Confirmada", hace(30), null); // justo 30 d: es del anterior
        cita(a, "2026-11-" + dia++, "09:00", "10:00", "Confirmada", hace(31), null);
        cita(a, "2026-11-" + dia++, "09:00", "10:00", "Confirmada", hace(59), null);
        // fuera
        cita(a, "2026-11-" + dia++, "09:00", "10:00", "Confirmada", hace(60), null); // justo 60 d
        cita(a, "2026-11-" + dia++, "09:00", "10:00", "Confirmada", hace(90), null);
        cita(a, "2026-11-" + dia++, "09:00", "10:00", "Confirmada", AHORA_UTC.plusHours(1), null); // futura
        inicio()
                .andExpect(jsonPath("$.citasNuevas.actual").value(5))
                .andExpect(jsonPath("$.citasNuevas.anterior").value(3))
                .andExpect(jsonPath("$.citasNuevas.variacion").value(closeTo(2.0 / 3, 1e-12)));
    }

    @Test
    void citasNuevasSinPeriodoPrevioNoTieneVariacion() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        cita(a, "2026-11-20", "09:00", "10:00", "Confirmada", hace(2), null);
        inicio()
                .andExpect(jsonPath("$.citasNuevas.actual").value(1))
                .andExpect(jsonPath("$.citasNuevas.anterior").value(0))
                .andExpect(jsonPath("$.citasNuevas.variacion").doesNotExist());
    }

    @Test
    void citasCanceladasCuentaPorLaFechaDeCancelacionNoDeCreacion() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        cita(a, "2026-11-20", "09:00", "10:00", "Cancelada", hace(50), hace(1));
        cita(a, "2026-11-21", "09:00", "10:00", "Cancelada", hace(50), hace(10));
        cita(a, "2026-11-22", "09:00", "10:00", "Cancelada", hace(70), hace(30)); // justo 30 d: periodo anterior
        cita(a, "2026-11-23", "09:00", "10:00", "Cancelada", hace(70), hace(45));
        cita(a, "2026-11-24", "09:00", "10:00", "Cancelada", hace(80), hace(60)); // justo 60 d: fuera
        cita(a, "2026-11-25", "09:00", "10:00", "Confirmada", hace(1), null); // no cancelada
        inicio()
                .andExpect(jsonPath("$.citasCanceladas.actual").value(2))
                .andExpect(jsonPath("$.citasCanceladas.anterior").value(2))
                .andExpect(jsonPath("$.citasCanceladas.variacion").value(0.0));
    }

    @Test
    void masCanceladasQueAntesEsUnaVariacionPositiva() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        cita(a, "2026-11-20", "09:00", "10:00", "Cancelada", hace(50), hace(1));
        cita(a, "2026-11-21", "09:00", "10:00", "Cancelada", hace(50), hace(10));
        cita(a, "2026-11-22", "09:00", "10:00", "Cancelada", hace(70), hace(45));
        inicio().andExpect(jsonPath("$.citasCanceladas.variacion").value(1.0));
    }

    // ---------- Ingresos estimados ----------

    @Test
    void ingresosSumaLosValoresDeLosUltimos30DiasHastaHoyYComparaConLos30Anteriores() throws Exception {
        UUID a = lunesDeOchoADoce("A"); // 50 000 por cita
        confirmada(a, "2026-11-09", "09:00", "10:00"); // hoy: entra
        confirmada(a, "2026-10-11", "09:00", "10:00"); // hoy - 29: entra
        confirmada(a, "2026-10-10", "09:00", "10:00"); // hoy - 30: periodo anterior
        confirmada(a, "2026-09-11", "09:00", "10:00"); // hoy - 59: periodo anterior
        confirmada(a, "2026-09-10", "09:00", "10:00"); // hoy - 60: fuera
        cita(a, "2026-10-20", "09:00", "10:00", "Cancelada"); // no suma
        confirmada(a, "2026-11-10", "09:00", "10:00"); // futura: no suma
        inicio()
                .andExpect(jsonPath("$.ingresos.actual").value(100000.0))
                .andExpect(jsonPath("$.ingresos.anterior").value(100000.0))
                .andExpect(jsonPath("$.ingresos.variacion").value(0.0));
    }

    @Test
    void ingresosMuestraLaVariacionFrenteAlPeriodoAnterior() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(a, "2026-11-08", "09:00", "10:00");
        confirmada(a, "2026-11-07", "09:00", "10:00");
        confirmada(a, "2026-10-01", "09:00", "10:00");
        confirmada(a, "2026-10-02", "09:00", "10:00");
        inicio()
                .andExpect(jsonPath("$.ingresos.actual").value(150000.0))
                .andExpect(jsonPath("$.ingresos.anterior").value(100000.0))
                .andExpect(jsonPath("$.ingresos.variacion").value(0.5));
    }

    @Test
    void ingresosSeSumaEnBigDecimalYSeSerializaComoNumeroJson() throws Exception {
        UUID barato = crearServicio("Barato", true, "0.10", "1 08:00 12:00");
        confirmada(barato, "2026-11-09", "08:00", "09:00");
        confirmada(barato, "2026-11-09", "09:00", "10:00");
        confirmada(barato, "2026-11-09", "10:00", "11:00");
        String cuerpo = inicio().andExpect(jsonPath("$.ingresos.actual").value(0.3))
                .andReturn().getResponse().getContentAsString();
        // número JSON (no texto) y sin el error de los binarios: 0.1 + 0.1 + 0.1 en double es 0.30000000000000004
        assertThat(cuerpo).contains("\"actual\":0.30")
                .doesNotContain("0.30000000000000004").doesNotContain("\"actual\":\"");
    }

    @Test
    void ingresosConValoresGrandesYDecimales() throws Exception {
        UUID caro = crearServicio("Caro", true, "9999999999.99", "1 08:00 12:00");
        confirmada(caro, "2026-11-09", "08:00", "09:00");
        confirmada(caro, "2026-11-09", "09:00", "10:00");
        String cuerpo = inicio().andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).contains("\"actual\":19999999999.98");
    }

    // ---------- Alertas ----------

    @Test
    void alertasMuestraLasCitasNuevasYCanceladasDeLasUltimas24HYLosServiciosSinHorario() throws Exception {
        UUID corte = crearServicio("Corte", true, "1000", "1 08:00 12:00");
        crearServicio("Masaje", true, "1000"); // activo sin ventanas
        crearServicio("Inactivo sin ventanas", false, "1000"); // no genera alerta
        UUID nueva = cita(corte, "2026-11-10", "09:00", "10:00", "Confirmada", AHORA_UTC.minusHours(2), null);
        cita(corte, "2026-11-10", "10:00", "11:00", "Confirmada", AHORA_UTC.minusHours(30), null); // vieja
        UUID cancelada = cita(corte, "2026-11-11", "09:00", "10:00", "Cancelada", hace(5), AHORA_UTC.minusHours(3));
        cita(corte, "2026-11-11", "10:00", "11:00", "Cancelada", hace(5), AHORA_UTC.minusHours(30)); // vieja
        // una cancelada creada hace poco pero cancelada hace más de 24 h (no es «nueva»: está cancelada)
        cita(corte, "2026-11-12", "09:00", "10:00", "Cancelada", AHORA_UTC.minusHours(40), AHORA_UTC.minusHours(26));

        inicio()
                .andExpect(jsonPath("$.alertas", hasSize(3)))
                .andExpect(jsonPath("$.alertas[0].tipo").value("cita_nueva"))
                .andExpect(jsonPath("$.alertas[0].titulo").value("Cita nueva"))
                .andExpect(jsonPath("$.alertas[0].detalle").value("Corte, Martes 10 de noviembre a las 09:00"))
                .andExpect(jsonPath("$.alertas[0].ref").value(nueva.toString()))
                .andExpect(jsonPath("$.alertas[1].tipo").value("cita_cancelada"))
                .andExpect(jsonPath("$.alertas[1].titulo").value("Cita cancelada"))
                .andExpect(jsonPath("$.alertas[1].detalle").value("Corte, Miércoles 11 de noviembre a las 09:00"))
                .andExpect(jsonPath("$.alertas[1].ref").value(cancelada.toString()))
                .andExpect(jsonPath("$.alertas[2].tipo").value("servicio_sin_horario"))
                .andExpect(jsonPath("$.alertas[2].titulo").value("Servicio sin horario"))
                .andExpect(jsonPath("$.alertas[2].detalle").value("Masaje no tiene horarios de atención"))
                .andExpect(jsonPath("$.alertasExtra").value(0));
    }

    @Test
    void alertasMuestraSeisYDiceCuantasQuedanFuera() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        for (int i = 0; i < 9; i++) {
            cita(a, "2026-11-" + (10 + i), "09:00", "10:00", "Confirmada", AHORA_UTC.minusMinutes(10 + i), null);
        }
        // las más recientes primero
        inicio()
                .andExpect(jsonPath("$.alertas", hasSize(6)))
                .andExpect(jsonPath("$.alertas[0].detalle").value("A, Martes 10 de noviembre a las 09:00"))
                .andExpect(jsonPath("$.alertas[5].detalle").value("A, Domingo 15 de noviembre a las 09:00"))
                .andExpect(jsonPath("$.alertasExtra").value(3));
    }

    @Test
    void unServicioActivoConVentanasPeroSinHorarioHoyNoGeneraAlerta() throws Exception {
        crearServicio("Solo martes", true, "1000", "2 08:00 12:00");
        inicio().andExpect(jsonPath("$.alertas", hasSize(0)));
    }

    // ---------- Citas por día ----------

    @Test
    void citasPorDiaCubreLosUltimos30DiasHastaHoyYNoCuentaCanceladasNiFuturas() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        UUID b = lunesDeOchoADoce("B");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(b, "2026-11-09", "09:00", "10:00");
        cita(a, "2026-11-09", "10:00", "11:00", "Cancelada");
        confirmada(a, "2026-10-11", "09:00", "10:00"); // hoy - 29: primer día
        confirmada(a, "2026-10-10", "09:00", "10:00"); // hoy - 30: fuera
        confirmada(a, "2026-11-10", "09:00", "10:00"); // mañana: fuera
        inicio()
                .andExpect(jsonPath("$.citasPorDia", hasSize(30)))
                .andExpect(jsonPath("$.citasPorDia[0].fecha").value("2026-10-11"))
                .andExpect(jsonPath("$.citasPorDia[0].total").value(1))
                .andExpect(jsonPath("$.citasPorDia[1].fecha").value("2026-10-12"))
                .andExpect(jsonPath("$.citasPorDia[1].total").value(0))
                .andExpect(jsonPath("$.citasPorDia[21].fecha").value("2026-11-01"))
                .andExpect(jsonPath("$.citasPorDia[29].fecha").value("2026-11-09"))
                .andExpect(jsonPath("$.citasPorDia[29].total").value(2));
    }

    // ---------- Ocupación por servicio de la semana ----------

    private UUID semanal(String nombre, boolean activo) {
        return crearServicio(nombre, activo, "1000", "1 08:00 12:00", "2 08:00 12:00", "3 08:00 12:00",
                "4 08:00 12:00", "5 08:00 12:00");
    }

    @Test
    void ocupacionSemanaEsPorServicioActivoDeLunesADomingoDeLaSemanaDeHoy() throws Exception {
        UUID a = semanal("Servicio A", true);
        UUID b = semanal("Servicio B", true);
        UUID inactivo = semanal("Servicio C", false);
        confirmada(a, "2026-11-09", "08:00", "09:00");
        confirmada(a, "2026-11-13", "08:00", "09:00"); // 120 de 1200 = 10 %
        confirmada(a, "2026-11-16", "08:00", "12:00"); // semana siguiente
        confirmada(a, "2026-11-06", "08:00", "12:00"); // semana anterior
        cita(a, "2026-11-10", "08:00", "12:00", "Cancelada");
        confirmada(inactivo, "2026-11-11", "08:00", "12:00");
        inicio()
                .andExpect(jsonPath("$.ocupacionSemana", hasSize(2)))
                .andExpect(jsonPath("$.ocupacionSemana[0].servicioId").value(a.toString()))
                .andExpect(jsonPath("$.ocupacionSemana[0].nombre").value("Servicio A"))
                .andExpect(jsonPath("$.ocupacionSemana[0].ocupacion").value(0.1))
                .andExpect(jsonPath("$.ocupacionSemana[1].servicioId").value(b.toString()))
                .andExpect(jsonPath("$.ocupacionSemana[1].ocupacion").value(0.0));
    }

    @Test
    void unDomingoSigueContandoLaSemanaQueEmpezoElLunesAnteriorYUnLunesEmpiezaOtra() throws Exception {
        UUID a = semanal("A", true);
        confirmada(a, "2026-11-09", "08:00", "12:00"); // 240 de 1200 = 20 %
        fijarAhora(LocalDateTime.of(2026, 11, 15, 10, 0));
        inicio().andExpect(jsonPath("$.fecha").value("2026-11-15"))
                .andExpect(jsonPath("$.ocupacionSemana[0].ocupacion").value(0.2));
        fijarAhora(LocalDateTime.of(2026, 11, 16, 10, 0));
        inicio().andExpect(jsonPath("$.fecha").value("2026-11-16"))
                .andExpect(jsonPath("$.ocupacionSemana[0].ocupacion").value(0.0));
    }

    // ---------- Fecha ----------

    @Test
    void sinFechaHoyEsLaFechaDelNegocioAunqueLaJvmEstenEnOtroDia() throws Exception {
        UUID a = semanal("A", true);
        // 23:30 en Bogotá son las 04:30 UTC del día siguiente
        fijarAhora(LocalDateTime.of(2026, 11, 9, 23, 30));
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(a, "2026-11-10", "09:00", "10:00");
        inicio().andExpect(jsonPath("$.fecha").value("2026-11-09"))
                .andExpect(jsonPath("$.citasHoy.total").value(1))
                .andExpect(jsonPath("$.citasPorDia[29].fecha").value("2026-11-09"));
    }

    @Test
    void conFechaTodasLasCifrasPorDiaSeCalculanParaEseDia() throws Exception {
        UUID a = lunesDeOchoADoce("A");
        // jueves 12; el mismo día de la semana pasada es el jueves 5
        UUID b = crearServicio("B", true, "50000", "4 08:00 12:00");
        confirmada(b, "2026-11-12", "08:00", "09:00");
        confirmada(b, "2026-11-12", "09:00", "10:00");
        cita(b, "2026-11-12", "10:00", "11:00", "Atendida");
        confirmada(b, "2026-11-05", "08:00", "09:00");
        confirmada(a, "2026-11-09", "09:00", "10:00"); // otro día: no cuenta para citasHoy
        obtener(INICIO + "?fecha=2026-11-12", lector).andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value("2026-11-12"))
                .andExpect(jsonPath("$.citasHoy.total").value(3))
                .andExpect(jsonPath("$.citasHoy.atendidas").value(1))
                .andExpect(jsonPath("$.ocupacionHoy.actual").value(closeTo(180.0 / 240, 1e-12)))
                .andExpect(jsonPath("$.ocupacionHoy.anterior").value(closeTo(60.0 / 240, 1e-12)))
                .andExpect(jsonPath("$.ocupacionHoy.variacion").value(closeTo(2.0, 1e-12)))
                .andExpect(jsonPath("$.citasPorDia", hasSize(30)))
                .andExpect(jsonPath("$.citasPorDia[29].fecha").value("2026-11-12"))
                .andExpect(jsonPath("$.citasPorDia[29].total").value(3));
    }

    @Test
    void unaFechaInvalidaEs400Validacion() throws Exception {
        for (String fecha : new String[] { "abc", "2026-02-30", "2026-13-01", "09-11-2026", "", "2026-11-9" }) {
            obtener(INICIO + "?fecha=" + fecha, lector)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
    }

    @Test
    void unaFechaFueraDeRangoEs400EnLugarDeUnErrorDeLaBase() throws Exception {
        for (String fecha : new String[] { "0001-01-01", "1899-12-31", "3000-01-01", "9999-12-31" }) {
            obtener(INICIO + "?fecha=" + fecha, lector)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
    }

    @Test
    void lasFechasLimiteDelRangoSonValidas() throws Exception {
        for (String fecha : new String[] { "1900-01-01", "2999-12-31" }) {
            obtener(INICIO + "?fecha=" + fecha, lector).andExpect(status().isOk())
                    .andExpect(jsonPath("$.fecha").value(fecha));
        }
    }

    // ---------- Permisos ----------

    @Test
    void sinTokenEs401() throws Exception {
        mvc.perform(get(INICIO)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void sinVerPanelEs403AunqueTengaOtrosPermisosDeLectura() throws Exception {
        for (String permiso : new String[] { "ver_citas", "gestionar_servicios", "gestionar_citas", "atender_citas",
                "gestionar_clientes", "gestionar_usuarios", "gestionar_roles", "gestionar_enlaces" }) {
            UUID otro = crearUsuario("u-" + permiso, crearRol("R-" + permiso, permiso));
            obtener(INICIO, otro).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        }
        UUID sinPermisos = crearUsuario("vacio", crearRol("Vacío"));
        obtener(INICIO, sinPermisos).andExpect(status().isForbidden());
    }

    @Test
    void conVerPanelEs200() throws Exception {
        inicio();
    }
}
