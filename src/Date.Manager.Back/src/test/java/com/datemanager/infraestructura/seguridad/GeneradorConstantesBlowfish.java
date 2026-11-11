package com.datemanager.infraestructura.seguridad;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generador de una sola vez de {@code ConstantesBlowfish}: Blowfish inicia su P-array (18 palabras) y sus cuatro
 * S-boxes (4 x 256 palabras) con los dígitos hexadecimales de la parte fraccionaria de pi, uno tras otro. Aquí se
 * calculan con {@link BigInteger} (fórmula de Machin: pi = 16 arctan(1/5) - 4 arctan(1/239)) en vez de copiarlos de
 * ninguna parte. No forma parte de la aplicación (vive en las pruebas); se ejecuta a mano:
 *
 * <pre>java -cp target/test-classes com.datemanager.infraestructura.seguridad.GeneradorConstantesBlowfish
 *     src/main/java/com/datemanager/infraestructura/seguridad/ConstantesBlowfish.java</pre>
 *
 * {@code ConstantesBlowfishTest} comprueba que lo que hay en el código fuente sigue siendo exactamente lo que este
 * generador calcula.
 */
public final class GeneradorConstantesBlowfish {

    static final int PALABRAS_P = 18;
    static final int PALABRAS_S = 4 * 256;
    /** Bits extra para que los errores de redondeo de las series no lleguen a los dígitos que se usan. */
    private static final int BITS_GUARDA = 128;

    private GeneradorConstantesBlowfish() {
    }

    /** Las primeras {@code cuantas} palabras de 32 bits de la parte fraccionaria de pi. */
    static int[] palabrasDePi(int cuantas) {
        int bits = cuantas * 32;
        int escala = bits + BITS_GUARDA;
        BigInteger uno = BigInteger.ONE.shiftLeft(escala);
        BigInteger pi = arcotangenteInversa(5, uno).multiply(BigInteger.valueOf(16))
                .subtract(arcotangenteInversa(239, uno).multiply(BigInteger.valueOf(4)));
        // pi * 2^bits, sin la guarda; se le quita la parte entera (3) y quedan los bits fraccionarios.
        BigInteger fraccion = pi.shiftRight(BITS_GUARDA).subtract(BigInteger.valueOf(3).shiftLeft(bits));
        int[] palabras = new int[cuantas];
        for (int i = 0; i < cuantas; i++) {
            palabras[i] = fraccion.shiftRight(bits - 32 * (i + 1)).intValue();
        }
        return palabras;
    }

    /** arctan(1/x) * uno, por su serie: 1/x - 1/(3x^3) + 1/(5x^5) - ... */
    private static BigInteger arcotangenteInversa(int x, BigInteger uno) {
        BigInteger cuadrado = BigInteger.valueOf((long) x * x);
        BigInteger potencia = uno.divide(BigInteger.valueOf(x));
        BigInteger suma = potencia;
        for (int k = 1; potencia.signum() != 0; k++) {
            potencia = potencia.divide(cuadrado);
            BigInteger termino = potencia.divide(BigInteger.valueOf(2L * k + 1));
            suma = k % 2 == 1 ? suma.subtract(termino) : suma.add(termino);
        }
        return suma;
    }

    /** El código fuente completo de {@code ConstantesBlowfish}. */
    static String fuente() {
        int[] pi = palabrasDePi(PALABRAS_P + PALABRAS_S);
        StringBuilder sb = new StringBuilder();
        sb.append("package com.datemanager.infraestructura.seguridad;\n\n");
        sb.append("/**\n");
        sb.append(" * Constantes iniciales de Blowfish: los dígitos hexadecimales de la parte fraccionaria de pi (P-array de 18\n");
        sb.append(" * palabras y cuatro S-boxes de 256). Archivo generado: no se edita a mano; lo produce\n");
        sb.append(" * {@code GeneradorConstantesBlowfish} (en las pruebas) y {@code ConstantesBlowfishTest} comprueba que coincide.\n");
        sb.append(" */\n");
        sb.append("final class ConstantesBlowfish {\n\n");
        sb.append("    private ConstantesBlowfish() {\n    }\n\n");
        sb.append("    static final int[] P = {\n");
        bloque(sb, pi, 0, PALABRAS_P);
        sb.append("    };\n");
        for (int s = 0; s < 4; s++) {
            sb.append("\n    static final int[] S").append(s).append(" = {\n");
            bloque(sb, pi, PALABRAS_P + 256 * s, 256);
            sb.append("    };\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static void bloque(StringBuilder sb, int[] palabras, int desde, int cuantas) {
        for (int i = 0; i < cuantas; i++) {
            sb.append(i % 4 == 0 ? "        " : " ");
            sb.append(String.format("0x%08X,", palabras[desde + i]));
            if (i % 4 == 3 || i == cuantas - 1) {
                sb.append('\n');
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Uso: GeneradorConstantesBlowfish <ruta de ConstantesBlowfish.java>");
        }
        Files.writeString(Path.of(args[0]), fuente(), StandardCharsets.UTF_8);
    }
}
