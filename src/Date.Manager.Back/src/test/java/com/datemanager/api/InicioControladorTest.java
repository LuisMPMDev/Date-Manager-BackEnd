package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, ?, 60, ?, ?)",
                id, nombre, activo, new BigDecimal(valor));
        for (String v : ventanas) {
            String[] partes = v.split(" "); // "dia inicio fin"
            ejecutar("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                    + "VALUES (?, ?, ?, ?, ?)", id, UUID.randomUUID(), Integer.parseInt(partes[0]), partes[1], partes[2]);
        }
        return id;
    }

    /** Servicio activo de 50 000 con el lunes de 08:00 a 12:00 (240 min). */
    private UUID lunesDeOchoADoce(String nombre) {
        return crearServicio(nombre, true, "50000", "1 08:00 12:00");
    }

    private UUID crearCliente(String nombre, String apellido) {
        return crearCliente(nombre, apellido, "300 1");
    }

    private UUID crearCliente(String nombre, String apellido, String telefono) {
        UUID id = UUID.randomUUID();
        ejecutar("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, ?, ?, ?, ?)",
                id, nombre, apellido, telefono, id + "@x.co");
        return id;
    }

    private UUID cita(UUID servicio, String fecha, String inicio, String fin, String estado, LocalDateTime creadaEn,
            LocalDateTime canceladaEn) {
        UUID id = UUID.randomUUID();
        ejecutar("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en, "
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

    private Resp inicio() {
        return exigir(200, obtener(INICIO, lector));
    }

    /** Compara el número en {@code ruta} con {@code esperado} (con margen para los decimales). */
    private static void num(Resp r, String ruta, double esperado) {
        BigDecimal real = r.numero(ruta);
        assertTrue(real != null && Math.abs(real.doubleValue() - esperado) < 1e-12,
                () -> ruta + ": esperado " + esperado + ", recibido " + real + " en " + r.texto());
    }

    private static void falta(Resp r, String ruta) {
        assertFalse(r.existe(ruta), () -> ruta + " no debía estar en " + r.texto());
    }

    private static void tamano(Resp r, String ruta, int n) {
        assertEquals(n, r.lista(ruta).size(), () -> ruta + " en " + r.texto());
    }

    // ---------- Día sin citas ----------

    @Test
    void unDiaSinNadaDevuelveCerosSinError() {
        Resp r1 = inicio();
        assertEquals("2026-11-09", r1.en("$.fecha"));
        num(r1, "$.citasHoy.total", 0);
        num(r1, "$.citasHoy.atendidas", 0);
        falta(r1, "$.proxima");
        num(r1, "$.ocupacionHoy.actual", 0.0);
        num(r1, "$.ocupacionHoy.anterior", 0.0);
        falta(r1, "$.ocupacionHoy.variacion");
        num(r1, "$.citasNuevas.actual", 0);
        num(r1, "$.citasNuevas.anterior", 0);
        falta(r1, "$.citasNuevas.variacion");
        num(r1, "$.citasCanceladas.actual", 0);
        falta(r1, "$.citasCanceladas.variacion");
        num(r1, "$.ingresos.actual", 0.0);
        num(r1, "$.ingresos.anterior", 0.0);
        falta(r1, "$.ingresos.variacion");
        tamano(r1, "$.alertas", 0);
        num(r1, "$.alertasExtra", 0);
        tamano(r1, "$.citasPorDia", 30);
        assertEquals(Arrays.asList(zeros(30)), r1.lista("$.citasPorDia[*].total").stream().map(o -> ((Number) o).intValue()).toList());
        tamano(r1, "$.ocupacionSemana", 0);
        tamano(r1, "$.proximas", 0);
    }

    private static Integer[] zeros(int n) {
        Integer[] z = new Integer[n];
        Arrays.fill(z, 0);
        return z;
    }

    @Test
    void conServiciosPeroSinCitasLaOcupacionEsCeroYNoNaN() {
        lunesDeOchoADoce("A");
        Resp r2 = inicio();
        num(r2, "$.ocupacionHoy.actual", 0.0);
        tamano(r2, "$.ocupacionSemana", 1);
        num(r2, "$.ocupacionSemana[0].ocupacion", 0.0);
        String cuerpo = r2.texto();
        assertFalse(cuerpo.contains("NaN") || cuerpo.contains("Infinity"), cuerpo);
    }

    @Test
    void elJsonNuncaTraeNaNNiInfinitoAunConPrevioCero() {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00"); // hoy 25 %, lunes pasado 0
        Resp r3 = inicio();
        String cuerpo = r3.texto();
        assertFalse(cuerpo.contains("NaN") || cuerpo.contains("Infinity"), cuerpo);
    }

    // ---------- Citas hoy y próximas ----------

    @Test
    void citasHoyCuentaLasNoCanceladasYCuantasSeAtendieron() {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        cita(a, "2026-11-09", "10:00", "11:00", "Atendida");
        cita(a, "2026-11-09", "11:00", "12:00", "Cancelada");
        confirmada(a, "2026-11-10", "09:00", "10:00"); // mañana: no
        confirmada(a, "2026-11-08", "09:00", "10:00"); // ayer: no
        Resp r4 = inicio();
        num(r4, "$.citasHoy.total", 2);
        num(r4, "$.citasHoy.atendidas", 1);
    }

    @Test
    void laProximaYLasProximasSonLasConfirmadasQueAunNoEmpiezanOrdenadasHastaCinco() {
        UUID a = lunesDeOchoADoce("Servicio A");
        UUID clienteNuevo = crearCliente("Luis", "Gómez", "310 555 22");
        confirmada(a, "2026-11-12", "09:00", "10:00");
        // ya empezó y sigue en curso (en otro servicio: en el mismo se solaparía con la de las 08:00)
        confirmada(lunesDeOchoADoce("Servicio B"), "2026-11-09", "07:30", "08:30");
        cita(a, "2026-11-09", "09:00", "10:00", "Atendida");
        UUID diez = confirmada(a, "2026-11-09", "10:00", "11:00");
        cita(a, "2026-11-10", "09:00", "10:00", "Cancelada");
        UUID ahoraMismo = cita(a, "2026-11-09", "08:00", "09:00", "Confirmada", hace(100), null);
        confirmada(a, "2026-11-13", "09:00", "10:00");
        confirmada(a, "2026-11-14", "09:00", "10:00");
        confirmada(a, "2026-11-15", "09:00", "10:00"); // la sexta: queda fuera
        ejecutar("UPDATE cita SET cliente_id = ? WHERE id = ?", clienteNuevo, diez);

        Resp r5 = inicio();
        tamano(r5, "$.proximas", 5);
        assertEquals(List.of("2026-11-09", "2026-11-09", "2026-11-12", "2026-11-13", "2026-11-14"), r5.en("$.proximas[*].fecha"));
        assertEquals(List.of("08:00", "10:00", "09:00", "09:00", "09:00"), r5.en("$.proximas[*].inicio"));
        assertEquals(ahoraMismo.toString(), r5.en("$.proximas[0].id"));
        assertEquals("09:00", r5.en("$.proximas[0].fin"));
        assertEquals(true, r5.en("$.proximas[0].esHoy"));
        num(r5, "$.proximas[0].minutosHasta", 0);
        assertEquals(a.toString(), r5.en("$.proximas[0].servicioId"));
        assertEquals("Servicio A", r5.en("$.proximas[0].servicioNombre"));
        assertEquals("Ana", r5.en("$.proximas[0].clienteNombre"));
        assertEquals("Pérez", r5.en("$.proximas[0].clienteApellido"));
        num(r5, "$.proximas[1].minutosHasta", 120);
        assertEquals("300 1", r5.en("$.proximas[0].clienteTelefono"));
        assertEquals("Luis", r5.en("$.proximas[1].clienteNombre"));
        assertEquals("310 555 22", r5.en("$.proximas[1].clienteTelefono"));
        assertEquals(false, r5.en("$.proximas[2].esHoy"));
        falta(r5, "$.proximas[2].minutosHasta");
        assertEquals(ahoraMismo.toString(), r5.en("$.proxima.id"));
        assertEquals("300 1", r5.en("$.proxima.clienteTelefono"));
        assertEquals("08:00", r5.en("$.proxima.inicio"));
        num(r5, "$.proxima.minutosHasta", 0);
    }

    @Test
    void siLaSiguienteEsDeOtroDiaLaProximaNoTrazaMinutos() {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-11", "14:00", "15:00");
        confirmada(a, "2026-11-10", "09:00", "10:00");
        Resp r6 = inicio();
        assertEquals("2026-11-10", r6.en("$.proxima.fecha"));
        assertEquals("09:00", r6.en("$.proxima.inicio"));
        assertEquals(false, r6.en("$.proxima.esHoy"));
        falta(r6, "$.proxima.minutosHasta");
    }

    @Test
    void sinConfirmadasPorDelanteNoHayProximaAunqueHayaCanceladasYPasadas() {
        UUID a = lunesDeOchoADoce("A");
        cita(a, "2026-11-10", "09:00", "10:00", "Cancelada");
        confirmada(a, "2026-11-08", "09:00", "10:00");
        cita(a, "2026-11-12", "09:00", "10:00", "Atendida");
        Resp r7 = inicio();
        falta(r7, "$.proxima");
        tamano(r7, "$.proximas", 0);
    }

    // ---------- Ocupación de hoy ----------

    /** Lunes de 08:00 a 12:00 (240 min). */
    @Test
    void laOcupacionDeHoyComparaConElMismoDiaDeLaSemanaPasada() {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00"); // 60 de 240 = 25 %
        confirmada(a, "2026-11-02", "09:00", "09:30"); // lunes pasado: 30 de 240 = 12,5 %
        // no se cuelan en la cifra de hoy ni en la del lunes pasado
        confirmada(a, "2026-11-10", "09:00", "12:00"); // mañana
        confirmada(a, "2026-11-08", "09:00", "12:00"); // ayer
        confirmada(a, "2026-11-03", "09:00", "12:00"); // martes pasado
        confirmada(a, "2026-11-01", "09:00", "12:00"); // domingo pasado
        Resp r8 = inicio();
        num(r8, "$.ocupacionHoy.actual", 0.25);
        num(r8, "$.ocupacionHoy.anterior", 0.125);
        num(r8, "$.ocupacionHoy.variacion", 1.0);
    }

    @Test
    void siBajaLaVariacionEsNegativa() {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(a, "2026-11-02", "09:00", "11:00");
        Resp r9 = inicio();
        num(r9, "$.ocupacionHoy.variacion", -0.5);
    }

    @Test
    void sinOcupacionLaSemanaPasadaNoHayVariacion() {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        Resp r10 = inicio();
        num(r10, "$.ocupacionHoy.actual", 0.25);
        num(r10, "$.ocupacionHoy.anterior", 0.0);
        falta(r10, "$.ocupacionHoy.variacion");
    }

    @Test
    void sinCambioLaVariacionEsCeroYLaClaveSePublica() {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(a, "2026-11-02", "09:00", "10:00");
        Resp r11 = inicio();
        num(r11, "$.ocupacionHoy.variacion", 0.0);
    }

    @Test
    void lasCanceladasNoOcupanPeroLasAtendidasSi() {
        UUID a = lunesDeOchoADoce("A");
        cita(a, "2026-11-09", "08:00", "10:00", "Cancelada");
        cita(a, "2026-11-09", "10:00", "11:00", "Atendida");
        Resp r12 = inicio();
        num(r12, "$.ocupacionHoy.actual", 0.25);
    }

    @Test
    void losServiciosInactivosYLosQueNoAtiendenHoyNoEntranEnElDenominador() {
        UUID a = lunesDeOchoADoce("A");
        UUID inactivo = crearServicio("Inactivo", false, "1000", "1 08:00 20:00");
        UUID soloMartes = crearServicio("Solo martes", true, "1000", "2 08:00 20:00");
        UUID sinVentanas = crearServicio("Sin ventanas", true, "1000");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(inactivo, "2026-11-09", "09:00", "12:00"); // no suma
        confirmada(soloMartes, "2026-11-09", "09:00", "12:00"); // ese día no atiende: no suma
        confirmada(sinVentanas, "2026-11-09", "09:00", "12:00");
        // solo cuenta A: 60 de 240
        Resp r13 = inicio();
        num(r13, "$.ocupacionHoy.actual", 0.25);
    }

    @Test
    void variosServiciosActivosSumanSusMinutosAntesDeDividir() {
        UUID a = lunesDeOchoADoce("A");
        UUID b = crearServicio("B", true, "1000", "1 08:00 10:00");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(b, "2026-11-09", "08:00", "09:00");
        // 120 de 240 + 120 = 360
        Resp r14 = inicio();
        num(r14, "$.ocupacionHoy.actual", 120.0 / 360);
    }

    // ---------- Citas nuevas y canceladas (30 días) ----------

    @Test
    void citasNuevasCuentaLasCreadasEnLosUltimos30DiasYComparaConLos30Anteriores() {
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
        Resp r15 = inicio();
        num(r15, "$.citasNuevas.actual", 5);
        num(r15, "$.citasNuevas.anterior", 3);
        num(r15, "$.citasNuevas.variacion", 2.0 / 3);
    }

    @Test
    void citasNuevasSinPeriodoPrevioNoTieneVariacion() {
        UUID a = lunesDeOchoADoce("A");
        cita(a, "2026-11-20", "09:00", "10:00", "Confirmada", hace(2), null);
        Resp r16 = inicio();
        num(r16, "$.citasNuevas.actual", 1);
        num(r16, "$.citasNuevas.anterior", 0);
        falta(r16, "$.citasNuevas.variacion");
    }

    @Test
    void citasCanceladasCuentaPorLaFechaDeCancelacionNoDeCreacion() {
        UUID a = lunesDeOchoADoce("A");
        cita(a, "2026-11-20", "09:00", "10:00", "Cancelada", hace(50), hace(1));
        cita(a, "2026-11-21", "09:00", "10:00", "Cancelada", hace(50), hace(10));
        cita(a, "2026-11-22", "09:00", "10:00", "Cancelada", hace(70), hace(30)); // justo 30 d: periodo anterior
        cita(a, "2026-11-23", "09:00", "10:00", "Cancelada", hace(70), hace(45));
        cita(a, "2026-11-24", "09:00", "10:00", "Cancelada", hace(80), hace(60)); // justo 60 d: fuera
        cita(a, "2026-11-25", "09:00", "10:00", "Confirmada", hace(1), null); // no cancelada
        Resp r17 = inicio();
        num(r17, "$.citasCanceladas.actual", 2);
        num(r17, "$.citasCanceladas.anterior", 2);
        num(r17, "$.citasCanceladas.variacion", 0.0);
    }

    @Test
    void masCanceladasQueAntesEsUnaVariacionPositiva() {
        UUID a = lunesDeOchoADoce("A");
        cita(a, "2026-11-20", "09:00", "10:00", "Cancelada", hace(50), hace(1));
        cita(a, "2026-11-21", "09:00", "10:00", "Cancelada", hace(50), hace(10));
        cita(a, "2026-11-22", "09:00", "10:00", "Cancelada", hace(70), hace(45));
        Resp r18 = inicio();
        num(r18, "$.citasCanceladas.variacion", 1.0);
    }

    // ---------- Ingresos estimados ----------

    @Test
    void ingresosSumaLosValoresDeLosUltimos30DiasHastaHoyYComparaConLos30Anteriores() {
        UUID a = lunesDeOchoADoce("A"); // 50 000 por cita
        confirmada(a, "2026-11-09", "09:00", "10:00"); // hoy: entra
        confirmada(a, "2026-10-11", "09:00", "10:00"); // hoy - 29: entra
        confirmada(a, "2026-10-10", "09:00", "10:00"); // hoy - 30: periodo anterior
        confirmada(a, "2026-09-11", "09:00", "10:00"); // hoy - 59: periodo anterior
        confirmada(a, "2026-09-10", "09:00", "10:00"); // hoy - 60: fuera
        cita(a, "2026-10-20", "09:00", "10:00", "Cancelada"); // no suma
        confirmada(a, "2026-11-10", "09:00", "10:00"); // futura: no suma
        Resp r19 = inicio();
        num(r19, "$.ingresos.actual", 100000.0);
        num(r19, "$.ingresos.anterior", 100000.0);
        num(r19, "$.ingresos.variacion", 0.0);
    }

    @Test
    void ingresosMuestraLaVariacionFrenteAlPeriodoAnterior() {
        UUID a = lunesDeOchoADoce("A");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(a, "2026-11-08", "09:00", "10:00");
        confirmada(a, "2026-11-07", "09:00", "10:00");
        confirmada(a, "2026-10-01", "09:00", "10:00");
        confirmada(a, "2026-10-02", "09:00", "10:00");
        Resp r20 = inicio();
        num(r20, "$.ingresos.actual", 150000.0);
        num(r20, "$.ingresos.anterior", 100000.0);
        num(r20, "$.ingresos.variacion", 0.5);
    }

    @Test
    void ingresosSeSumaEnBigDecimalYSeSerializaComoNumeroJson() {
        UUID barato = crearServicio("Barato", true, "0.10", "1 08:00 12:00");
        confirmada(barato, "2026-11-09", "08:00", "09:00");
        confirmada(barato, "2026-11-09", "09:00", "10:00");
        confirmada(barato, "2026-11-09", "10:00", "11:00");
        Resp r21 = inicio();
        num(r21, "$.ingresos.actual", 0.3);
        String cuerpo = r21.texto();
        // número JSON (no texto) y sin el error de los binarios: 0.1 + 0.1 + 0.1 en double es 0.30000000000000004
        assertTrue(cuerpo.contains("\"actual\":0.30"), cuerpo);
        assertFalse(cuerpo.contains("0.30000000000000004"), cuerpo);
        assertFalse(cuerpo.contains("\"actual\":\""), cuerpo);
    }

    @Test
    void ingresosConValoresGrandesYDecimales() {
        UUID caro = crearServicio("Caro", true, "9999999999.99", "1 08:00 12:00");
        confirmada(caro, "2026-11-09", "08:00", "09:00");
        confirmada(caro, "2026-11-09", "09:00", "10:00");
        Resp r22 = inicio();
        String cuerpo = r22.texto();
        assertTrue(cuerpo.contains("\"actual\":19999999999.98"), cuerpo);
    }

    // ---------- Alertas ----------

    @Test
    void alertasMuestraLasCitasNuevasYCanceladasDeLasUltimas24HYLosServiciosSinHorario() {
        UUID corte = crearServicio("Corte", true, "1000", "1 08:00 12:00");
        crearServicio("Masaje", true, "1000"); // activo sin ventanas
        crearServicio("Inactivo sin ventanas", false, "1000"); // no genera alerta
        UUID nueva = cita(corte, "2026-11-10", "09:00", "10:00", "Confirmada", AHORA_UTC.minusHours(2), null);
        cita(corte, "2026-11-10", "10:00", "11:00", "Confirmada", AHORA_UTC.minusHours(30), null); // vieja
        UUID cancelada = cita(corte, "2026-11-11", "09:00", "10:00", "Cancelada", hace(5), AHORA_UTC.minusHours(3));
        cita(corte, "2026-11-11", "10:00", "11:00", "Cancelada", hace(5), AHORA_UTC.minusHours(30)); // vieja
        // una cancelada creada hace poco pero cancelada hace más de 24 h (no es «nueva»: está cancelada)
        cita(corte, "2026-11-12", "09:00", "10:00", "Cancelada", AHORA_UTC.minusHours(40), AHORA_UTC.minusHours(26));

        Resp r23 = inicio();
        tamano(r23, "$.alertas", 3);
        assertEquals("cita_nueva", r23.en("$.alertas[0].tipo"));
        assertEquals("Cita nueva", r23.en("$.alertas[0].titulo"));
        assertEquals("Corte, Martes 10 de noviembre a las 09:00", r23.en("$.alertas[0].detalle"));
        assertEquals(nueva.toString(), r23.en("$.alertas[0].ref"));
        assertEquals("cita_cancelada", r23.en("$.alertas[1].tipo"));
        assertEquals("Cita cancelada", r23.en("$.alertas[1].titulo"));
        assertEquals("Corte, Miércoles 11 de noviembre a las 09:00", r23.en("$.alertas[1].detalle"));
        assertEquals(cancelada.toString(), r23.en("$.alertas[1].ref"));
        assertEquals("servicio_sin_horario", r23.en("$.alertas[2].tipo"));
        assertEquals("Servicio sin horario", r23.en("$.alertas[2].titulo"));
        assertEquals("Masaje no tiene horarios de atención", r23.en("$.alertas[2].detalle"));
        num(r23, "$.alertasExtra", 0);
    }

    @Test
    void alertasMuestraSeisYDiceCuantasQuedanFuera() {
        UUID a = lunesDeOchoADoce("A");
        for (int i = 0; i < 9; i++) {
            cita(a, "2026-11-" + (10 + i), "09:00", "10:00", "Confirmada", AHORA_UTC.minusMinutes(10 + i), null);
        }
        // las más recientes primero
        Resp r24 = inicio();
        tamano(r24, "$.alertas", 6);
        assertEquals("A, Martes 10 de noviembre a las 09:00", r24.en("$.alertas[0].detalle"));
        assertEquals("A, Domingo 15 de noviembre a las 09:00", r24.en("$.alertas[5].detalle"));
        num(r24, "$.alertasExtra", 3);
    }

    @Test
    void unServicioActivoConVentanasPeroSinHorarioHoyNoGeneraAlerta() {
        crearServicio("Solo martes", true, "1000", "2 08:00 12:00");
        Resp r25 = inicio();
        tamano(r25, "$.alertas", 0);
    }

    // ---------- Citas por día ----------

    @Test
    void citasPorDiaCubreLosUltimos30DiasHastaHoyYNoCuentaCanceladasNiFuturas() {
        UUID a = lunesDeOchoADoce("A");
        UUID b = lunesDeOchoADoce("B");
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(b, "2026-11-09", "09:00", "10:00");
        cita(a, "2026-11-09", "10:00", "11:00", "Cancelada");
        confirmada(a, "2026-10-11", "09:00", "10:00"); // hoy - 29: primer día
        confirmada(a, "2026-10-10", "09:00", "10:00"); // hoy - 30: fuera
        confirmada(a, "2026-11-10", "09:00", "10:00"); // mañana: fuera
        Resp r26 = inicio();
        tamano(r26, "$.citasPorDia", 30);
        assertEquals("2026-10-11", r26.en("$.citasPorDia[0].fecha"));
        num(r26, "$.citasPorDia[0].total", 1);
        assertEquals("2026-10-12", r26.en("$.citasPorDia[1].fecha"));
        num(r26, "$.citasPorDia[1].total", 0);
        assertEquals("2026-11-01", r26.en("$.citasPorDia[21].fecha"));
        assertEquals("2026-11-09", r26.en("$.citasPorDia[29].fecha"));
        num(r26, "$.citasPorDia[29].total", 2);
    }

    // ---------- Ocupación por servicio de la semana ----------

    private UUID semanal(String nombre, boolean activo) {
        return crearServicio(nombre, activo, "1000", "1 08:00 12:00", "2 08:00 12:00", "3 08:00 12:00",
                "4 08:00 12:00", "5 08:00 12:00");
    }

    @Test
    void ocupacionSemanaEsPorServicioActivoDeLunesADomingoDeLaSemanaDeHoy() {
        UUID a = semanal("Servicio A", true);
        UUID b = semanal("Servicio B", true);
        UUID inactivo = semanal("Servicio C", false);
        confirmada(a, "2026-11-09", "08:00", "09:00");
        confirmada(a, "2026-11-13", "08:00", "09:00"); // 120 de 1200 = 10 %
        confirmada(a, "2026-11-16", "08:00", "12:00"); // semana siguiente
        confirmada(a, "2026-11-06", "08:00", "12:00"); // semana anterior
        cita(a, "2026-11-10", "08:00", "12:00", "Cancelada");
        confirmada(inactivo, "2026-11-11", "08:00", "12:00");
        Resp r27 = inicio();
        tamano(r27, "$.ocupacionSemana", 2);
        assertEquals(a.toString(), r27.en("$.ocupacionSemana[0].servicioId"));
        assertEquals("Servicio A", r27.en("$.ocupacionSemana[0].nombre"));
        num(r27, "$.ocupacionSemana[0].ocupacion", 0.1);
        assertEquals(b.toString(), r27.en("$.ocupacionSemana[1].servicioId"));
        num(r27, "$.ocupacionSemana[1].ocupacion", 0.0);
    }

    @Test
    void unDomingoSigueContandoLaSemanaQueEmpezoElLunesAnteriorYUnLunesEmpiezaOtra() {
        UUID a = semanal("A", true);
        confirmada(a, "2026-11-09", "08:00", "12:00"); // 240 de 1200 = 20 %
        fijarAhora(LocalDateTime.of(2026, 11, 15, 10, 0));
        Resp r28 = inicio();
        assertEquals("2026-11-15", r28.en("$.fecha"));
        num(r28, "$.ocupacionSemana[0].ocupacion", 0.2);
        fijarAhora(LocalDateTime.of(2026, 11, 16, 10, 0));
        Resp r29 = inicio();
        assertEquals("2026-11-16", r29.en("$.fecha"));
        num(r29, "$.ocupacionSemana[0].ocupacion", 0.0);
    }

    // ---------- Fecha ----------

    @Test
    void sinFechaHoyEsLaFechaDelNegocioAunqueLaJvmEstenEnOtroDia() {
        UUID a = semanal("A", true);
        // 23:30 en Bogotá son las 04:30 UTC del día siguiente
        fijarAhora(LocalDateTime.of(2026, 11, 9, 23, 30));
        confirmada(a, "2026-11-09", "09:00", "10:00");
        confirmada(a, "2026-11-10", "09:00", "10:00");
        Resp r30 = inicio();
        assertEquals("2026-11-09", r30.en("$.fecha"));
        num(r30, "$.citasHoy.total", 1);
        assertEquals("2026-11-09", r30.en("$.citasPorDia[29].fecha"));
    }

    @Test
    void conFechaTodasLasCifrasPorDiaSeCalculanParaEseDia() {
        UUID a = lunesDeOchoADoce("A");
        // jueves 12; el mismo día de la semana pasada es el jueves 5
        UUID b = crearServicio("B", true, "50000", "4 08:00 12:00");
        confirmada(b, "2026-11-12", "08:00", "09:00");
        confirmada(b, "2026-11-12", "09:00", "10:00");
        cita(b, "2026-11-12", "10:00", "11:00", "Atendida");
        confirmada(b, "2026-11-05", "08:00", "09:00");
        confirmada(a, "2026-11-09", "09:00", "10:00"); // otro día: no cuenta para citasHoy
        Resp r31 = exigir(200, obtener(INICIO + "?fecha=2026-11-12", lector));
        assertEquals("2026-11-12", r31.en("$.fecha"));
        num(r31, "$.citasHoy.total", 3);
        num(r31, "$.citasHoy.atendidas", 1);
        num(r31, "$.ocupacionHoy.actual", 180.0 / 240);
        num(r31, "$.ocupacionHoy.anterior", 60.0 / 240);
        num(r31, "$.ocupacionHoy.variacion", 2.0);
        tamano(r31, "$.citasPorDia", 30);
        assertEquals("2026-11-12", r31.en("$.citasPorDia[29].fecha"));
        num(r31, "$.citasPorDia[29].total", 3);
    }

    @Test
    void unaFechaInvalidaEs400Validacion() {
        for (String fecha : new String[] { "abc", "2026-02-30", "2026-13-01", "09-11-2026", "", "2026-11-9" }) {
            Resp r32 = exigir(400, obtener(INICIO + "?fecha=" + fecha, lector));
            assertEquals("VALIDACION", r32.en("$.codigo"));
        }
    }

    @Test
    void unaFechaFueraDeRangoEs400EnLugarDeUnErrorDeLaBase() {
        for (String fecha : new String[] { "0001-01-01", "1899-12-31", "3000-01-01", "9999-12-31" }) {
            Resp r33 = exigir(400, obtener(INICIO + "?fecha=" + fecha, lector));
            assertEquals("VALIDACION", r33.en("$.codigo"));
        }
    }

    @Test
    void lasFechasLimiteDelRangoSonValidas() {
        for (String fecha : new String[] { "1900-01-01", "2999-12-31" }) {
            Resp r34 = exigir(200, obtener(INICIO + "?fecha=" + fecha, lector));
            assertEquals(fecha, r34.en("$.fecha"));
        }
    }

    // ---------- Permisos ----------

    @Test
    void sinTokenEs401() {
        exigir(401, "NO_AUTENTICADO", get(INICIO).enviar());
    }

    @Test
    void sinVerPanelEs403AunqueTengaOtrosPermisosDeLectura() {
        for (String permiso : new String[] { "ver_citas", "gestionar_servicios", "gestionar_citas", "atender_citas",
                "gestionar_clientes", "gestionar_usuarios", "gestionar_roles", "gestionar_enlaces" }) {
            UUID otro = crearUsuario("u-" + permiso, crearRol("R-" + permiso, permiso));
            Resp r35 = exigir(403, obtener(INICIO, otro));
            assertEquals("SIN_PERMISO", r35.en("$.codigo"));
        }
        UUID sinPermisos = crearUsuario("vacio", crearRol("Vacío"));
        Resp r36 = exigir(403, obtener(INICIO, sinPermisos));
    }

    @Test
    void conVerPanelEs200() {
        inicio();
    }
}
