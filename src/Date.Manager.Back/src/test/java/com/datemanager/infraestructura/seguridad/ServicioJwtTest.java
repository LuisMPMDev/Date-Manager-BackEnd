package com.datemanager.infraestructura.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.datemanager.RelojMutable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioJwtTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-bytes!!";
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 10, 9, 0, 0);
    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final String CABECERA = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private RelojMutable reloj;
    private ServicioJwt jwt;

    @BeforeEach
    void armar() {
        reloj = new RelojMutable(ZoneId.of("America/Bogota"));
        reloj.fijarAhora(AHORA);
        jwt = new ServicioJwt(SECRETO, reloj);
    }

    // ------------------------------------------------------------------ ayudantes

    private static String b64(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static String firmar(String algoritmoJava, String secreto, String datos) {
        try {
            Mac mac = Mac.getInstance(algoritmoJava);
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), algoritmoJava));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(datos.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un token con la cabecera y la carga dadas, firmado con HS256 y el secreto de la prueba. */
    private static String token(String cabecera, String carga) {
        String cuerpo = b64(cabecera) + "." + b64(carga);
        return cuerpo + "." + firmar("HmacSHA256", SECRETO, cuerpo);
    }

    private long expDentroDe(long segundos) {
        return reloj.instant().getEpochSecond() + segundos;
    }

    private static String carga(String sub, Object exp) {
        return "{\"sub\":\"" + sub + "\",\"exp\":" + exp + "}";
    }

    private Optional<UUID> validar(String cabecera, String carga) {
        return jwt.validar(token(cabecera, carga));
    }

    // ------------------------------------------------------------------ emitir y validar

    @Test
    void idaYVuelta() {
        String token = jwt.emitir(USUARIO);

        assertEquals(Optional.of(USUARIO), jwt.validar(token));
    }

    @Test
    void elTokenTieneLaCabeceraYLosDosClaimsEsperados() {
        String[] partes = jwt.emitir(USUARIO).split("\\.");

        assertEquals(3, partes.length);
        assertEquals(CABECERA, new String(Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8));
        assertEquals("{\"sub\":\"" + USUARIO + "\",\"exp\":" + expDentroDe(3600) + "}",
                new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8));
        assertEquals(firmar("HmacSHA256", SECRETO, partes[0] + "." + partes[1]), partes[2]);
        assertFalse(String.join("", partes).contains("="), "sin relleno");
    }

    @Test
    void venceALos60Minutos() {
        String token = jwt.emitir(USUARIO);

        reloj.fijarAhora(AHORA.plusMinutes(59).plusSeconds(59));
        assertEquals(Optional.of(USUARIO), jwt.validar(token), "un segundo antes");
        reloj.fijarAhora(AHORA.plusMinutes(60));
        assertEquals(Optional.empty(), jwt.validar(token), "exactamente en exp ya no vale (sin tolerancia)");
        reloj.fijarAhora(AHORA.plusMinutes(60).plusSeconds(1));
        assertEquals(Optional.empty(), jwt.validar(token), "60 minutos y un segundo");
    }

    @Test
    void unTokenEmitidoPorLaVersionSpringSigueValiendo() {
        // Emitido por el NimbusJwtEncoder de la aplicación original (cabecera sin "typ", "exp" antes de "sub").
        String deSpring = "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjQxMDI0NDQ4MDAsInN1YiI6IjAwMDAwMDAwLTAwMDAtMDAwMC0wMDAwLTAwMDAwMDAwMDBhMSJ9"
                + ".5zJNXCH9vhjHhmfFUik_dCeV3gDbNygcNu3g1FkFmZk"; // exp = 2100-01-01

        assertEquals(Optional.of(USUARIO), jwt.validar(deSpring));
    }

    @Test
    void otroSecretoNoValida() {
        ServicioJwt otro = new ServicioJwt("otro-secreto-distinto-de-mas-de-32-bytes", reloj);

        assertEquals(Optional.empty(), otro.validar(jwt.emitir(USUARIO)));
    }

    // ------------------------------------------------------------------ firma

    @Test
    void firmaAlteradaNoValida() {
        String token = jwt.emitir(USUARIO);
        String[] partes = token.split("\\.");
        char primero = partes[2].charAt(0);

        String firmaCambiada = partes[0] + "." + partes[1] + "." + (primero == 'A' ? 'B' : 'A') + partes[2].substring(1);
        assertEquals(Optional.empty(), jwt.validar(firmaCambiada));

        // Los dos últimos bits del último carácter no cuentan al decodificar: la misma firma escrita de otra forma
        // tampoco se acepta.
        char ultimo = partes[2].charAt(partes[2].length() - 1);
        String alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        for (char otro : alfabeto.toCharArray()) {
            if (otro != ultimo) {
                String variante = partes[0] + "." + partes[1] + "." + partes[2].substring(0, partes[2].length() - 1) + otro;
                assertEquals(Optional.empty(), jwt.validar(variante), "último carácter " + otro);
            }
        }
    }

    @Test
    void cargaAlteradaConLaFirmaOriginalNoValida() {
        String[] partes = jwt.emitir(USUARIO).split("\\.");
        UUID otroUsuario = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        String cargaFalsa = b64(carga(otroUsuario.toString(), expDentroDe(3600)));

        assertEquals(Optional.empty(), jwt.validar(partes[0] + "." + cargaFalsa + "." + partes[2]));
        assertEquals(Optional.empty(), jwt.validar(b64("{\"alg\":\"HS256\"}") + "." + partes[1] + "." + partes[2]));
    }

    // ------------------------------------------------------------------ algoritmo

    @Test
    void algNoneSinFirmaNoValida() {
        String cuerpo = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + b64(carga(USUARIO.toString(), expDentroDe(3600)));

        assertEquals(Optional.empty(), jwt.validar(cuerpo + "."));
        assertEquals(Optional.empty(), jwt.validar(cuerpo));
        assertEquals(Optional.empty(), jwt.validar(cuerpo + "." + firmar("HmacSHA256", SECRETO, cuerpo)),
                "ni siquiera con la firma HS256 correcta");
    }

    @Test
    void algHs512NoValidaAunqueEstéBienFirmado() {
        String cabecera = "{\"alg\":\"HS512\",\"typ\":\"JWT\"}";
        String cuerpo = b64(cabecera) + "." + b64(carga(USUARIO.toString(), expDentroDe(3600)));

        assertEquals(Optional.empty(), jwt.validar(cuerpo + "." + firmar("HmacSHA512", SECRETO, cuerpo)));
        assertEquals(Optional.empty(), jwt.validar(cuerpo + "." + firmar("HmacSHA256", SECRETO, cuerpo)));
    }

    @Test
    void cabecerasQueNoSonHs256NoValen() {
        String bien = carga(USUARIO.toString(), expDentroDe(3600));
        assertEquals(Optional.of(USUARIO), validar("{\"alg\":\"HS256\"}", bien), "control: sin typ también vale");

        for (String cabecera : new String[] {"{}", "{\"typ\":\"JWT\"}", "{\"alg\":\"hs256\"}", "{\"alg\":\"RS256\"}",
                "{\"alg\":null}", "{\"alg\":256}", "{\"alg\":[\"HS256\"]}", "[]", "\"HS256\"", "nulo", "", "{\"alg\":"}) {
            assertEquals(Optional.empty(), validar(cabecera, bien), cabecera);
        }
    }

    // ------------------------------------------------------------------ claims

    @Test
    void sinSubNoValida() {
        assertEquals(Optional.empty(), validar(CABECERA, "{\"exp\":" + expDentroDe(3600) + "}"));
        assertEquals(Optional.empty(), validar(CABECERA, "{\"sub\":null,\"exp\":" + expDentroDe(3600) + "}"));
    }

    @Test
    void subQueNoEsUuidNoValida() {
        for (String sub : new String[] {"", " ", "no-es-uuid", "1234", "1-1-1-1-1",
                " 00000000-0000-0000-0000-0000000000a1", "00000000-0000-0000-0000-0000000000a1 ",
                "00000000-0000-0000-0000-0000000000a", "00000000-0000-0000-0000-0000000000zz",
                "00000000000000000000000000000000"}) {
            assertEquals(Optional.empty(), validar(CABECERA, carga(sub, expDentroDe(3600))), "sub=[" + sub + "]");
        }
        assertEquals(Optional.empty(), validar(CABECERA, "{\"sub\":12345,\"exp\":" + expDentroDe(3600) + "}"));
        assertEquals(Optional.empty(), validar(CABECERA, "{\"sub\":true,\"exp\":" + expDentroDe(3600) + "}"));
        assertEquals(Optional.empty(), validar(CABECERA, "{\"sub\":[\"" + USUARIO + "\"],\"exp\":" + expDentroDe(3600) + "}"));
    }

    @Test
    void sinExpONoNumericoNoValida() {
        assertEquals(Optional.empty(), validar(CABECERA, "{\"sub\":\"" + USUARIO + "\"}"));
        assertEquals(Optional.empty(), validar(CABECERA, carga(USUARIO.toString(), "null")));
        assertEquals(Optional.empty(), validar(CABECERA, carga(USUARIO.toString(), "\"" + expDentroDe(3600) + "\"")));
        assertEquals(Optional.empty(), validar(CABECERA, carga(USUARIO.toString(), "true")));
        assertEquals(Optional.empty(), validar(CABECERA, carga(USUARIO.toString(), "[]")));
    }

    @Test
    void expEnElPasadoOEnElMomentoNoValida() {
        assertEquals(Optional.empty(), validar(CABECERA, carga(USUARIO.toString(), expDentroDe(-1))));
        assertEquals(Optional.empty(), validar(CABECERA, carga(USUARIO.toString(), expDentroDe(0))));
        assertEquals(Optional.empty(), validar(CABECERA, carga(USUARIO.toString(), 0)));
        assertEquals(Optional.empty(), validar(CABECERA, carga(USUARIO.toString(), -5)));
        assertEquals(Optional.of(USUARIO), validar(CABECERA, carga(USUARIO.toString(), expDentroDe(1))));
    }

    @Test
    void cargaQueNoEsUnObjetoNoValida() {
        for (String carga : new String[] {"[]", "\"texto\"", "123", "null", "", "{", "no es json"}) {
            assertEquals(Optional.empty(), validar(CABECERA, carga), carga);
        }
    }

    // ------------------------------------------------------------------ formas raras

    @Test
    void tokensConFormaRaraNoValidenYNoLanzan() {
        String bueno = jwt.emitir(USUARIO);
        String[] partes = bueno.split("\\.");
        String[] raros = {
            null,
            "",
            " ",
            ".",
            "..",
            "...",
            "a",
            "a.b",
            partes[0] + "." + partes[1],
            partes[0] + "." + partes[1] + ".",
            "." + partes[1] + "." + partes[2],
            partes[0] + ".." + partes[2],
            bueno + ".extra",
            bueno + ".",
            " " + bueno,
            bueno + " ",
            bueno + "\n",
            bueno + "\r\n",
            partes[0] + " ." + partes[1] + "." + partes[2],
            partes[0] + "." + partes[1].substring(0, 10) + " " + partes[1].substring(10) + "." + partes[2],
            "Bearer " + bueno,
            "!!!.???.***",
            partes[0] + "." + partes[1] + "." + partes[2].replace('A', '+'),
            partes[0] + "." + partes[1] + "=." + partes[2],
            partes[0] + "ñ." + partes[1] + "." + partes[2],
            partes[0] + "." + partes[1] + "." + partes[2] + "ñ",
        };
        for (String raro : raros) {
            assertEquals(Optional.empty(), jwt.validar(raro), "token=[" + raro + "]");
        }
        assertEquals(Optional.of(USUARIO), jwt.validar(bueno), "control");
    }

    @Test
    void tokenEnormeNoValidaYNoLanza() {
        assertEquals(Optional.empty(), jwt.validar("a".repeat(100_000) + "." + "b".repeat(100_000) + "." + "c"));
    }

    // ------------------------------------------------------------------ secreto

    @Test
    void secretoCortoOAusenteNoArranca() {
        String mensaje = "La variable DATEMANAGER_JWT_SECRETO falta o tiene menos de 32 bytes.";

        IllegalStateException corto = assertThrows(IllegalStateException.class,
                () -> new ServicioJwt("corto", reloj));
        assertEquals(mensaje, corto.getMessage());
        assertFalse(corto.getMessage().contains("corto"), "nunca el valor");
        assertEquals(mensaje, assertThrows(IllegalStateException.class,
                () -> new ServicioJwt("x".repeat(31), reloj)).getMessage());
        assertEquals(mensaje, assertThrows(IllegalStateException.class,
                () -> new ServicioJwt(null, reloj)).getMessage());
        assertEquals(mensaje, assertThrows(IllegalStateException.class,
                () -> new ServicioJwt("", reloj)).getMessage());
    }

    @Test
    void elSecretoSeMideEnBytesNoEnCaracteres() {
        // 16 'ñ' son 16 caracteres pero 32 bytes: vale. 15 'ñ' son 30 bytes: no.
        assertTrue(new ServicioJwt("ñ".repeat(16), reloj).validar("x").isEmpty());
        assertThrows(IllegalStateException.class, () -> new ServicioJwt("ñ".repeat(15), reloj));
        new ServicioJwt("x".repeat(32), reloj);
    }
}
