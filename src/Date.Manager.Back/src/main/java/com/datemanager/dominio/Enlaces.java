package com.datemanager.dominio;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;

/** Reglas puras de los enlaces de invitación: estado según el reloj, código aleatorio y límites al crearlos. */
public final class Enlaces {

    public static final int DURACION_MINIMA = 60;
    /** 30 días. */
    public static final int DURACION_MAXIMA = 43_200;
    /** 24 horas. */
    public static final int DURACION_POR_DEFECTO = 1_440;

    /** 16 bytes = 128 bits aleatorios → 22 caracteres en Base64 URL sin relleno. */
    private static final int BYTES_CODIGO = 16;
    /** Lo que puede ser un código (hoy 22 caracteres; hasta 43 = 256 bits si se alarga algún día). */
    private static final Pattern FORMATO_CODIGO = Pattern.compile("[A-Za-z0-9_-]{22,43}");
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private Enlaces() {
    }

    /**
     * Estado en {@code ahora}, con precedencia {@code Desactivado} > {@code Vencido} ({@code ahora >= venceEn}) >
     * {@code Agotado} ({@code usos >= maxUsos}) > {@code Activo}.
     */
    public static EstadoEnlace estado(Enlace enlace, Instant ahora) {
        if (enlace.desactivadoEn() != null) {
            return EstadoEnlace.Desactivado;
        }
        if (!ahora.isBefore(enlace.venceEn())) {
            return EstadoEnlace.Vencido;
        }
        if (enlace.maxUsos() != null && enlace.usos() >= enlace.maxUsos()) {
            return EstadoEnlace.Agotado;
        }
        return EstadoEnlace.Activo;
    }

    /** Código nuevo: 128 bits de {@link SecureRandom} en Base64 URL (apto para la ruta, sin relleno). */
    public static String nuevoCodigo() {
        byte[] bytes = new byte[BYTES_CODIGO];
        ALEATORIO.nextBytes(bytes);
        return BASE64_URL.encodeToString(bytes);
    }

    /**
     * Si el texto puede ser un código. Lo que no lo es se responde como un código inexistente sin consultar la base
     * (que, por ejemplo, ignoraría un espacio final al comparar).
     */
    public static boolean esCodigoBienFormado(String codigo) {
        return codigo != null && FORMATO_CODIGO.matcher(codigo).matches();
    }

    /** Duración pedida en minutos (nula = 24 h); fuera de 60..43200 es {@code VALIDACION}. */
    public static int duracionMinutos(Integer pedida) {
        if (pedida == null) {
            return DURACION_POR_DEFECTO;
        }
        if (pedida < DURACION_MINIMA || pedida > DURACION_MAXIMA) {
            throw invalido("duracionMinutos", "La duración debe estar entre 60 minutos (1 hora) y 43200 (30 días).");
        }
        return pedida;
    }

    /** Máximo de usos: nulo (sin límite) o al menos 1. */
    public static void validarMaxUsos(Integer maxUsos) {
        if (maxUsos != null && maxUsos < 1) {
            throw invalido("maxUsos", "El máximo de usos debe ser al menos 1, o vacío para no tener límite.");
        }
    }

    /** Error de un enlace que no está {@code Activo}. */
    public static ErrorNegocio errorDe(EstadoEnlace estado) {
        return switch (estado) {
            case Vencido -> new ErrorNegocio(CodigoError.ENLACE_VENCIDO, "Este enlace de invitación ya venció.");
            case Agotado -> new ErrorNegocio(CodigoError.ENLACE_AGOTADO,
                    "Este enlace de invitación ya alcanzó su máximo de citas.");
            case Desactivado -> new ErrorNegocio(CodigoError.ENLACE_DESACTIVADO,
                    "Este enlace de invitación fue desactivado.");
            case Activo -> throw new IllegalArgumentException("Un enlace activo no es un error.");
        };
    }

    /** Siempre el mismo error, exista algo parecido o no: no da pistas para adivinar otros códigos. */
    public static ErrorNegocio noEncontrado() {
        return new ErrorNegocio(CodigoError.ENLACE_NO_ENCONTRADO, "El enlace de invitación no existe.");
    }

    private static ErrorNegocio invalido(String campo, String mensaje) {
        return new ErrorNegocio(CodigoError.VALIDACION, mensaje, Map.of(campo, mensaje));
    }
}
