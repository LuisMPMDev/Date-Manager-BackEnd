package com.datemanager.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.datemanager.PruebaAgenda;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.VentanaDisponibilidad;
import org.junit.jupiter.api.Test;

/** Casos de uso de servicios (portados de las pruebas del controlador de la aplicación original). */
class ServiciosServicioTest extends PruebaAgenda {

    private static VentanaDisponibilidad ventana(UUID id, int dia, String inicio, String fin) {
        return new VentanaDisponibilidad(id, dia, LocalTime.parse(inicio), LocalTime.parse(fin));
    }

    private static Servicio servicio(UUID id, String nombre, int duracion, boolean activo, String valor,
            VentanaDisponibilidad... ventanas) {
        return new Servicio(id, nombre, duracion, activo, new BigDecimal(valor), List.of(ventanas));
    }

    private static Servicio masaje(UUID id, VentanaDisponibilidad... ventanas) {
        return servicio(id, "Masaje", 45, true, "50000", ventanas);
    }

    private static List<String> ventanasEnBase(UUID servicioId) {
        return columna("SELECT CONCAT(dia, ' ', CONVERT(NVARCHAR(5), hora_inicio, 108), '-', "
                + "CONVERT(NVARCHAR(5), hora_fin, 108)) FROM ventana_disponibilidad WHERE servicio_id = ? "
                + "ORDER BY dia, hora_inicio", servicioId);
    }

    private ErrorNegocio validacion(Servicio s) {
        return exigirError(CodigoError.VALIDACION, () -> serviciosServicio.guardar(s));
    }

    // ---------- Crear y leer ----------

    @Test
    void creaUnServicioConVentanasYLoLee() {
        UUID id = nuevoId();
        UUID v1 = nuevoId();
        UUID v2 = nuevoId();

        Servicio guardado = serviciosServicio.guardar(
                masaje(id, ventana(v1, 1, "09:00", "12:00"), ventana(v2, 3, "14:00", "17:30")));

        assertEquals(id, guardado.id());
        assertEquals("Masaje", guardado.nombre());
        assertEquals(45, guardado.duracionMin());
        assertTrue(guardado.activo());
        assertEquals(new BigDecimal("50000.00"), guardado.valor());
        assertEquals(2, guardado.ventanas().size());
        assertEquals(ventana(v1, 1, "09:00", "12:00"), guardado.ventanas().get(0));
        assertEquals(ventana(v2, 3, "14:00", "17:30"), guardado.ventanas().get(1));

        List<Servicio> listado = serviciosServicio.listar();
        assertEquals(1, listado.size());
        assertEquals(id, listado.get(0).id());
        assertEquals(guardado, listado.get(0));
        assertEquals(List.of("1 09:00-12:00", "3 14:00-17:30"), ventanasEnBase(id));
    }

    @Test
    void unIdDesconocidoCreaElServicioConEseId() {
        UUID id = nuevoId();

        serviciosServicio.guardar(masaje(id));

        assertEquals(1, contar("servicio"));
        assertEquals(id.toString(), consultarTexto("SELECT CONVERT(NVARCHAR(36), id) FROM servicio").toLowerCase());
    }

    @Test
    void elValorSeGuardaConDosDecimales() {
        UUID id = nuevoId();

        Servicio guardado = serviciosServicio.guardar(servicio(id, "Corte", 30, true, "12500.5"));

        assertEquals(new BigDecimal("12500.50"), guardado.valor());
        assertEquals(new BigDecimal("12500.50"), serviciosServicio.listar().get(0).valor());
        assertEquals(1250050, consultarLong("SELECT CAST(valor * 100 AS BIGINT) FROM servicio WHERE id = ?", id));
    }

    @Test
    void elNombreSeGuardaSinEspaciosDeLosExtremos() {
        Servicio guardado = serviciosServicio.guardar(servicio(nuevoId(), "  Masaje  ", 45, true, "1"));

        assertEquals("Masaje", guardado.nombre());
    }

    @Test
    void unaVentanaConSuIdSeGuardaConEseId() {
        // Ese caso es del DTO: aquí el id ya llega; se comprueba que se guarda tal cual.
        UUID v = nuevoId();
        serviciosServicio.guardar(masaje(nuevoId(), ventana(v, 2, "08:00", "10:00")));

        assertEquals(1, contar("ventana_disponibilidad"));
        assertEquals(v.toString(), consultarTexto("SELECT CONVERT(NVARCHAR(36), id) FROM ventana_disponibilidad")
                .toLowerCase());
    }

    // ---------- Editar reemplaza ventanas ----------

    @Test
    void editarReemplazaLasVentanasPorLasRecibidas() {
        UUID id = nuevoId();
        UUID conservada = nuevoId();
        serviciosServicio.guardar(masaje(id, ventana(conservada, 1, "09:00", "12:00"),
                ventana(nuevoId(), 2, "09:00", "12:00")));
        UUID nueva = nuevoId();

        Servicio editado = serviciosServicio.guardar(servicio(id, "Masaje relajante", 60, true, "70000",
                ventana(conservada, 1, "10:00", "13:00"), ventana(nueva, 5, "15:00", "18:00")));

        assertEquals("Masaje relajante", editado.nombre());
        assertEquals(60, editado.duracionMin());
        assertEquals(2, editado.ventanas().size());
        assertEquals(conservada, editado.ventanas().get(0).id());
        assertEquals(LocalTime.of(10, 0), editado.ventanas().get(0).inicio());
        assertEquals(nueva, editado.ventanas().get(1).id());
        assertEquals(1, contar("servicio"));
        assertEquals(List.of("1 10:00-13:00", "5 15:00-18:00"), ventanasEnBase(id));
        assertEquals(2, contar("ventana_disponibilidad"));
    }

    @Test
    void reenviarLasMismasVentanasConLosMismosIdsNoChocaConLaLlavePrimaria() {
        UUID id = nuevoId();
        Servicio s = masaje(id, ventana(nuevoId(), 1, "09:00", "12:00"), ventana(nuevoId(), 1, "14:00", "16:00"));

        serviciosServicio.guardar(s);
        serviciosServicio.guardar(s);
        serviciosServicio.guardar(s);

        assertEquals(2, contar("ventana_disponibilidad"));
        assertEquals(List.of("1 09:00-12:00", "1 14:00-16:00"), ventanasEnBase(id));
    }

    // El disparador tr_ventana_sin_cruce rechaza cualquier paso que deje dos ventanas cruzadas, aunque sea un
    // instante: los guardados cuyo conjunto final es válido no deben pasar por un cruce intermedio.

    @Test
    void reemplazarUnaVentanaPorOtraDeIdNuevoQueSeCruzaConLaAnteriorFunciona() {
        UUID id = nuevoId();
        serviciosServicio.guardar(masaje(id, ventana(nuevoId(), 1, "09:00", "10:00")));
        UUID nueva = nuevoId();

        Servicio editado = serviciosServicio.guardar(masaje(id, ventana(nueva, 1, "09:30", "10:30")));

        assertEquals(List.of(ventana(nueva, 1, "09:30", "10:30")), editado.ventanas());
        assertEquals(List.of("1 09:30-10:30"), ventanasEnBase(id));
    }

    @Test
    void desplazarDosVentanasContiguasConSusMismosIdsFunciona() {
        UUID id = nuevoId();
        UUID v1 = nuevoId();
        UUID v2 = nuevoId();
        serviciosServicio.guardar(masaje(id, ventana(v1, 1, "09:00", "10:00"), ventana(v2, 1, "10:00", "11:00")));

        Servicio editado = serviciosServicio.guardar(
                masaje(id, ventana(v1, 1, "10:00", "11:00"), ventana(v2, 1, "11:00", "12:00")));

        assertEquals(List.of(ventana(v1, 1, "10:00", "11:00"), ventana(v2, 1, "11:00", "12:00")), editado.ventanas());
        assertEquals(List.of("1 10:00-11:00", "1 11:00-12:00"), ventanasEnBase(id));
    }

    @Test
    void intercambiarLosHorariosDeDosVentanasConSusMismosIdsFunciona() {
        UUID id = nuevoId();
        UUID v1 = nuevoId();
        UUID v2 = nuevoId();
        serviciosServicio.guardar(masaje(id, ventana(v1, 1, "09:00", "10:00"), ventana(v2, 1, "10:00", "12:00")));

        Servicio editado = serviciosServicio.guardar(
                masaje(id, ventana(v1, 1, "09:00", "11:00"), ventana(v2, 1, "11:00", "12:00")));

        assertEquals(v1, editado.ventanas().get(0).id());
        assertEquals(v2, editado.ventanas().get(1).id());
        assertEquals(List.of("1 09:00-11:00", "1 11:00-12:00"), ventanasEnBase(id));
    }

    @Test
    void unConjuntoFinalConCruceRealSigueSiendoUnaValidacionConElMensajeDeLaAplicacionYNoCambiaNada() {
        UUID id = nuevoId();
        UUID v1 = nuevoId();
        serviciosServicio.guardar(masaje(id, ventana(v1, 1, "09:00", "10:00")));

        ErrorNegocio error = validacion(
                masaje(id, ventana(v1, 1, "09:00", "10:00"), ventana(nuevoId(), 1, "09:30", "10:30")));

        assertEquals("Lunes: las ventanas 09:00–10:00 y 09:30–10:30 se cruzan. "
                + "Ajusta las horas para que una termine donde empieza la otra o antes.", error.getMessage());
        assertEquals(List.of("1 09:00-10:00"), ventanasEnBase(id));
    }

    @Test
    void cambiarElDiaDeUnaVentanaConElMismoIdLaMueve() {
        UUID id = nuevoId();
        UUID v = nuevoId();
        serviciosServicio.guardar(masaje(id, ventana(v, 1, "09:00", "12:00")));

        Servicio editado = serviciosServicio.guardar(masaje(id, ventana(v, 4, "09:00", "12:00")));

        assertEquals(4, editado.ventanas().get(0).dia());
        assertEquals(List.of("4 09:00-12:00"), ventanasEnBase(id));
    }

    @Test
    void editarConUnaListaVaciaQuitaTodasLasVentanas() {
        UUID id = nuevoId();
        serviciosServicio.guardar(masaje(id, ventana(nuevoId(), 1, "09:00", "12:00")));

        Servicio editado = serviciosServicio.guardar(masaje(id));

        assertTrue(editado.ventanas().isEmpty());
        assertEquals(0, contar("ventana_disponibilidad"));
    }

    @Test
    void losMismosIdsDeVentanaEnDosServiciosNoChocanYCadaUnaPerteneceAlSuyo() {
        UUID a = nuevoId();
        UUID b = nuevoId();
        UUID compartida = nuevoId();

        serviciosServicio.guardar(servicio(a, "A", 30, true, "1", ventana(compartida, 1, "09:00", "10:00")));
        serviciosServicio.guardar(servicio(b, "B", 30, true, "1", ventana(compartida, 2, "11:00", "12:00")));

        assertEquals(2, contar("ventana_disponibilidad"));
        assertEquals(List.of("1 09:00-10:00"), ventanasEnBase(a));
        assertEquals(List.of("2 11:00-12:00"), ventanasEnBase(b));

        // Editar B no toca la ventana de A aunque compartan id.
        serviciosServicio.guardar(servicio(b, "B", 30, true, "1"));
        assertEquals(List.of("1 09:00-10:00"), ventanasEnBase(a));
        assertTrue(ventanasEnBase(b).isEmpty());
        List<Servicio> todos = serviciosServicio.listar();
        assertEquals("A", todos.get(0).nombre());
        assertEquals(1, todos.get(0).ventanas().size());
        assertEquals("B", todos.get(1).nombre());
        assertEquals(0, todos.get(1).ventanas().size());
    }

    @Test
    void dosVentanasConElMismoIdEnLaMismaPeticionSonUnaValidacion() {
        UUID repetida = nuevoId();

        validacion(masaje(nuevoId(), ventana(repetida, 1, "09:00", "10:00"), ventana(repetida, 2, "09:00", "10:00")));

        assertEquals(0, contar("servicio"));
    }

    // ---------- Validación ----------

    @Test
    void unNombreEnBlancoEsUnaValidacionConElMensajeDeValidaciones() {
        assertEquals("Escribe el nombre del servicio.",
                validacion(servicio(nuevoId(), "   ", 45, true, "100")).getMessage());
        assertEquals(0, contar("servicio"));
    }

    @Test
    void unaDuracionDeMasDeVeinticuatroHorasEsUnaValidacionYLaDeExactamente1440Vale() {
        assertEquals("La duración no puede superar 24 horas (1440 minutos).",
                validacion(servicio(nuevoId(), "Masaje", 1441, true, "100")).getMessage());
        assertEquals(0, contar("servicio"));

        assertEquals(1440, serviciosServicio.guardar(servicio(nuevoId(), "Masaje", 1440, true, "100")).duracionMin());
        assertEquals(1, contar("servicio"));
    }

    @Test
    void unaDuracionCeroONegativaEsUnaValidacion() {
        for (int duracion : new int[] {0, -30}) {
            assertEquals("La duración debe ser un número entero de minutos mayor que cero.",
                    validacion(servicio(nuevoId(), "Masaje", duracion, true, "100")).getMessage());
        }
    }

    @Test
    void unValorNegativoEsUnaValidacion() {
        assertEquals("El valor no puede ser negativo.",
                validacion(servicio(nuevoId(), "Masaje", 45, true, "-1")).getMessage());
    }

    @Test
    void unValorSinNumeroEsUnaValidacion() {
        validacion(new Servicio(nuevoId(), "Masaje", 45, true, null, List.of()));
    }

    @Test
    void unValorConMasDeDosDecimalesOQueNoCabeEnLaColumnaEsUnaValidacion() {
        assertEquals("El valor admite como máximo dos decimales.",
                validacion(servicio(nuevoId(), "Masaje", 45, true, "10.555")).getMessage());
        assertEquals("El valor es demasiado grande.",
                validacion(servicio(nuevoId(), "Masaje", 45, true, "12345678901.00")).getMessage());
        assertEquals(0, contar("servicio"));
        // Ceros a la derecha de más no son decimales de verdad, y 10 enteros caben.
        serviciosServicio.guardar(servicio(nuevoId(), "Masaje", 45, true, "10.500"));
        assertEquals(new BigDecimal("9999999999.99"),
                serviciosServicio.guardar(servicio(nuevoId(), "Otro", 45, true, "9999999999.99")).valor());
    }

    @Test
    void unNombreMasLargoQueLaColumnaLoRechazaLaBase() {
        // El límite de 150 lo comprueba la capa HTTP; si llegara aquí, la base lo rechaza y no se guarda nada.
        exigirRestriccion(() -> serviciosServicio.guardar(servicio(nuevoId(), "x".repeat(151), 45, true, "100")));
        assertEquals(0, contar("servicio"));
        serviciosServicio.guardar(servicio(nuevoId(), "x".repeat(150), 45, true, "100"));
    }

    @Test
    void lasVentanasCruzadasSeRechazanConElMensajeEspanol() {
        ErrorNegocio error = validacion(masaje(nuevoId(), ventana(nuevoId(), 1, "09:00", "12:00"),
                ventana(nuevoId(), 1, "11:00", "14:00")));

        assertEquals("Lunes: las ventanas 09:00–12:00 y 11:00–14:00 se cruzan. "
                + "Ajusta las horas para que una termine donde empieza la otra o antes.", error.getMessage());
        assertEquals(0, contar("servicio"));
        assertEquals(0, contar("ventana_disponibilidad"));
    }

    @Test
    void ventanasQueSoloSeTocanSonValidas() {
        serviciosServicio.guardar(masaje(nuevoId(), ventana(nuevoId(), 1, "09:00", "12:00"),
                ventana(nuevoId(), 1, "12:00", "14:00")));

        assertEquals(2, contar("ventana_disponibilidad"));
    }

    @Test
    void unaVentanaConFinNoPosteriorAlInicioEsUnaValidacion() {
        assertEquals("Martes: la hora de fin debe ser posterior a la de inicio.",
                validacion(masaje(nuevoId(), ventana(nuevoId(), 2, "12:00", "12:00"))).getMessage());
        validacion(masaje(nuevoId(), ventana(nuevoId(), 2, "13:00", "12:00")));
    }

    @Test
    void unaVentanaSinHoraEsUnaValidacion() {
        validacion(masaje(nuevoId(), new VentanaDisponibilidad(nuevoId(), 1, LocalTime.of(9, 0), null)));
        assertEquals(0, contar("servicio"));
    }

    @Test
    void unDiaFueraDeUnoASieteEsUnaValidacionAntesDeLlegarALaBase() {
        for (int dia : new int[] {0, 8, -1, 99}) {
            validacion(masaje(nuevoId(), ventana(nuevoId(), dia, "09:00", "10:00")));
        }
        assertEquals(0, contar("servicio"));
        assertEquals(0, contar("ventana_disponibilidad"));
    }

    @Test
    void losDiasUnoYSieteSonValidos() {
        serviciosServicio.guardar(masaje(nuevoId(), ventana(nuevoId(), 1, "09:00", "10:00"),
                ventana(nuevoId(), 7, "09:00", "10:00")));

        assertEquals(2, contar("ventana_disponibilidad"));
    }

    @Test
    void unaEdicionInvalidaNoModificaNada() {
        UUID id = nuevoId();
        serviciosServicio.guardar(masaje(id, ventana(nuevoId(), 1, "09:00", "12:00")));

        validacion(servicio(id, "Otro", 45, true, "1", ventana(nuevoId(), 1, "09:00", "12:00"),
                ventana(nuevoId(), 1, "10:00", "11:00")));

        Servicio sigue = serviciosServicio.listar().get(0);
        assertEquals("Masaje", sigue.nombre());
        assertEquals(1, sigue.ventanas().size());
    }

    @Test
    void unaFallaPosteriorEnLaMismaTransaccionDeshaceElGuardado() {
        UUID id = nuevoId();
        serviciosServicio.guardar(masaje(id, ventana(nuevoId(), 1, "09:00", "12:00")));

        assertThrows(IllegalStateException.class, () -> tx.escribir(() -> {
            serviciosServicio.guardar(servicio(id, "Cambiado", 45, true, "1"));
            throw new IllegalStateException("falla después de guardar");
        }));

        Servicio sigue = serviciosServicio.buscar(id);
        assertEquals("Masaje", sigue.nombre());
        assertEquals(1, sigue.ventanas().size());
    }

    // ---------- Desactivar ----------

    @Test
    void desactivarConservaElServicioYSusVentanasYSigueEnElListado() {
        UUID id = nuevoId();
        UUID v = nuevoId();
        serviciosServicio.guardar(masaje(id, ventana(v, 1, "09:00", "12:00")));

        Servicio desactivado = serviciosServicio.guardar(
                servicio(id, "Masaje", 45, false, "50000", ventana(v, 1, "09:00", "12:00")));

        assertFalse(desactivado.activo());
        List<Servicio> listado = serviciosServicio.listar();
        assertEquals(1, listado.size());
        assertFalse(listado.get(0).activo());
        assertEquals("Masaje", listado.get(0).nombre());
        assertEquals(45, listado.get(0).duracionMin());
        assertEquals(v, listado.get(0).ventanas().get(0).id());
        assertEquals(List.of("1 09:00-12:00"), ventanasEnBase(id));

        // Y se puede reactivar.
        assertTrue(serviciosServicio.guardar(
                servicio(id, "Masaje", 45, true, "50000", ventana(v, 1, "09:00", "12:00"))).activo());
    }

    // ---------- Orden ----------

    @Test
    void elListadoVaOrdenadoPorNombreYLasVentanasPorDiaYLuegoInicio() {
        serviciosServicio.guardar(servicio(nuevoId(), "Zumba", 30, true, "1"));
        Servicio masajeGuardado = serviciosServicio.guardar(servicio(nuevoId(), "masaje", 30, true, "1",
                ventana(nuevoId(), 5, "08:00", "09:00"), ventana(nuevoId(), 1, "14:00", "15:00"),
                ventana(nuevoId(), 1, "09:00", "10:00"), ventana(nuevoId(), 3, "10:00", "11:00")));
        serviciosServicio.guardar(servicio(nuevoId(), "Alineación", 30, true, "1"));

        assertEquals(List.of(1, 1, 3, 5), masajeGuardado.ventanas().stream().map(VentanaDisponibilidad::dia).toList());
        assertEquals(List.of("09:00", "14:00", "10:00", "08:00"),
                masajeGuardado.ventanas().stream().map(v -> v.inicio().toString()).toList());
        List<Servicio> listado = serviciosServicio.listar();
        assertEquals(List.of("Alineación", "masaje", "Zumba"), listado.stream().map(Servicio::nombre).toList());
        assertEquals(masajeGuardado.ventanas(), listado.get(1).ventanas());
    }

    @Test
    void sinServiciosElListadoEsVacio() {
        assertTrue(serviciosServicio.listar().isEmpty());
    }

    // ---------- buscar y bloquear ----------

    @Test
    void buscarDevuelveElServicioConSusVentanasOrdenadas() {
        UUID id = nuevoId();
        UUID v1 = nuevoId();
        UUID v2 = nuevoId();
        serviciosServicio.guardar(servicio(id, "Masaje", 45, false, "50000.5", ventana(v2, 3, "09:00", "10:00"),
                ventana(v1, 1, "09:00", "10:00")));

        Servicio servicio = serviciosServicio.buscar(id);

        assertEquals(id, servicio.id());
        assertEquals("Masaje", servicio.nombre());
        assertEquals(45, servicio.duracionMin());
        assertFalse(servicio.activo());
        assertEquals(0, servicio.valor().compareTo(new BigDecimal("50000.50")));
        assertEquals(List.of(v1, v2), servicio.ventanas().stream().map(VentanaDisponibilidad::id).toList());
        assertEquals("09:00", servicio.ventanas().get(0).inicio().toString());
    }

    @Test
    void buscarUnIdInexistenteLanzaNoEncontrado() {
        ErrorNegocio error = exigirError(CodigoError.NO_ENCONTRADO, () -> serviciosServicio.buscar(nuevoId()));

        assertEquals("El servicio no existe.", error.getMessage());
    }

    @Test
    void bloquearExigeUnaTransaccionAbierta() {
        UUID id = nuevoId();
        serviciosServicio.guardar(masaje(id));

        assertThrows(IllegalStateException.class, () -> serviciosServicio.bloquear(id));
        assertEquals(id, tx.escribir(() -> serviciosServicio.bloquear(id)).id());
    }

    @Test
    void bloquearUnIdInexistenteLanzaNoEncontrado() {
        exigirError(CodigoError.NO_ENCONTRADO, () -> tx.escribir(() -> serviciosServicio.bloquear(nuevoId())));
    }

    // ---------- Restricciones de la base ----------

    @Test
    void laBaseRechazaUnDiaFueraDeUnoASiete() {
        UUID servicio = insertarServicio("30", "0");

        exigirRestriccion(() -> insertarVentana(servicio, 8, "09:00", "10:00"));
        exigirRestriccion(() -> insertarVentana(servicio, 0, "09:00", "10:00"));
        insertarVentana(servicio, 1, "09:00", "10:00");
        insertarVentana(servicio, 7, "09:00", "10:00");
    }

    @Test
    void laBaseRechazaUnaVentanaConFinNoPosteriorAlInicio() {
        UUID servicio = insertarServicio("30", "0");

        exigirRestriccion(() -> insertarVentana(servicio, 1, "10:00", "10:00"));
        exigirRestriccion(() -> insertarVentana(servicio, 1, "11:00", "10:00"));
    }

    @Test
    void laBaseRechazaDuracionNoPositivaYValorNegativo() {
        exigirRestriccion(() -> insertarServicio("0", "0"));
        exigirRestriccion(() -> insertarServicio("-5", "0"));
        exigirRestriccion(() -> insertarServicio("30", "-0.01"));
        insertarServicio("30", "0");
    }

    @Test
    void laBaseRechazaUnaVentanaDeUnServicioInexistente() {
        exigirRestriccion(() -> insertarVentana(nuevoId(), 1, "09:00", "10:00"));
    }

    @Test
    void borrarUnServicioEnLaBaseBorraSusVentanasEnCascada() {
        UUID servicio = insertarServicio("30", "0");
        insertarVentana(servicio, 1, "09:00", "10:00");
        insertarVentana(servicio, 2, "09:00", "10:00");

        ejecutar("DELETE FROM servicio WHERE id = ?", servicio);

        assertEquals(0, contar("ventana_disponibilidad"));
    }

    @Test
    void lasVentanasLeidasDeLaBaseSeVenEnElServicio() {
        UUID servicio = insertarServicio("30", "100");
        insertarVentana(servicio, 6, "10:30", "11:30");

        VentanaDisponibilidad v = serviciosServicio.listar().get(0).ventanas().get(0);

        assertEquals(LocalTime.of(10, 30), v.inicio());
        assertEquals(LocalTime.of(11, 30), v.fin());
        assertEquals(6, v.dia());
    }
}
