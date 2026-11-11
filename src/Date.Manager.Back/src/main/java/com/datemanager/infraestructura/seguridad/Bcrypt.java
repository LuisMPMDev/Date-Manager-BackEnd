package com.datemanager.infraestructura.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import com.datemanager.aplicacion.CodificadorClave;

/**
 * BCrypt (Blowfish con la llave «cara», {@code eksblowfish}) sin dependencias. Produce hashes {@code $2a$10$...}
 * de 60 caracteres con sal aleatoria y comprueba hashes {@code $2a$}, {@code $2b$} y {@code $2y$} de cualquier costo
 * entre 4 y 31, que es lo que escribía el {@code BCryptPasswordEncoder} de la aplicación original.
 *
 * <p>BCrypt solo usa los primeros 72 bytes (UTF-8) de la clave. Como el {@code BCryptPasswordEncoder} de Spring,
 * {@link #codificar} se niega ({@link IllegalArgumentException}) a codificar una clave más larga, para no guardar sin
 * avisar el hash de una clave recortada; {@link #coincide} sí recorta, igual que {@code matches} de Spring. Un hash
 * mal formado nunca lanza: simplemente no coincide. La comparación del hash es de tiempo constante.
 */
public final class Bcrypt implements CodificadorClave {

    /** Costo (logaritmo de las rondas) de los hashes nuevos. */
    static final int COSTO = 10;

    private static final int COSTO_MINIMO = 4;
    private static final int COSTO_MAXIMO = 31;
    private static final int BYTES_MAXIMOS_CLAVE = 72;
    private static final int BYTES_SAL = 16;
    private static final int BYTES_HASH = 23;
    /** {@code $2a$10$} + 22 caracteres de sal. */
    private static final int LARGO_SAL_CON_PREFIJO = 29;
    private static final int LARGO_HASH = 60;
    private static final int RONDAS_BLOWFISH = 16;

    /** Alfabeto base64 propio de bcrypt (no es el estándar). */
    private static final char[] ALFABETO = "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final byte[] INVERSO = new byte[128];

    /** El texto cifrado inicial de bcrypt: "OrpheanBeholderScryDoubt". */
    private static final int[] TEXTO_INICIAL = {0x4f727068, 0x65616e42, 0x65686f6c, 0x64657253, 0x63727944, 0x6f756274};

    static {
        Arrays.fill(INVERSO, (byte) -1);
        for (int i = 0; i < ALFABETO.length; i++) {
            INVERSO[ALFABETO[i]] = (byte) i;
        }
    }

    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * @throws IllegalArgumentException si la clave pasa de 72 bytes en UTF-8 (el mensaje no incluye la clave)
     */
    @Override
    public String codificar(String clave) {
        if (clave.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS_CLAVE) {
            throw new IllegalArgumentException("La clave no puede pasar de " + BYTES_MAXIMOS_CLAVE + " bytes.");
        }
        byte[] sal = new byte[BYTES_SAL];
        aleatorio.nextBytes(sal);
        return hashear(clave, "$2a$" + (COSTO < 10 ? "0" : "") + COSTO + "$" + codificarBase64(sal, BYTES_SAL));
    }

    @Override
    public boolean coincide(String clave, String hash) {
        if (clave == null || hash == null || hash.length() != LARGO_HASH) {
            return false;
        }
        String calculado;
        try {
            calculado = hashear(clave, hash.substring(0, LARGO_SAL_CON_PREFIJO));
        } catch (IllegalArgumentException e) {
            return false;
        }
        // El hash guardado tiene que ser exactamente el que sale de la sal que dice llevar (incluye validar sus 31
        // caracteres finales); la comparación recorre todos los bytes aunque el primero ya difiera.
        return MessageDigest.isEqual(calculado.getBytes(StandardCharsets.US_ASCII),
                hash.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * El hash completo de {@code clave} con la sal (y la versión y el costo) de {@code sal}: {@code $2a$CC$} seguido
     * de 22 caracteres de sal; si trae más (un hash entero), se ignora el resto.
     *
     * @throws IllegalArgumentException si el prefijo, el costo o la sal no son válidos
     */
    static String hashear(String clave, String sal) {
        if (sal == null || sal.length() < LARGO_SAL_CON_PREFIJO || sal.charAt(0) != '$' || sal.charAt(1) != '2'
                || "aby".indexOf(sal.charAt(2)) < 0 || sal.charAt(3) != '$' || sal.charAt(6) != '$') {
            throw new IllegalArgumentException("Prefijo de bcrypt inválido.");
        }
        int decenas = sal.charAt(4) - '0';
        int unidades = sal.charAt(5) - '0';
        if (decenas < 0 || decenas > 9 || unidades < 0 || unidades > 9) {
            throw new IllegalArgumentException("Costo de bcrypt inválido.");
        }
        int costo = decenas * 10 + unidades;
        if (costo < COSTO_MINIMO || costo > COSTO_MAXIMO) {
            throw new IllegalArgumentException("Costo de bcrypt fuera de rango.");
        }
        byte[] salBytes = decodificarSal(sal.substring(7, LARGO_SAL_CON_PREFIJO));

        // La clave termina en un byte 0 (BCrypt lo exige desde $2a$) y solo se usan los primeros 72 bytes.
        byte[] utf8 = clave.getBytes(StandardCharsets.UTF_8);
        byte[] llave = Arrays.copyOf(utf8, Math.min(utf8.length + 1, BYTES_MAXIMOS_CLAVE));
        try {
            byte[] hash = new Blowfish().calcular(llave, salBytes, costo);
            return sal.substring(0, 7) + codificarBase64(salBytes, BYTES_SAL) + codificarBase64(hash, BYTES_HASH);
        } finally {
            Arrays.fill(llave, (byte) 0);
            Arrays.fill(utf8, (byte) 0);
        }
    }

    // ------------------------------------------------------------------ base64 de bcrypt

    private static String codificarBase64(byte[] datos, int largo) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < largo) {
            int c1 = datos[i++] & 0xff;
            sb.append(ALFABETO[(c1 >> 2) & 0x3f]);
            c1 = (c1 & 0x03) << 4;
            if (i >= largo) {
                sb.append(ALFABETO[c1 & 0x3f]);
                break;
            }
            int c2 = datos[i++] & 0xff;
            c1 |= (c2 >> 4) & 0x0f;
            sb.append(ALFABETO[c1 & 0x3f]);
            c1 = (c2 & 0x0f) << 2;
            if (i >= largo) {
                sb.append(ALFABETO[c1 & 0x3f]);
                break;
            }
            c2 = datos[i++] & 0xff;
            c1 |= (c2 >> 6) & 0x03;
            sb.append(ALFABETO[c1 & 0x3f]);
            sb.append(ALFABETO[c2 & 0x3f]);
        }
        return sb.toString();
    }

    /** 22 caracteres de bcrypt a los 16 bytes de la sal (los 4 bits sobrantes del último carácter se descartan). */
    private static byte[] decodificarSal(String texto) {
        int[] valores = new int[22];
        for (int i = 0; i < 22; i++) {
            char c = texto.charAt(i);
            int valor = c < INVERSO.length ? INVERSO[c] : -1;
            if (valor < 0) {
                throw new IllegalArgumentException("Carácter inválido en la sal de bcrypt.");
            }
            valores[i] = valor;
        }
        byte[] sal = new byte[BYTES_SAL];
        int salida = 0;
        for (int i = 0; i + 1 < 22 && salida < BYTES_SAL; i += 4) {
            sal[salida++] = (byte) ((valores[i] << 2) | ((valores[i + 1] & 0x30) >> 4));
            if (salida >= BYTES_SAL || i + 2 >= 22) {
                break;
            }
            sal[salida++] = (byte) (((valores[i + 1] & 0x0f) << 4) | ((valores[i + 2] & 0x3c) >> 2));
            if (salida >= BYTES_SAL || i + 3 >= 22) {
                break;
            }
            sal[salida++] = (byte) (((valores[i + 2] & 0x03) << 6) | valores[i + 3]);
        }
        return sal;
    }

    // ------------------------------------------------------------------ Blowfish con llave cara

    /** Un estado de Blowfish (P-array y S-boxes) y el cálculo de bcrypt sobre él. */
    private static final class Blowfish {

        private final int[] p = ConstantesBlowfish.P.clone();
        private final int[] s = new int[1024];

        Blowfish() {
            System.arraycopy(ConstantesBlowfish.S0, 0, s, 0, 256);
            System.arraycopy(ConstantesBlowfish.S1, 0, s, 256, 256);
            System.arraycopy(ConstantesBlowfish.S2, 0, s, 512, 256);
            System.arraycopy(ConstantesBlowfish.S3, 0, s, 768, 256);
        }

        /** Los 23 primeros bytes del resultado (el último de los 24 no se usa en el formato de bcrypt). */
        byte[] calcular(byte[] llave, byte[] sal, int costo) {
            long rondas = 1L << costo;
            expandirConSal(sal, llave);
            for (long i = 0; i < rondas; i++) {
                expandir(llave);
                expandir(sal);
            }
            int[] texto = TEXTO_INICIAL.clone();
            for (int i = 0; i < 64; i++) {
                for (int j = 0; j < texto.length; j += 2) {
                    cifrar(texto, j);
                }
            }
            byte[] resultado = new byte[texto.length * 4];
            for (int i = 0, j = 0; i < texto.length; i++) {
                resultado[j++] = (byte) (texto[i] >> 24);
                resultado[j++] = (byte) (texto[i] >> 16);
                resultado[j++] = (byte) (texto[i] >> 8);
                resultado[j++] = (byte) texto[i];
            }
            return resultado;
        }

        /** Cifra en el sitio el par de palabras {@code datos[desde]}, {@code datos[desde + 1]}. */
        private void cifrar(int[] datos, int desde) {
            int l = datos[desde] ^ p[0];
            int r = datos[desde + 1];
            for (int i = 1; i <= RONDAS_BLOWFISH; i += 2) {
                r ^= funcion(l) ^ p[i];
                l ^= funcion(r) ^ p[i + 1];
            }
            datos[desde] = r ^ p[RONDAS_BLOWFISH + 1];
            datos[desde + 1] = l;
        }

        private int funcion(int x) {
            return ((s[(x >>> 24) & 0xff] + s[0x100 | ((x >>> 16) & 0xff)]) ^ s[0x200 | ((x >>> 8) & 0xff)])
                    + s[0x300 | (x & 0xff)];
        }

        /** Siguiente palabra de 32 bits del flujo circular de {@code datos}; {@code posicion[0]} avanza. */
        private static int palabra(byte[] datos, int[] posicion) {
            int palabra = 0;
            int actual = posicion[0];
            for (int i = 0; i < 4; i++) {
                palabra = (palabra << 8) | (datos[actual] & 0xff);
                actual = (actual + 1) % datos.length;
            }
            posicion[0] = actual;
            return palabra;
        }

        /** El paso de bcrypt sin sal: mezcla la llave y vuelve a cifrar todo el estado. */
        private void expandir(byte[] llave) {
            int[] posicion = {0};
            for (int i = 0; i < p.length; i++) {
                p[i] ^= palabra(llave, posicion);
            }
            int[] bloque = {0, 0};
            regenerar(bloque, null, null);
        }

        /** {@code ExpandKey} con sal: además mezcla la sal en cada bloque cifrado. */
        private void expandirConSal(byte[] sal, byte[] llave) {
            int[] posicionLlave = {0};
            for (int i = 0; i < p.length; i++) {
                p[i] ^= palabra(llave, posicionLlave);
            }
            int[] bloque = {0, 0};
            regenerar(bloque, sal, new int[] {0});
        }

        /** Reescribe P y las S-boxes con el cifrado encadenado de {@code bloque}, mezclando {@code sal} si la hay. */
        private void regenerar(int[] bloque, byte[] sal, int[] posicionSal) {
            for (int i = 0; i < p.length; i += 2) {
                mezclar(bloque, sal, posicionSal);
                cifrar(bloque, 0);
                p[i] = bloque[0];
                p[i + 1] = bloque[1];
            }
            for (int i = 0; i < s.length; i += 2) {
                mezclar(bloque, sal, posicionSal);
                cifrar(bloque, 0);
                s[i] = bloque[0];
                s[i + 1] = bloque[1];
            }
        }

        private static void mezclar(int[] bloque, byte[] sal, int[] posicionSal) {
            if (sal != null) {
                bloque[0] ^= palabra(sal, posicionSal);
                bloque[1] ^= palabra(sal, posicionSal);
            }
        }
    }
}
