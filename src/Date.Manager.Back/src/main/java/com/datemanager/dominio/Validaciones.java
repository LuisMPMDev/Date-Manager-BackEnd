package com.datemanager.dominio;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Validaciones y normalizaciones puras (porta {@code core/validaciones.ts}). */
public final class Validaciones {

    private static final List<String> DIAS =
            List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern CORREO =
            Pattern.compile("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$", Pattern.UNICODE_CHARACTER_CLASS);

    private Validaciones() {
    }

    /** Mensaje de error en español si el servicio no se puede guardar; vacío si es válido. */
    public static Optional<String> errorServicio(Servicio s) {
        if (s.nombre() == null || s.nombre().isBlank()) {
            return Optional.of("Escribe el nombre del servicio.");
        }
        if (s.duracionMin() <= 0) {
            return Optional.of("La duración debe ser un número entero de minutos mayor que cero.");
        }
        if (s.valor() == null || s.valor().signum() < 0) {
            return Optional.of("El valor no puede ser negativo.");
        }
        for (VentanaDisponibilidad v : s.ventanas()) {
            String dia = nombreDia(v.dia(), "Día");
            if (v.inicio() == null || v.fin() == null) {
                return Optional.of(dia + ": escribe la hora de inicio y de fin de la ventana.");
            }
            if (!v.inicio().isBefore(v.fin())) {
                return Optional.of(dia + ": la hora de fin debe ser posterior a la de inicio.");
            }
        }
        return mensajeDeVentanasCruzadas(s);
    }

    /** Dos ventanas del mismo día no pueden cruzarse (que una termine justo cuando empieza la otra sí vale). */
    private static Optional<String> mensajeDeVentanasCruzadas(Servicio s) {
        for (int dia = 1; dia <= 7; dia++) {
            int diaActual = dia;
            List<VentanaDisponibilidad> delDia = s.ventanas().stream()
                    .filter(v -> v.dia() == diaActual)
                    .sorted(Comparator.comparing(VentanaDisponibilidad::inicio))
                    .toList();
            for (int i = 1; i < delDia.size(); i++) {
                VentanaDisponibilidad antes = delDia.get(i - 1);
                VentanaDisponibilidad despues = delDia.get(i);
                if (despues.inicio().isBefore(antes.fin())) {
                    return Optional.of(DIAS.get(dia - 1) + ": las ventanas " + hora(antes.inicio()) + "–"
                            + hora(antes.fin()) + " y " + hora(despues.inicio()) + "–" + hora(despues.fin())
                            + " se cruzan. Ajusta las horas para que una termine donde empieza la otra o antes.");
                }
            }
        }
        return Optional.empty();
    }

    /** Una sola regla para el correo en toda la app: lo que se acepta al reservar se puede guardar en Clientes. */
    public static boolean esCorreoValido(String correo) {
        return correo != null && CORREO.matcher(correo.strip()).matches();
    }

    /** Trim + minúsculas con {@code Locale.ROOT}; nulo queda vacío. */
    public static String normalizarCorreo(String correo) {
        return normalizar(correo);
    }

    /** Trim + minúsculas con {@code Locale.ROOT}; nulo queda vacío. */
    public static String normalizarUsuario(String usuario) {
        return normalizar(usuario);
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
    }

    private static String nombreDia(int dia, String porDefecto) {
        return dia >= 1 && dia <= DIAS.size() ? DIAS.get(dia - 1) : porDefecto;
    }

    private static String hora(LocalTime hora) {
        return hora.format(HHMM);
    }
}
