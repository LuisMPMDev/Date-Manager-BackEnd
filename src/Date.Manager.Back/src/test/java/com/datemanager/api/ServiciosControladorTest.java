package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /api/panel/servicios}. Las pruebas de {@code ServiciosServicio.buscar} y de las restricciones de la base que
 * en la versión Spring vivían aquí están en {@code ServiciosServicioTest}.
 */
class ServiciosControladorTest extends PruebaPanel {

    private static final String SERVICIOS = "/api/panel/servicios";

    private UUID admin;

    @BeforeEach
    void crearGestor() {
        admin = crearUsuario("gestor", crearRol("Gestor", "gestionar_servicios"));
    }

    private static Map<String, Object> ventana(Object id, int dia, String inicio, String fin) {
        Map<String, Object> v = new LinkedHashMap<>();
        if (id != null) {
            v.put("id", id.toString());
        }
        v.put("dia", dia);
        v.put("inicio", inicio);
        v.put("fin", fin);
        return v;
    }

    private static Map<String, Object> cuerpo(String nombre, int duracionMin, boolean activo, Object valor,
            List<Map<String, Object>> ventanas) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("nombre", nombre);
        c.put("duracionMin", duracionMin);
        c.put("activo", activo);
        c.put("valor", valor);
        c.put("ventanas", ventanas);
        return c;
    }

    @SafeVarargs
    private static Map<String, Object> masaje(Map<String, Object>... ventanas) {
        return cuerpo("Masaje", 45, true, 50000, List.of(ventanas));
    }

    private List<String> ventanasEnBase(UUID servicioId) {
        return columna("""
                SELECT CONCAT(dia, N' ', CONVERT(NVARCHAR(5), hora_inicio, 108), N'-', CONVERT(NVARCHAR(5), hora_fin, 108))
                FROM ventana_disponibilidad WHERE servicio_id = ? ORDER BY dia, hora_inicio
                """, servicioId);
    }

    private static String idUnico() {
        return consultarTexto("SELECT CONVERT(NVARCHAR(36), id) FROM servicio");
    }

    // ---------- Crear y leer ----------

    @Test
    void creaUnServicioConVentanasYLoLee() {
        UUID id = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();

        Resp r = exigir(200, guardar(SERVICIOS + "/" + id, admin,
                masaje(ventana(v1, 1, "09:00", "12:00"), ventana(v2, 3, "14:00", "17:30"))));
        assertEquals(id.toString(), r.en("id"));
        assertEquals("Masaje", r.en("nombre"));
        igualNumero("45", r.numero("duracionMin"));
        assertEquals(true, r.en("activo"));
        assertTrue(r.en("valor") instanceof Number, "valor debe ser un número");
        igualNumero("50000", r.numero("valor"));
        assertEquals(2, r.lista("ventanas").size());
        assertEquals(v1.toString(), r.en("ventanas[0].id"));
        igualNumero("1", r.numero("ventanas[0].dia"));
        assertEquals("09:00", r.en("ventanas[0].inicio"));
        assertEquals("12:00", r.en("ventanas[0].fin"));
        assertEquals(v2.toString(), r.en("ventanas[1].id"));
        assertEquals("14:00", r.en("ventanas[1].inicio"));
        assertEquals("17:30", r.en("ventanas[1].fin"));

        Resp lista = exigir(200, obtener(SERVICIOS, admin));
        assertEquals(1, lista.lista("$").size());
        assertEquals(id.toString(), lista.en("$[0].id"));
        igualNumero("50000", lista.numero("$[0].valor"));
        assertEquals(2, lista.lista("$[0].ventanas").size());
        assertEquals("17:30", lista.en("$[0].ventanas[1].fin"));
        assertEquals(List.of("1 09:00-12:00", "3 14:00-17:30"), ventanasEnBase(id));
    }

    @Test
    void unIdDesconocidoEnPutCreaElServicioConEseId() {
        UUID id = UUID.randomUUID();

        exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje()));

        assertEquals(1, contar("servicio"));
        assertTrue(id.toString().equalsIgnoreCase(idUnico()));
    }

    @Test
    void elValorSeGuardaConDosDecimales() {
        UUID id = UUID.randomUUID();

        Resp r = exigir(200, guardar(SERVICIOS + "/" + id, admin, cuerpo("Corte", 30, true, 12500.5, List.of())));
        igualNumero("12500.5", r.numero("valor"));

        assertEquals("12500.50", consultarTexto("SELECT CONVERT(NVARCHAR(20), valor) FROM servicio WHERE id = ?", id));
        igualNumero("12500.5", obtener(SERVICIOS, admin).numero("$[0].valor"));
    }

    @Test
    void elIdDelCuerpoSeIgnoraYManda_ElDeLaRuta() {
        UUID ruta = UUID.randomUUID();
        UUID otro = UUID.randomUUID();
        Map<String, Object> c = masaje();
        c.put("id", otro.toString());

        Resp r = exigir(200, guardar(SERVICIOS + "/" + ruta, admin, c));
        assertEquals(ruta.toString(), r.en("id"));

        assertEquals(1, contar("servicio"));
        assertTrue(ruta.toString().equalsIgnoreCase(idUnico()));
    }

    @Test
    void elIdDeLaRutaQueNoEsUuidEsUn400() {
        exigir(400, "VALIDACION", guardar(SERVICIOS + "/no-es-uuid", admin, masaje()));
    }

    @Test
    void siUnaVentanaNoTraeIdElServidorLeGeneraUno() {
        UUID id = UUID.randomUUID();

        Resp r = exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje(ventana(null, 2, "08:00", "10:00"))));
        assertNotNull(r.en("ventanas[0].id"));

        assertEquals(1, contar("ventana_disponibilidad"));
    }

    // ---------- Editar reemplaza ventanas ----------

    @Test
    void editarReemplazaLasVentanasPorLasRecibidas() {
        UUID id = UUID.randomUUID();
        UUID conservada = UUID.randomUUID();
        UUID quitada = UUID.randomUUID();
        exigir(200, guardar(SERVICIOS + "/" + id, admin,
                masaje(ventana(conservada, 1, "09:00", "12:00"), ventana(quitada, 2, "09:00", "12:00"))));
        UUID nueva = UUID.randomUUID();

        Resp r = exigir(200, guardar(SERVICIOS + "/" + id, admin, cuerpo("Masaje relajante", 60, true, 70000, List.of(
                ventana(conservada, 1, "10:00", "13:00"), ventana(nueva, 5, "15:00", "18:00")))));
        assertEquals("Masaje relajante", r.en("nombre"));
        igualNumero("60", r.numero("duracionMin"));
        assertEquals(2, r.lista("ventanas").size());
        assertEquals(conservada.toString(), r.en("ventanas[0].id"));
        assertEquals("10:00", r.en("ventanas[0].inicio"));
        assertEquals(nueva.toString(), r.en("ventanas[1].id"));

        assertEquals(1, contar("servicio"));
        assertEquals(List.of("1 10:00-13:00", "5 15:00-18:00"), ventanasEnBase(id));
        assertEquals(2, contar("ventana_disponibilidad"));
    }

    @Test
    void reenviarLasMismasVentanasConLosMismosIdsNoChocaConLaLlavePrimaria() {
        UUID id = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        Map<String, Object> c = masaje(ventana(v1, 1, "09:00", "12:00"), ventana(v2, 1, "14:00", "16:00"));

        exigir(200, guardar(SERVICIOS + "/" + id, admin, c));
        exigir(200, guardar(SERVICIOS + "/" + id, admin, c));
        exigir(200, guardar(SERVICIOS + "/" + id, admin, c));

        assertEquals(2, contar("ventana_disponibilidad"));
        assertEquals(List.of("1 09:00-12:00", "1 14:00-16:00"), ventanasEnBase(id));
    }

    // El disparador tr_ventana_sin_cruce (V9) rechaza cualquier paso que deje dos ventanas cruzadas, aunque sea un
    // instante: los guardados cuyo conjunto final es válido no deben pasar por un cruce intermedio.

    @Test
    void reemplazarUnaVentanaPorOtraDeIdNuevoQueSeCruzaConLaAnteriorFunciona() {
        UUID id = UUID.randomUUID();
        exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje(ventana(UUID.randomUUID(), 1, "09:00", "10:00"))));
        UUID nueva = UUID.randomUUID();

        Resp r = exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje(ventana(nueva, 1, "09:30", "10:30"))));
        assertEquals(1, r.lista("ventanas").size());
        assertEquals(nueva.toString(), r.en("ventanas[0].id"));
        assertEquals("09:30", r.en("ventanas[0].inicio"));
        assertEquals("10:30", r.en("ventanas[0].fin"));

        assertEquals(List.of("1 09:30-10:30"), ventanasEnBase(id));
    }

    @Test
    void desplazarDosVentanasContiguasConSusMismosIdsFunciona() {
        UUID id = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        exigir(200, guardar(SERVICIOS + "/" + id, admin,
                masaje(ventana(v1, 1, "09:00", "10:00"), ventana(v2, 1, "10:00", "11:00"))));

        Resp r = exigir(200, guardar(SERVICIOS + "/" + id, admin,
                masaje(ventana(v1, 1, "10:00", "11:00"), ventana(v2, 1, "11:00", "12:00"))));
        assertEquals(2, r.lista("ventanas").size());
        assertEquals(v1.toString(), r.en("ventanas[0].id"));
        assertEquals("10:00", r.en("ventanas[0].inicio"));
        assertEquals(v2.toString(), r.en("ventanas[1].id"));
        assertEquals("12:00", r.en("ventanas[1].fin"));

        assertEquals(List.of("1 10:00-11:00", "1 11:00-12:00"), ventanasEnBase(id));
    }

    @Test
    void intercambiarLosHorariosDeDosVentanasConSusMismosIdsFunciona() {
        UUID id = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        exigir(200, guardar(SERVICIOS + "/" + id, admin,
                masaje(ventana(v1, 1, "09:00", "10:00"), ventana(v2, 1, "10:00", "12:00"))));

        Resp r = exigir(200, guardar(SERVICIOS + "/" + id, admin,
                masaje(ventana(v1, 1, "09:00", "11:00"), ventana(v2, 1, "11:00", "12:00"))));
        assertEquals(v1.toString(), r.en("ventanas[0].id"));
        assertEquals(v2.toString(), r.en("ventanas[1].id"));

        assertEquals(List.of("1 09:00-11:00", "1 11:00-12:00"), ventanasEnBase(id));
    }

    @Test
    void unConjuntoFinalConCruceRealSigueSiendoUn400ConElMensajeDeLaAplicacionYNoCambiaNada() {
        UUID id = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje(ventana(v1, 1, "09:00", "10:00"))));

        Resp r = exigir(400, "VALIDACION", guardar(SERVICIOS + "/" + id, admin, masaje(ventana(v1, 1, "09:00", "10:00"),
                ventana(UUID.randomUUID(), 1, "09:30", "10:30"))));
        assertEquals("Lunes: las ventanas 09:00–10:00 y 09:30–10:30 se cruzan. "
                + "Ajusta las horas para que una termine donde empieza la otra o antes.", r.en("mensaje"));

        assertEquals(List.of("1 09:00-10:00"), ventanasEnBase(id));
    }

    @Test
    void cambiarElDiaDeUnaVentanaConElMismoIdLaMueve() {
        UUID id = UUID.randomUUID();
        UUID v = UUID.randomUUID();
        exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje(ventana(v, 1, "09:00", "12:00"))));

        Resp r = exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje(ventana(v, 4, "09:00", "12:00"))));
        igualNumero("4", r.numero("ventanas[0].dia"));

        assertEquals(List.of("4 09:00-12:00"), ventanasEnBase(id));
    }

    @Test
    void editarConUnaListaVaciaQuitaTodasLasVentanas() {
        UUID id = UUID.randomUUID();
        exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje(ventana(UUID.randomUUID(), 1, "09:00", "12:00"))));

        Resp r = exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje()));
        assertEquals(0, r.lista("ventanas").size());

        assertEquals(0, contar("ventana_disponibilidad"));
    }

    @Test
    void losMismosIdsDeVentanaEnDosServiciosNoChocanYCadaUnaPerteneceAlSuyo() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID compartida = UUID.randomUUID();

        exigir(200, guardar(SERVICIOS + "/" + a, admin,
                cuerpo("A", 30, true, 1, List.of(ventana(compartida, 1, "09:00", "10:00")))));
        exigir(200, guardar(SERVICIOS + "/" + b, admin,
                cuerpo("B", 30, true, 1, List.of(ventana(compartida, 2, "11:00", "12:00")))));

        assertEquals(2, contar("ventana_disponibilidad"));
        assertEquals(List.of("1 09:00-10:00"), ventanasEnBase(a));
        assertEquals(List.of("2 11:00-12:00"), ventanasEnBase(b));

        // Editar B no toca la ventana de A aunque compartan id.
        exigir(200, guardar(SERVICIOS + "/" + b, admin, cuerpo("B", 30, true, 1, List.of())));
        assertEquals(List.of("1 09:00-10:00"), ventanasEnBase(a));
        assertTrue(ventanasEnBase(b).isEmpty());
        Resp lista = obtener(SERVICIOS, admin);
        assertEquals("A", lista.en("$[0].nombre"));
        assertEquals(1, lista.lista("$[0].ventanas").size());
        assertEquals("B", lista.en("$[1].nombre"));
        assertEquals(0, lista.lista("$[1].ventanas").size());
    }

    @Test
    void dosVentanasConElMismoIdEnLaMismaPeticionSonUn400() {
        UUID id = UUID.randomUUID();
        UUID repetida = UUID.randomUUID();

        exigir(400, "VALIDACION", guardar(SERVICIOS + "/" + id, admin,
                masaje(ventana(repetida, 1, "09:00", "10:00"), ventana(repetida, 2, "09:00", "10:00"))));

        assertEquals(0, contar("servicio"));
    }

    // ---------- Validación ----------

    @Test
    void unNombreEnBlancoEsUn400ConElMensajeDeValidaciones() {
        Resp r = exigir(400, "VALIDACION",
                guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("   ", 45, true, 100, List.of())));
        assertEquals("Escribe el nombre del servicio.", r.en("mensaje"));
        assertEquals(0, contar("servicio"));
    }

    @Test
    void unaDuracionDeMasDeVeinticuatroHorasEsUn400YLaDeExactamente1440Vale() {
        Resp r = exigir(400, "VALIDACION",
                guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("Masaje", 1441, true, 100, List.of())));
        assertEquals("La duración no puede superar 24 horas (1440 minutos).", r.en("mensaje"));
        assertEquals(0, contar("servicio"));

        Resp ok = exigir(200,
                guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("Masaje", 1440, true, 100, List.of())));
        igualNumero("1440", ok.numero("duracionMin"));
        assertEquals(1, contar("servicio"));
    }

    @Test
    void unaDuracionCeroONegativaEsUn400() {
        for (int duracion : new int[] {0, -30}) {
            Resp r = exigir(400, "VALIDACION", guardar(SERVICIOS + "/" + UUID.randomUUID(), admin,
                    cuerpo("Masaje", duracion, true, 100, List.of())));
            assertEquals("La duración debe ser un número entero de minutos mayor que cero.", r.en("mensaje"));
        }
    }

    @Test
    void unValorNegativoEsUn400() {
        Resp r = exigir(400, "VALIDACION",
                guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("Masaje", 45, true, -1, List.of())));
        assertEquals("El valor no puede ser negativo.", r.en("mensaje"));
    }

    @Test
    void unValorConMasDeDosDecimalesOQueNoCabeEnLaColumnaEsUn400() {
        exigir(400, "VALIDACION",
                guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("Masaje", 45, true, 10.555, List.of())));
        exigir(400, "VALIDACION", guardar(SERVICIOS + "/" + UUID.randomUUID(), admin,
                cuerpo("Masaje", 45, true, new BigDecimal("12345678901.00"), List.of())));
        assertEquals(0, contar("servicio"));
    }

    @Test
    void faltanDatosObligatoriosEsUn400() {
        Resp r = exigir(400, "VALIDACION", guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, Map.of("nombre", "Masaje")));
        assertEquals("Indica si el servicio está activo.", r.en("campos.activo"));
        assertEquals(0, contar("servicio"));
    }

    @Test
    void unNombreMasLargoQueLaColumnaEsUn400() {
        Resp r = exigir(400, "VALIDACION", guardar(SERVICIOS + "/" + UUID.randomUUID(), admin,
                cuerpo("x".repeat(151), 45, true, 100, List.of())));
        assertEquals("El nombre es demasiado largo.", r.en("campos.nombre"));
    }

    @Test
    void lasVentanasCruzadasSeRechazanConElMensajeEspanol() {
        Resp r = exigir(400, "VALIDACION", guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(
                ventana(UUID.randomUUID(), 1, "09:00", "12:00"), ventana(UUID.randomUUID(), 1, "11:00", "14:00"))));
        assertEquals("Lunes: las ventanas 09:00–12:00 y 11:00–14:00 se cruzan. "
                + "Ajusta las horas para que una termine donde empieza la otra o antes.", r.en("mensaje"));
        assertEquals(0, contar("servicio"));
        assertEquals(0, contar("ventana_disponibilidad"));
    }

    @Test
    void ventanasQueSoloSeTocanSonValidas() {
        exigir(200, guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(
                ventana(UUID.randomUUID(), 1, "09:00", "12:00"), ventana(UUID.randomUUID(), 1, "12:00", "14:00"))));
    }

    @Test
    void unaVentanaConFinNoPosteriorAlInicioEsUn400() {
        Resp r = exigir(400, "VALIDACION", guardar(SERVICIOS + "/" + UUID.randomUUID(), admin,
                masaje(ventana(UUID.randomUUID(), 2, "12:00", "12:00"))));
        assertEquals("Martes: la hora de fin debe ser posterior a la de inicio.", r.en("mensaje"));
        exigir(400, guardar(SERVICIOS + "/" + UUID.randomUUID(), admin,
                masaje(ventana(UUID.randomUUID(), 2, "13:00", "12:00"))));
    }

    @Test
    void unDiaFueraDeUnoASieteEsUn400AntesDeLlegarALaBase() {
        for (int dia : new int[] {0, 8, -1, 99}) {
            exigir(400, "VALIDACION", guardar(SERVICIOS + "/" + UUID.randomUUID(), admin,
                    masaje(ventana(UUID.randomUUID(), dia, "09:00", "10:00"))));
        }
        assertEquals(0, contar("servicio"));
        assertEquals(0, contar("ventana_disponibilidad"));
    }

    @Test
    void losDiasUnoYSieteSonValidos() {
        exigir(200, guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(
                ventana(UUID.randomUUID(), 1, "09:00", "10:00"), ventana(UUID.randomUUID(), 7, "09:00", "10:00"))));
    }

    @Test
    void unaHoraConFormatoIncorrectoOFaltanteEsUn400() {
        for (String mala : new String[] {"9:00", "25:00", "09:00:30", "mediodía", ""}) {
            exigir(400, "VALIDACION", guardar(SERVICIOS + "/" + UUID.randomUUID(), admin,
                    masaje(ventana(UUID.randomUUID(), 1, mala, "12:00"))));
        }
        Map<String, Object> sinFin = ventana(UUID.randomUUID(), 1, "09:00", "10:00");
        sinFin.remove("fin");
        exigir(400, "VALIDACION", guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(sinFin)));
        assertEquals(0, contar("servicio"));
    }

    @Test
    void unaEdicionInvalidaNoModificaNada() {
        UUID id = UUID.randomUUID();
        exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje(ventana(UUID.randomUUID(), 1, "09:00", "12:00"))));

        exigir(400, guardar(SERVICIOS + "/" + id, admin, cuerpo("Otro", 45, true, 1, List.of(
                ventana(UUID.randomUUID(), 1, "09:00", "12:00"), ventana(UUID.randomUUID(), 1, "10:00", "11:00")))));

        Resp lista = obtener(SERVICIOS, admin);
        assertEquals("Masaje", lista.en("$[0].nombre"));
        assertEquals(1, lista.lista("$[0].ventanas").size());
    }

    // ---------- Desactivar ----------

    @Test
    void desactivarConservaElServicioYSusVentanasYSigueEnElListado() {
        UUID id = UUID.randomUUID();
        UUID v = UUID.randomUUID();
        exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje(ventana(v, 1, "09:00", "12:00"))));

        Resp r = exigir(200, guardar(SERVICIOS + "/" + id, admin,
                cuerpo("Masaje", 45, false, 50000, List.of(ventana(v, 1, "09:00", "12:00")))));
        assertEquals(false, r.en("activo"));

        Resp lista = obtener(SERVICIOS, admin);
        assertEquals(1, lista.lista("$").size());
        assertEquals(false, lista.en("$[0].activo"));
        assertEquals("Masaje", lista.en("$[0].nombre"));
        igualNumero("45", lista.numero("$[0].duracionMin"));
        assertEquals(1, lista.lista("$[0].ventanas").size());
        assertEquals(v.toString(), lista.en("$[0].ventanas[0].id"));
        assertEquals(List.of("1 09:00-12:00"), ventanasEnBase(id));

        // Y se puede reactivar.
        Resp reactivado = guardar(SERVICIOS + "/" + id, admin,
                cuerpo("Masaje", 45, true, 50000, List.of(ventana(v, 1, "09:00", "12:00"))));
        assertEquals(true, reactivado.en("activo"));
    }

    // ---------- Orden ----------

    @Test
    void elListadoVaOrdenadoPorNombreYLasVentanasPorDiaYLuegoInicio() {
        exigir(200, guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("Zumba", 30, true, 1, List.of())));
        Resp r = exigir(200, guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("masaje", 30, true, 1, List.of(
                ventana(UUID.randomUUID(), 5, "08:00", "09:00"),
                ventana(UUID.randomUUID(), 1, "14:00", "15:00"),
                ventana(UUID.randomUUID(), 1, "09:00", "10:00"),
                ventana(UUID.randomUUID(), 3, "10:00", "11:00")))));
        assertEquals(List.of(1L, 1L, 3L, 5L), r.en("ventanas[*].dia"));
        assertEquals(List.of("09:00", "14:00", "10:00", "08:00"), r.en("ventanas[*].inicio"));
        exigir(200, guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("Alineación", 30, true, 1, List.of())));

        Resp lista = obtener(SERVICIOS, admin);
        assertEquals(List.of("Alineación", "masaje", "Zumba"), lista.en("$[*].nombre"));
        assertEquals(List.of(1L, 1L, 3L, 5L), lista.en("$[1].ventanas[*].dia"));
        assertEquals(List.of("09:00", "14:00", "10:00", "08:00"), lista.en("$[1].ventanas[*].inicio"));
    }

    @Test
    void sinServiciosElListadoEsVacio() {
        Resp r = exigir(200, obtener(SERVICIOS, admin));
        assertEquals(0, r.lista("$").size());
    }

    // ---------- Permisos ----------

    @Test
    void verPanelPuedeLeerPeroNoEscribir() {
        UUID id = UUID.randomUUID();
        exigir(200, guardar(SERVICIOS + "/" + id, admin, masaje()));
        UUID lector = crearUsuario("lector", crearRol("Lector", "ver_panel"));

        assertEquals(1, exigir(200, obtener(SERVICIOS, lector)).lista("$").size());

        exigir(403, "SIN_PERMISO", guardar(SERVICIOS + "/" + id, lector, cuerpo("Cambiado", 45, true, 1, List.of())));
        exigir(403, guardar(SERVICIOS + "/" + UUID.randomUUID(), lector, masaje()));
        assertEquals(1, contar("servicio"));
        assertEquals("Masaje", consultarTexto("SELECT nombre FROM servicio"));
    }

    @Test
    void verCitasYGestionarServiciosTambienPuedenLeer() {
        UUID verCitas = crearUsuario("citas", crearRol("Citas", "ver_citas"));
        UUID soloGestionar = crearUsuario("solo", crearRol("Solo", "gestionar_servicios"));

        exigir(200, obtener(SERVICIOS, verCitas));
        exigir(200, obtener(SERVICIOS, soloGestionar));
        exigir(403, guardar(SERVICIOS + "/" + UUID.randomUUID(), verCitas, masaje()));
    }

    @Test
    void sinPermisosDeServiciosEsUn403EnLecturaYEscritura() {
        UUID otro = crearUsuario("otro",
                crearRol("Otro", "gestionar_clientes", "gestionar_usuarios", "gestionar_roles"));

        exigir(403, "SIN_PERMISO", obtener(SERVICIOS, otro));
        exigir(403, guardar(SERVICIOS + "/" + UUID.randomUUID(), otro, masaje()));
        assertEquals(0, contar("servicio"));
    }

    @Test
    void sinTokenEsUn401() {
        exigir(401, "NO_AUTENTICADO", get(SERVICIOS).enviar());
        exigir(401, "NO_AUTENTICADO", put(SERVICIOS + "/" + UUID.randomUUID()).cuerpo(masaje()).enviar());
        assertEquals(0, contar("servicio"));
    }

    // ---------- Base leída por la API ----------

    @Test
    void lasVentanasLeidasDeLaBaseSeVenEnLaApi() {
        UUID servicio = UUID.randomUUID();
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'S', 30, 1, 100)", servicio);
        ejecutar("INSERT INTO ventana_disponibilidad (id, servicio_id, dia, hora_inicio, hora_fin) "
                + "VALUES (?, ?, 6, '10:30', '11:30')", UUID.randomUUID(), servicio);

        Resp r = obtener(SERVICIOS, admin);
        assertEquals("10:30", r.en("$[0].ventanas[0].inicio"));
        assertEquals("11:30", r.en("$[0].ventanas[0].fin"));
        igualNumero("6", r.numero("$[0].ventanas[0].dia"));
        assertFalse(r.texto().contains("null"), "el JSON no lleva nulos");
    }
}
