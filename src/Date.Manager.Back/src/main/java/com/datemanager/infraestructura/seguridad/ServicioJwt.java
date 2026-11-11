package com.datemanager.infraestructura.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.datemanager.infraestructura.http.Json;
import com.datemanager.infraestructura.http.JsonInvalido;

/**
 * Emite y valida los tokens de acceso (JWT HS256). El token solo lleva el id del usuario ({@code sub}) y la
 * expiración ({@code exp}, 60 minutos); ambas cosas se calculan y comprueban con el {@link Clock} recibido, sin
 * tolerancia: en el segundo {@code exp} el token ya no vale.
 *
 * <p>{@link #validar} nunca lanza por un token raro: cualquier cosa que no sea un JWT HS256 bien firmado, vigente y
 * con un {@code sub} UUID da {@link Optional#empty()}.
 */
public final class ServicioJwt {

    static final Duration VIDA = Duration.ofMinutes(60);
    private static final int BYTES_MINIMOS = 32;
    /** Un token legítimo mide unos 150 caracteres; más de esto ni se mira. */
    private static final int LARGO_MAXIMO = 8192;
    private static final String ALGORITMO = "HmacSHA256";
    private static final String CABECERA = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

    private final Clock reloj;
    private final SecretKeySpec clave;

    public ServicioJwt(String secreto, Clock reloj) {
        byte[] bytes = secreto == null ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < BYTES_MINIMOS) {
            // Nunca se muestra el valor, solo qué falta.
            throw new IllegalStateException(
                    "La variable DATEMANAGER_JWT_SECRETO falta o tiene menos de " + BYTES_MINIMOS + " bytes.");
        }
        this.clave = new SecretKeySpec(bytes, ALGORITMO);
        this.reloj = reloj;
    }

    public String emitir(UUID usuarioId) {
        long vence = reloj.instant().plus(VIDA).getEpochSecond();
        String cuerpo = CABECERA + "." + b64("{\"sub\":\"" + usuarioId + "\",\"exp\":" + vence + "}");
        return cuerpo + "." + firmar(cuerpo);
    }

    /** El usuario del token, o vacío si el token no es válido por la razón que sea. */
    public Optional<UUID> validar(String token) {
        if (token == null || token.length() > LARGO_MAXIMO) {
            return Optional.empty();
        }
        String[] partes = token.split("\\.", -1);
        if (partes.length != 3 || partes[0].isEmpty() || partes[1].isEmpty() || partes[2].isEmpty()) {
            return Optional.empty();
        }
        try {
            if (!(Json.parse(decodificar(partes[0])) instanceof Map<?, ?> cabecera)
                    || !"HS256".equals(cabecera.get("alg"))) {
                return Optional.empty();
            }
            // Se compara el texto de la firma, no los bytes decodificados: así tampoco vale la misma firma escrita
            // con otros bits de relleno en el último carácter. Tiempo constante.
            byte[] esperada = firmar(partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(esperada, partes[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            if (!(Json.parse(decodificar(partes[1])) instanceof Map<?, ?> carga)) {
                return Optional.empty();
            }
            if (!(carga.get("exp") instanceof Number exp) || exp.longValue() <= reloj.instant().getEpochSecond()
                    || !(carga.get("sub") instanceof String sub)) {
                return Optional.empty();
            }
            return uuid(sub);
        } catch (IllegalArgumentException | JsonInvalido e) {
            // Base64 o JSON inválidos: no es un token nuestro.
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ interno

    private String firmar(String datos) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(clave);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(datos.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no está disponible en esta JVM.", e);
        }
    }

    private static String b64(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    /** Base64url sin relleno (el relleno {@code =} tampoco se admite); {@link IllegalArgumentException} si no lo es. */
    private static String decodificar(String parte) {
        if (parte.indexOf('=') >= 0) {
            throw new IllegalArgumentException("Relleno no permitido.");
        }
        return new String(Base64.getUrlDecoder().decode(parte), StandardCharsets.UTF_8);
    }

    /** El UUID en su forma canónica de 36 caracteres ({@code UUID.fromString} acepta además variantes como 1-1-1-1-1). */
    private static Optional<UUID> uuid(String texto) {
        if (texto.length() != 36) {
            return Optional.empty();
        }
        for (int i = 0; i < 36; i++) {
            char c = texto.charAt(i);
            boolean guion = i == 8 || i == 13 || i == 18 || i == 23;
            if (guion ? c != '-' : c >= 128 || Character.digit(c, 16) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(UUID.fromString(texto));
    }
}
