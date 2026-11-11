package com.datemanager.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
        assertTrue(Validaciones.errorServicio(valido()).isEmpty());
    }

    @Test
    void unServicioSinVentanasEsValido() {
        assertTrue(Validaciones.errorServicio(servicio("Masaje", 45, "50000", List.of())).isEmpty());
    }

    @Test
    void valorCeroEsValido() {
        assertTrue(Validaciones.errorServicio(servicio("Masaje", 45, "0", List.of())).isEmpty());
    }

    @Test
    void rechazaNombreVacioEnBlancoONulo() {
        for (String nombre : new String[] {"", "   ", null}) {
            assertEquals(Optional.of("Escribe el nombre del servicio."),
                Validaciones.errorServicio(servicio(nombre, 45, "50000", List.of())));
        }
    }

    @Test
    void aceptaHastaVeinticuatroHorasYRechazaMas() {
        assertTrue(Validaciones.errorServicio(servicio("Masaje", 1440, "50000", List.of())).isEmpty());
        for (int duracion : new int[] {1441, Integer.MAX_VALUE}) {
            assertEquals(Optional.of("La duración no puede superar 24 horas (1440 minutos)."),
                Validaciones.errorServicio(servicio("Masaje", duracion, "50000", List.of())));
        }
    }

    @Test
    void rechazaDuracionCeroONegativa() {
        for (int duracion : new int[] {0, -30}) {
            assertEquals(Optional.of("La duración debe ser un número entero de minutos mayor que cero."),
                Validaciones.errorServicio(servicio("Masaje", duracion, "50000", List.of())));
        }
    }

    @Test
    void rechazaValorNegativoONulo() {
        assertEquals(Optional.of("El valor no puede ser negativo."),
                Validaciones.errorServicio(servicio("Masaje", 45, "-1", List.of())));
        assertEquals(Optional.of("El valor no puede ser negativo."),
                Validaciones.errorServicio(new Servicio(UUID.randomUUID(), "Masaje", 45, true, null, List.of())));
    }

    @Test
    void rechazaVentanaConFinIgualOAnteriorAlInicio() {
        assertEquals(Optional.of("Martes: la hora de fin debe ser posterior a la de inicio."),
                Validaciones.errorServicio(conVentanas(ventana(2, "10:00", "10:00"))));
        assertTrue(Validaciones.errorServicio(conVentanas(ventana(2, "12:00", "09:00"))).isPresent());
    }

    @Test
    void rechazaVentanaSinHoras() {
        assertEquals(Optional.of("Martes: escribe la hora de inicio y de fin de la ventana."),
                Validaciones.errorServicio(conVentanas(
                new VentanaDisponibilidad(UUID.randomUUID(), 2, null, LocalTime.parse("10:00")))));
    }

    @Test
    void elMensajeDeUnaVentanaInvalidaNombraSuDia() {
        assertTrue(Validaciones.errorServicio(conVentanas(ventana(3, "10:00", "09:00"))).orElseThrow().contains("Miércoles"));
    }

    @Test
    void rechazaVentanasQueSeCruzanNombrandoDiaYHoras() {
        String mensaje = Validaciones.errorServicio(
                conVentanas(ventana(2, "09:00", "12:00"), ventana(2, "11:00", "14:00"))).orElseThrow();
        assertEquals("Martes: las ventanas 09:00–12:00 y 11:00–14:00 se cruzan. "
                + "Ajusta las horas para que una termine donde empieza la otra o antes.", mensaje);
    }

    @Test
    void rechazaUnaVentanaContenidaEnOtraAunqueEstenEnOtroOrden() {
        assertTrue(Validaciones.errorServicio(conVentanas(ventana(3, "10:00", "11:00"), ventana(3, "08:00", "13:00"))).isPresent());
    }

    @Test
    void rechazaVentanasIdenticasRepetidas() {
        assertTrue(Validaciones.errorServicio(conVentanas(ventana(1, "09:00", "12:00"), ventana(1, "09:00", "12:00"))).isPresent());
    }

    @Test
    void permiteVentanasQueSeTocanOEstanEnDiasDistintos() {
        assertTrue(Validaciones.errorServicio(conVentanas(ventana(2, "09:00", "12:00"), ventana(2, "12:00", "15:00"))).isEmpty());
        assertTrue(Validaciones.errorServicio(conVentanas(ventana(2, "09:00", "12:00"), ventana(3, "10:00", "13:00"))).isEmpty());
        assertTrue(Validaciones.errorServicio(conVentanas(ventana(2, "15:00", "18:00"), ventana(2, "09:00", "13:00"))).isEmpty());
    }

    @Test
    void noModificaLaListaDeVentanasDelServicio() {
        List<VentanaDisponibilidad> original =
                new ArrayList<>(List.of(ventana(2, "15:00", "18:00"), ventana(2, "09:00", "13:00")));
        Servicio s = servicio("Masaje", 45, "50000", original);
        Validaciones.errorServicio(s);
        assertEquals(original, s.ventanas());
    }

    @Test
    void aceptaCorreosNormalesConEspaciosAlrededor() {
        for (String c : new String[] {"nombre@correo.com", "a.b+c@sub.dominio.co", " ana@correo.com ", "a@b.co"}) {
            assertTrue(Validaciones.esCorreoValido(c), c);
        }
    }

    @Test
    void rechazaLoQueNoTieneFormaDeCorreo() {
        for (String c : new String[] {"", "sin-arroba", "a@b", "a@b.", "a@.com", "a@b..c", "a b@c.co", "a@@c.com", "@c.com"}) {
            assertFalse(Validaciones.esCorreoValido(c), c);
        }
        assertFalse(Validaciones.esCorreoValido(null));
    }

    @Test
    void normalizaCorreoYUsuarioConTrimYMinusculas() {
        assertEquals("marta", Validaciones.normalizarUsuario(" Marta "));
        assertEquals("ana@correo.com", Validaciones.normalizarCorreo("  Ana@Correo.COM "));
        assertEquals(Validaciones.normalizarUsuario("marta "), Validaciones.normalizarUsuario("MARTA"));
    }

    @Test
    void normalizarNuloDevuelveVacio() {
        assertTrue(Validaciones.normalizarUsuario(null).isEmpty());
        assertTrue(Validaciones.normalizarCorreo(null).isEmpty());
    }

    @Test
    void normalizaConLocaleRootAunqueElLocaleSeaTurco() {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("martin", Validaciones.normalizarUsuario("MARTIN"));
        } finally {
            Locale.setDefault(anterior);
        }
    }
}
