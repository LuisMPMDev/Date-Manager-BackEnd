package com.datemanager.infraestructura.seguridad;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ConstantesBlowfishTest {

    @Test
    void lasCuatroPalabrasConocidasDePiCoinciden() {
        // Valores publicados de Blowfish; sirven solo para contrastar, las constantes se calculan.
        assertEquals(18, ConstantesBlowfish.P.length);
        assertEquals(256, ConstantesBlowfish.S0.length);
        assertEquals(256, ConstantesBlowfish.S1.length);
        assertEquals(256, ConstantesBlowfish.S2.length);
        assertEquals(256, ConstantesBlowfish.S3.length);
        assertEquals(0x243F6A88, ConstantesBlowfish.P[0]);
        assertEquals(0x8979FB1B, ConstantesBlowfish.P[17]);
        assertEquals(0xD1310BA6, ConstantesBlowfish.S0[0]);
        assertEquals(0x3AC372E6, ConstantesBlowfish.S3[255]);
    }

    @Test
    void lasConstantesSonExactamenteLosDigitosDePiQueCalculaElGenerador() {
        int[] pi = GeneradorConstantesBlowfish.palabrasDePi(
                GeneradorConstantesBlowfish.PALABRAS_P + GeneradorConstantesBlowfish.PALABRAS_S);
        int[][] cajas = {ConstantesBlowfish.S0, ConstantesBlowfish.S1, ConstantesBlowfish.S2, ConstantesBlowfish.S3};

        assertArrayEquals(Arrays.copyOfRange(pi, 0, 18), ConstantesBlowfish.P);
        for (int s = 0; s < 4; s++) {
            int desde = 18 + 256 * s;
            assertArrayEquals(Arrays.copyOfRange(pi, desde, desde + 256), cajas[s], "S" + s);
        }
    }

    @Test
    void elArchivoGeneradoNoEstaDesactualizado() throws IOException {
        Path fuente = Path.of("src/main/java/com/datemanager/infraestructura/seguridad/ConstantesBlowfish.java");
        // Git puede haber convertido los saltos de línea al sacar el archivo.
        assertEquals(GeneradorConstantesBlowfish.fuente(), Files.readString(fuente).replace("\r\n", "\n"));
    }
}
