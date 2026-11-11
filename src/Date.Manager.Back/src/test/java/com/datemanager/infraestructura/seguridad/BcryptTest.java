package com.datemanager.infraestructura.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Las claves y las sales de los vectores son de ejemplo. Los hashes esperados vienen de dos fuentes independientes de
 * este código: los vectores públicos de jBCrypt/OpenBSD y el {@code BCrypt}/{@code BCryptPasswordEncoder} de Spring
 * Security 6.5.6 (la implementación que escribió los hashes de la aplicación original).
 */
class BcryptTest {

    private static final Pattern FORMA_COSTO_10 = Pattern.compile("\\$2a\\$10\\$[./A-Za-z0-9]{53}");

    /** Sal fija de los vectores de costo 5. */
    private static final String SAL_C = "$2a$05$CCCCCCCCCCCCCCCCCCCCC.";
    private static final String SAL_ROJA = "$2a$06$DCq7YPn5Rq63x1Lad4cll.";

    // Hashes que generó el BCryptPasswordEncoder de Spring (costo 10, sal aleatoria) para estas claves de ejemplo.
    private static final String CLAVE_SPRING_A = "Prueba-Clave-1";
    private static final String HASH_SPRING_A = "$2a$10$vixASfLD20qxB.gNeD87ReDPHGDdnDFOnvX5VHfbBGI9pPlyxMomu";
    private static final String CLAVE_SPRING_B = "otra clave ñ😀 de prueba";
    private static final String HASH_SPRING_B = "$2a$10$JL2vTiINMulctTwDmcm8POUnWcjsUa3WBL7zb9SMIkFF7lLKEWh.2";
    private static final String CLAVE_SPRING_2B = "Prueba-Clave-2";
    private static final String HASH_SPRING_2B = "$2b$10$VTeSo8CLU1rHgXMO9vT4EO.iKhED2UsqQNtWguNT1EYyxuHV/ki7a";
    private static final String CLAVE_SPRING_2Y = "Prueba-Clave-3";
    private static final String HASH_SPRING_2Y = "$2y$10$Kqdp.EnJBiSfYctig2wHju2e4IHYocEJ5b2bwuq7TGnSykZ45JtOG";

    private final Bcrypt bcrypt = new Bcrypt();

    // ------------------------------------------------------------------ vectores públicos y de Spring

    @Test
    void vectoresPublicosDeJBCryptYOpenBsd() {
        assertEquals("$2a$06$DCq7YPn5Rq63x1Lad4cll.TV4S6ytwfsfvkgY8jIucDrjc8deX1s.", Bcrypt.hashear("", SAL_ROJA));
        assertEquals("$2a$06$m0CrhHm10qJ3lXRY.5zDGO3rS2KdeeWLuGmsfGlMfOxih58VYVfxe",
                Bcrypt.hashear("a", "$2a$06$m0CrhHm10qJ3lXRY.5zDGO"));
    }

    @Test
    void mismosHashesQueSpringConUnaSalFija() {
        assertEquals("$2a$06$DCq7YPn5Rq63x1Lad4cll.H41X4kZus1aFK7afV5nRTlWViVmkNmO", Bcrypt.hashear("ñ😀", SAL_ROJA));
        assertEquals("$2a$05$DCq7YPn5Rq63x1Lad4cll.f5I6Ad0uGO2fctejA3L4RvAtk3Yu/Xi",
                Bcrypt.hashear("😀", "$2a$05$DCq7YPn5Rq63x1Lad4cll."));
        assertEquals("$2a$05$CCCCCCCCCCCCCCCCCCCCC.gNn9EDJdrUJF53AHnb.4T9BCvqhUYdW", Bcrypt.hashear("abc", SAL_C));
        assertEquals("$2a$05$CCCCCCCCCCCCCCCCCCCCC.WbbLb4J7PKYWYm3biA.M18AI380Pnzu",
                Bcrypt.hashear("x".repeat(71), SAL_C));
        assertEquals("$2a$05$CCCCCCCCCCCCCCCCCCCCC.qjiB2XDSBWMDGUjwsL4V3UA7SGXjPU.",
                Bcrypt.hashear("x".repeat(71) + "Q", SAL_C));
    }

    @Test
    void laVersionDelPrefijoSeConservaEnLaSalida() {
        String cuerpo = "05$CCCCCCCCCCCCCCCCCCCCC.gNn9EDJdrUJF53AHnb.4T9BCvqhUYdW";
        assertEquals("$2b$" + cuerpo, Bcrypt.hashear("abc", "$2b$05$CCCCCCCCCCCCCCCCCCCCC."));
        assertEquals("$2y$" + cuerpo, Bcrypt.hashear("abc", "$2y$05$CCCCCCCCCCCCCCCCCCCCC."));
    }

    @Test
    void coincideConHashesQueGeneroSpring() {
        assertTrue(bcrypt.coincide(CLAVE_SPRING_A, HASH_SPRING_A));
        assertTrue(bcrypt.coincide(CLAVE_SPRING_B, HASH_SPRING_B));
        assertTrue(bcrypt.coincide(CLAVE_SPRING_2B, HASH_SPRING_2B));
        assertTrue(bcrypt.coincide(CLAVE_SPRING_2Y, HASH_SPRING_2Y));
        assertFalse(bcrypt.coincide("prueba-clave-1", HASH_SPRING_A));
        assertFalse(bcrypt.coincide(CLAVE_SPRING_A, HASH_SPRING_B));
        assertFalse(bcrypt.coincide("", HASH_SPRING_A));
    }

    @Test
    void aceptaLosTresPrefijosDeVersion() {
        String hash2a = Bcrypt.hashear("abc", SAL_C);
        for (String version : new String[] {"2a", "2b", "2y"}) {
            String hash = "$" + version + hash2a.substring(3);
            assertTrue(bcrypt.coincide("abc", hash), version);
            assertFalse(bcrypt.coincide("abd", hash), version);
        }
    }

    // ------------------------------------------------------------------ codificar

    @Test
    void codificarDaUnHashDeCostoDiezConElFormatoDelEsquema() {
        String hash = bcrypt.codificar("Clave segura 123");

        assertTrue(FORMA_COSTO_10.matcher(hash).matches(), hash);
        assertEquals(60, hash.length());
    }

    @Test
    void cadaCodificacionUsaUnaSalDistinta() {
        String uno = bcrypt.codificar("misma");
        String otro = bcrypt.codificar("misma");

        assertNotEquals(uno, otro);
        assertTrue(bcrypt.coincide("misma", uno));
        assertTrue(bcrypt.coincide("misma", otro));
    }

    @Test
    void idaYVueltaConTildesEnyeYEmoji() {
        String clave = "ñ😀";
        String hash = bcrypt.codificar(clave);

        assertTrue(bcrypt.coincide(clave, hash));
        assertFalse(bcrypt.coincide("n😀", hash));
        assertFalse(bcrypt.coincide("ñ", hash));
    }

    @Test
    void claveVaciaTambienSeCodificaYSeComprueba() {
        String hash = bcrypt.codificar("");

        assertTrue(bcrypt.coincide("", hash));
        assertFalse(bcrypt.coincide(" ", hash));
    }

    // ------------------------------------------------------------------ 72 bytes

    @Test
    void soloCuentanLosPrimeros72Bytes() {
        String exacta = "x".repeat(72);
        // Lo que Spring calcula para 72 bytes exactos (con 73 o más, su codificador se niega a codificar).
        String hash = "$2a$05$CCCCCCCCCCCCCCCCCCCCC.D7RDOYXakZhsFmh/LdqQNx0kQ424AFS";

        assertEquals(hash, Bcrypt.hashear(exacta, SAL_C));
        assertEquals(hash, Bcrypt.hashear(exacta + "ZZZ", SAL_C));
        assertEquals(hash, Bcrypt.hashear("x".repeat(500), SAL_C));
        assertTrue(bcrypt.coincide("x".repeat(80), hash));
        assertFalse(bcrypt.coincide("x".repeat(71), hash));
    }

    @Test
    void elCorteEsPorBytesNoPorCaracteres() {
        // 36 'ñ' son 72 bytes; Spring da este hash para ellos.
        String hash = "$2a$05$CCCCCCCCCCCCCCCCCCCCC.bwyFH7jRnLOarcBuB7FVpCXtCj8Wu7a";

        assertEquals(hash, Bcrypt.hashear("ñ".repeat(36), SAL_C));
        assertEquals(hash, Bcrypt.hashear("ñ".repeat(36) + "ZZZ", SAL_C));
        // 35 'ñ' + "ab" también son 72 bytes (hash de Spring).
        assertEquals("$2a$05$CCCCCCCCCCCCCCCCCCCCC.u82nCj2J4YXUyIhPLXuoelT3oAdFjae",
                Bcrypt.hashear("ñ".repeat(35) + "ab", SAL_C));
    }

    @Test
    void codificarConClaveLargaTruncaComoLoHaceCoincide() {
        String larga = "y".repeat(90);
        String hash = bcrypt.codificar(larga);

        assertTrue(bcrypt.coincide("y".repeat(72), hash));
        assertTrue(bcrypt.coincide(larga, hash));
        assertFalse(bcrypt.coincide("y".repeat(71), hash));
    }

    // ------------------------------------------------------------------ hashes inválidos: false, nunca excepción

    @Test
    void hashCorruptoOMalFormadoNoCoincideYNoLanza() {
        String bueno = HASH_SPRING_A;
        String[] malos = {
            null,
            "",
            "basura",
            "$2a$10$",
            bueno.substring(0, 59),
            bueno + "x",
            "$2c$" + bueno.substring(4),
            "$3a$" + bueno.substring(4),
            "$2a$1$" + bueno.substring(6),
            "$2a$ab$" + bueno.substring(7),
            "$2a$03$" + bueno.substring(7),
            "$2a$32$" + bueno.substring(7),
            "$2a$10!" + bueno.substring(7),
            bueno.substring(0, 20) + "!" + bueno.substring(21),
            bueno.substring(0, 40) + "ñ" + bueno.substring(41),
            " " + bueno,
        };
        for (String malo : malos) {
            assertFalse(bcrypt.coincide(CLAVE_SPRING_A, malo), String.valueOf(malo));
        }
        assertFalse(bcrypt.coincide(null, bueno));
    }

    @Test
    void unCaracterCambiadoDelHashNoCoincide() {
        char[] hash = HASH_SPRING_A.toCharArray();
        for (int i = 7; i < hash.length; i += 7) {
            char original = hash[i];
            hash[i] = original == 'a' ? 'b' : 'a';
            assertFalse(bcrypt.coincide(CLAVE_SPRING_A, new String(hash)), "posición " + i);
            hash[i] = original;
        }
        assertTrue(bcrypt.coincide(CLAVE_SPRING_A, new String(hash)));
    }
}
