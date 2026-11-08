package com.datemanager.infraestructura.seguridad;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Emite y valida los tokens de acceso (JWT HS256). El token solo lleva el id del usuario ({@code sub}) y la
 * expiración ({@code exp}, 60 minutos); ambas cosas se calculan y comprueban con el {@link Clock} del contexto.
 */
@Component
public class ServicioJwt {

    static final Duration VIDA = Duration.ofMinutes(60);
    private static final int BYTES_MINIMOS = 32;

    private final Clock reloj;
    private final JwtEncoder codificador;
    private final JwtDecoder decodificador;

    public ServicioJwt(@Value("${datemanager.jwt.secreto}") String secreto, Clock reloj) {
        byte[] bytes = secreto == null ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < BYTES_MINIMOS) {
            // Nunca se muestra el valor, solo qué falta.
            throw new IllegalStateException(
                    "La variable DATEMANAGER_JWT_SECRETO falta o tiene menos de " + BYTES_MINIMOS + " bytes.");
        }
        SecretKey clave = new SecretKeySpec(bytes, "HmacSHA256");
        this.reloj = reloj;
        this.codificador = new NimbusJwtEncoder(new ImmutableSecret<>(clave));

        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator vigencia = new JwtTimestampValidator(Duration.ZERO);
        vigencia.setClock(reloj);
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                vigencia,
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, exp -> exp != null),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, sub -> sub != null && !sub.isBlank())));
        this.decodificador = decodificador;
    }

    public String emitir(UUID usuarioId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuarioId.toString())
                .expiresAt(reloj.instant().plus(VIDA))
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    public JwtDecoder decodificador() {
        return decodificador;
    }
}
