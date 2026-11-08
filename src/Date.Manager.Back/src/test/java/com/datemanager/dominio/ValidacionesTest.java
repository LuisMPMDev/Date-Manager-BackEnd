package com.datemanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidacionesTest {

    private static VentanaDisponibilidad ventana(int dia, String inicio, String fin) {
        return new VentanaDisponibilidad(UUID.randomUUID(), dia, LocalTime.parse(inicio), LocalTime.parse(fin));
    }

    private static Servicio servicio(String nombre, int duracionMin, String valor, List<VentanaDisponibilidad> ventanas) {
        return new Servicio(UUID.randomUUID(), nombre, duracionMin, true, new BigDecimal(valor), ventanas);
    }

    private static Servicio valido() {
        return servicio("Masaje", 45, "50000", List.of(ventana(1, "09:00", "12:00")));
    }

    private static Servicio conVentanas(VentanaDisponibilidad... ventanas) {
        return servicio("Masaje", 45, "50000", List.of(ventanas));
    }

    @Test
    void unServicioValidoNoTieneError() {
        assertThat(Validaciones.errorServicio(valido())).isEmpty();
    }

    @Test
    void unServicioSinVentanasEsValido() {
        assertThat(Validaciones.errorServicio(servicio("Masaje", 45, "50000", List.of()))).isEmpty();
    }

    @Test
    void valorCeroEsValido() {
        assertThat(Validaciones.errorServicio(servicio("Masaje", 45, "0", List.of()))).isEmpty();
    }

    @Test
    void rechazaNombreVacioEnBlancoONulo() {
        for (String nombre : new String[] {"", "   ", null}) {
            assertThat(Validaciones.errorServicio(servicio(nombre, 45, "50000", List.of())))
                    .contains("Escribe el nombre del servicio.");
        }
    }

    @Test
    void rechazaDuracionCeroONegativa() {
        for (int duracion : new int[] {0, -30}) {
            assertThat(Validaciones.errorServicio(servicio("Masaje", duracion, "50000", List.of())))
                    .contains("La duración debe ser un número entero de minutos mayor que cero.");
        }
    }

    @Test
    void rechazaValorNegativoONulo() {
        assertThat(Validaciones.errorServicio(servicio("Masaje", 45, "-1", List.of())))
                .contains("El valor no puede ser negativo.");
        assertThat(Validaciones.errorServicio(new Servicio(UUID.randomUUID(), "Masaje", 45, true, null, List.of())))
                .contains("El valor no puede ser negativo.");
    }

    @Test
    void rechazaVentanaConFinIgualOAnteriorAlInicio() {
        assertThat(Validaciones.errorServicio(conVentanas(ventana(2, "10:00", "10:00"))))
                .contains("Martes: la hora de fin debe ser posterior a la de inicio.");
        assertThat(Validaciones.errorServicio(conVentanas(ventana(2, "12:00", "09:00")))).isPresent();
    }

    @Test
    void rechazaVentanaSinHoras() {
        assertThat(Validaciones.errorServicio(conVentanas(
                new VentanaDisponibilidad(UUID.randomUUID(), 2, null, LocalTime.parse("10:00")))))
                .contains("Martes: escribe la hora de inicio y de fin de la ventana.");
    }

    @Test
    void elMensajeDeUnaVentanaInvalidaNombraSuDia() {
        assertThat(Validaciones.errorServicio(conVentanas(ventana(3, "10:00", "09:00"))).orElseThrow())
                .contains("Miércoles");
    }

    @Test
    void rechazaVentanasQueSeCruzanNombrandoDiaYHoras() {
        String mensaje = Validaciones.errorServicio(
                conVentanas(ventana(2, "09:00", "12:00"), ventana(2, "11:00", "14:00"))).orElseThrow();
        assertThat(mensaje).isEqualTo("Martes: las ventanas 09:00–12:00 y 11:00–14:00 se cruzan. "
                + "Ajusta las horas para que una termine donde empieza la otra o antes.");
    }

    @Test
    void rechazaUnaVentanaContenidaEnOtraAunqueEstenEnOtroOrden() {
        assertThat(Validaciones.errorServicio(conVentanas(ventana(3, "10:00", "11:00"), ventana(3, "08:00", "13:00"))))
                .isPresent();
    }

    @Test
    void rechazaVentanasIdenticasRepetidas() {
        assertThat(Validaciones.errorServicio(conVentanas(ventana(1, "09:00", "12:00"), ventana(1, "09:00", "12:00"))))
                .isPresent();
    }

    @Test
    void permiteVentanasQueSeTocanOEstanEnDiasDistintos() {
        assertThat(Validaciones.errorServicio(conVentanas(ventana(2, "09:00", "12:00"), ventana(2, "12:00", "15:00"))))
                .isEmpty();
        assertThat(Validaciones.errorServicio(conVentanas(ventana(2, "09:00", "12:00"), ventana(3, "10:00", "13:00"))))
                .isEmpty();
        assertThat(Validaciones.errorServicio(conVentanas(ventana(2, "15:00", "18:00"), ventana(2, "09:00", "13:00"))))
                .isEmpty();
    }

    @Test
    void noModificaLaListaDeVentanasDelServicio() {
        List<VentanaDisponibilidad> original =
                new ArrayList<>(List.of(ventana(2, "15:00", "18:00"), ventana(2, "09:00", "13:00")));
        Servicio s = servicio("Masaje", 45, "50000", original);
        Validaciones.errorServicio(s);
        assertThat(s.ventanas()).containsExactlyElementsOf(original);
    }

    @Test
    void aceptaCorreosNormalesConEspaciosAlrededor() {
        for (String c : new String[] {"nombre@correo.com", "a.b+c@sub.dominio.co", " ana@correo.com ", "a@b.co"}) {
            assertThat(Validaciones.esCorreoValido(c)).as(c).isTrue();
        }
    }

    @Test
    void rechazaLoQueNoTieneFormaDeCorreo() {
        for (String c : new String[] {"", "sin-arroba", "a@b", "a@b.", "a@.com", "a@b..c", "a b@c.co", "a@@c.com", "@c.com"}) {
            assertThat(Validaciones.esCorreoValido(c)).as(c).isFalse();
        }
        assertThat(Validaciones.esCorreoValido(null)).isFalse();
    }

    @Test
    void normalizaCorreoYUsuarioConTrimYMinusculas() {
        assertThat(Validaciones.normalizarUsuario(" Marta ")).isEqualTo("marta");
        assertThat(Validaciones.normalizarCorreo("  Ana@Correo.COM ")).isEqualTo("ana@correo.com");
        assertThat(Validaciones.normalizarUsuario("MARTA")).isEqualTo(Validaciones.normalizarUsuario("marta "));
    }

    @Test
    void normalizarNuloDevuelveVacio() {
        assertThat(Validaciones.normalizarUsuario(null)).isEmpty();
        assertThat(Validaciones.normalizarCorreo(null)).isEmpty();
    }

    @Test
    void normalizaConLocaleRootAunqueElLocaleSeaTurco() {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(Validaciones.normalizarUsuario("MARTIN")).isEqualTo("martin");
        } finally {
            Locale.setDefault(anterior);
        }
    }
}
