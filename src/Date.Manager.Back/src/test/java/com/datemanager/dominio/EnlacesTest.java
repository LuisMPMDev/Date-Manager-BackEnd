package com.datemanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(Enlaces.estado(enlace(null, 500, null), VENCE.minusMillis(1))).isEqualTo(EstadoEnlace.Activo);
    }

    @Test
    void justoAlVencerEsVencido() {
        assertThat(Enlaces.estado(enlace(null, 0, null), VENCE)).isEqualTo(EstadoEnlace.Vencido);
        assertThat(Enlaces.estado(enlace(null, 0, null), VENCE.plusSeconds(1))).isEqualTo(EstadoEnlace.Vencido);
    }

    @Test
    void conTodosLosUsosGastadosEsAgotado() {
        assertThat(Enlaces.estado(enlace(2, 1, null), CREADO)).isEqualTo(EstadoEnlace.Activo);
        assertThat(Enlaces.estado(enlace(2, 2, null), CREADO)).isEqualTo(EstadoEnlace.Agotado);
        assertThat(Enlaces.estado(enlace(1, 1, null), CREADO)).isEqualTo(EstadoEnlace.Agotado);
    }

    @Test
    void vencidoGanaAAgotado() {
        assertThat(Enlaces.estado(enlace(2, 2, null), VENCE)).isEqualTo(EstadoEnlace.Vencido);
    }

    @Test
    void desactivadoGanaATodo() {
        Instant desactivado = CREADO.plusSeconds(60);
        assertThat(Enlaces.estado(enlace(null, 0, desactivado), CREADO.plusSeconds(120)))
                .isEqualTo(EstadoEnlace.Desactivado);
        assertThat(Enlaces.estado(enlace(1, 1, desactivado), VENCE.plusSeconds(1)))
                .isEqualTo(EstadoEnlace.Desactivado);
        assertThat(Enlaces.estado(enlace(1, 1, desactivado), CREADO)).isEqualTo(EstadoEnlace.Desactivado);
    }

    // ---------- Código ----------

    @Test
    void elCodigoTieneAlMenos128BitsEnBase64UrlSinRelleno() {
        String codigo = Enlaces.nuevoCodigo();

        assertThat(codigo).hasSizeGreaterThanOrEqualTo(22).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(codigo)).hasSizeGreaterThanOrEqualTo(16);
        assertThat(Enlaces.esCodigoBienFormado(codigo)).isTrue();
    }

    @Test
    void losCodigosNoSeRepiten() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(vistos.add(Enlaces.nuevoCodigo())).isTrue();
        }
    }

    @Test
    void unCodigoMalFormadoSeReconoceSinIrALaBase() {
        assertThat(Enlaces.esCodigoBienFormado(null)).isFalse();
        assertThat(Enlaces.esCodigoBienFormado("")).isFalse();
        assertThat(Enlaces.esCodigoBienFormado("corto")).isFalse();
        assertThat(Enlaces.esCodigoBienFormado("AAAAAAAAAAAAAAAAAAAAAA ")).isFalse(); // espacio final
        assertThat(Enlaces.esCodigoBienFormado("AAAAAAAAAAAAAAAAAAAA+/")).isFalse(); // Base64 normal, no URL
        assertThat(Enlaces.esCodigoBienFormado("A".repeat(44))).isFalse();
        assertThat(Enlaces.esCodigoBienFormado("AAAAAAAAAAAAAAAAAAAA-_")).isTrue();
    }

    // ---------- Validar al crear ----------

    private static void esValidacionDe(Runnable accion, String campo) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ErrorNegocio.class, e -> {
            assertThat(e.codigo()).isEqualTo(CodigoError.VALIDACION);
            assertThat(e.campos()).containsKey(campo);
        });
    }

    @Test
    void laDuracionVaDe60A43200MinutosYPorDefectoEs1440() {
        assertThat(Enlaces.duracionMinutos(null)).isEqualTo(1440);
        assertThat(Enlaces.duracionMinutos(60)).isEqualTo(60);
        assertThat(Enlaces.duracionMinutos(43200)).isEqualTo(43200);
        esValidacionDe(() -> Enlaces.duracionMinutos(59), "duracionMinutos");
        esValidacionDe(() -> Enlaces.duracionMinutos(43201), "duracionMinutos");
        esValidacionDe(() -> Enlaces.duracionMinutos(0), "duracionMinutos");
        esValidacionDe(() -> Enlaces.duracionMinutos(-60), "duracionMinutos");
    }

    @Test
    void elMaximoDeUsosEsNuloOAlMenosUno() {
        assertThatCode(() -> Enlaces.validarMaxUsos(null)).doesNotThrowAnyException();
        assertThatCode(() -> Enlaces.validarMaxUsos(1)).doesNotThrowAnyException();
        esValidacionDe(() -> Enlaces.validarMaxUsos(0), "maxUsos");
        esValidacionDe(() -> Enlaces.validarMaxUsos(-1), "maxUsos");
    }

    // ---------- Errores por estado ----------

    @Test
    void cadaEstadoNoActivoTieneSuCodigoDeError() {
        assertThat(Enlaces.errorDe(EstadoEnlace.Vencido).codigo()).isEqualTo(CodigoError.ENLACE_VENCIDO);
        assertThat(Enlaces.errorDe(EstadoEnlace.Agotado).codigo()).isEqualTo(CodigoError.ENLACE_AGOTADO);
        assertThat(Enlaces.errorDe(EstadoEnlace.Desactivado).codigo()).isEqualTo(CodigoError.ENLACE_DESACTIVADO);
        assertThat(Enlaces.noEncontrado().codigo()).isEqualTo(CodigoError.ENLACE_NO_ENCONTRADO);
    }
}
