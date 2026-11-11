package com.datemanager.dominio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EnlacesTest {

    private static final Instant CREADO = Instant.parse("2026-11-09T13:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-11-10T13:00:00Z");

    private static Enlace enlace(Integer maxUsos, int usos, Instant desactivadoEn) {
        return new Enlace(UUID.randomUUID(), Enlaces.nuevoCodigo(), UUID.randomUUID(), CREADO, VENCE, maxUsos, usos,
                desactivadoEn);
    }

    // ---------- Estado ----------

    @Test
    void antesDeVencerSinLimiteEsActivo() {
        assertEquals(EstadoEnlace.Activo, Enlaces.estado(enlace(null, 500, null), VENCE.minusMillis(1)));
    }

    @Test
    void justoAlVencerEsVencido() {
        assertEquals(EstadoEnlace.Vencido, Enlaces.estado(enlace(null, 0, null), VENCE));
        assertEquals(EstadoEnlace.Vencido, Enlaces.estado(enlace(null, 0, null), VENCE.plusSeconds(1)));
    }

    @Test
    void conTodosLosUsosGastadosEsAgotado() {
        assertEquals(EstadoEnlace.Activo, Enlaces.estado(enlace(2, 1, null), CREADO));
        assertEquals(EstadoEnlace.Agotado, Enlaces.estado(enlace(2, 2, null), CREADO));
        assertEquals(EstadoEnlace.Agotado, Enlaces.estado(enlace(1, 1, null), CREADO));
    }

    @Test
    void vencidoGanaAAgotado() {
        assertEquals(EstadoEnlace.Vencido, Enlaces.estado(enlace(2, 2, null), VENCE));
    }

    @Test
    void desactivadoGanaATodo() {
        Instant desactivado = CREADO.plusSeconds(60);
        assertEquals(EstadoEnlace.Desactivado, Enlaces.estado(enlace(null, 0, desactivado), CREADO.plusSeconds(120)));
        assertEquals(EstadoEnlace.Desactivado, Enlaces.estado(enlace(1, 1, desactivado), VENCE.plusSeconds(1)));
        assertEquals(EstadoEnlace.Desactivado, Enlaces.estado(enlace(1, 1, desactivado), CREADO));
    }

    // ---------- Código ----------

    @Test
    void elCodigoTieneAlMenos128BitsEnBase64UrlSinRelleno() {
        String codigo = Enlaces.nuevoCodigo();

        assertTrue(codigo.length() >= 22, "longitud: " + codigo.length());
        assertTrue(codigo.matches("[A-Za-z0-9_-]+"), codigo);
        assertTrue(Base64.getUrlDecoder().decode(codigo).length >= 16);
        assertTrue(Enlaces.esCodigoBienFormado(codigo));
    }

    @Test
    void losCodigosNoSeRepiten() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(vistos.add(Enlaces.nuevoCodigo()));
        }
    }

    @Test
    void unCodigoMalFormadoSeReconoceSinIrALaBase() {
        assertFalse(Enlaces.esCodigoBienFormado(null));
        assertFalse(Enlaces.esCodigoBienFormado(""));
        assertFalse(Enlaces.esCodigoBienFormado("corto"));
        assertFalse(Enlaces.esCodigoBienFormado("AAAAAAAAAAAAAAAAAAAAAA ")); // espacio final
        assertFalse(Enlaces.esCodigoBienFormado("AAAAAAAAAAAAAAAAAAAA+/")); // Base64 normal, no URL
        assertFalse(Enlaces.esCodigoBienFormado("A".repeat(44)));
        assertTrue(Enlaces.esCodigoBienFormado("AAAAAAAAAAAAAAAAAAAA-_"));
    }

    // ---------- Validar al crear ----------

    private static void esValidacionDe(Runnable accion, String campo) {
        ErrorNegocio e = assertThrows(ErrorNegocio.class, accion::run);
        assertEquals(CodigoError.VALIDACION, e.codigo());
        assertTrue(e.campos().containsKey(campo), "campos: " + e.campos());
    }

    @Test
    void laDuracionVaDe60A43200MinutosYPorDefectoEs1440() {
        assertEquals(1440, Enlaces.duracionMinutos(null));
        assertEquals(60, Enlaces.duracionMinutos(60));
        assertEquals(43200, Enlaces.duracionMinutos(43200));
        esValidacionDe(() -> Enlaces.duracionMinutos(59), "duracionMinutos");
        esValidacionDe(() -> Enlaces.duracionMinutos(43201), "duracionMinutos");
        esValidacionDe(() -> Enlaces.duracionMinutos(0), "duracionMinutos");
        esValidacionDe(() -> Enlaces.duracionMinutos(-60), "duracionMinutos");
    }

    @Test
    void elMaximoDeUsosEsNuloOAlMenosUno() {
        assertDoesNotThrow(() -> Enlaces.validarMaxUsos(null));
        assertDoesNotThrow(() -> Enlaces.validarMaxUsos(1));
        esValidacionDe(() -> Enlaces.validarMaxUsos(0), "maxUsos");
        esValidacionDe(() -> Enlaces.validarMaxUsos(-1), "maxUsos");
    }

    // ---------- Errores por estado ----------

    @Test
    void cadaEstadoNoActivoTieneSuCodigoDeError() {
        assertEquals(CodigoError.ENLACE_VENCIDO, Enlaces.errorDe(EstadoEnlace.Vencido).codigo());
        assertEquals(CodigoError.ENLACE_AGOTADO, Enlaces.errorDe(EstadoEnlace.Agotado).codigo());
        assertEquals(CodigoError.ENLACE_DESACTIVADO, Enlaces.errorDe(EstadoEnlace.Desactivado).codigo());
        assertEquals(CodigoError.ENLACE_NO_ENCONTRADO, Enlaces.noEncontrado().codigo());
    }
}
