package com.datemanager.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * {@code db/migration/indice.txt} es la lista a mano de las migraciones (un jar no se puede listar). Esta prueba
 * falla si se añade un {@code V*.sql} sin ponerlo en el índice, o al revés, así no hay migración que se quede fuera.
 */
class IndiceMigracionesTest {

    private static final Path CARPETA = Path.of("src", "main", "resources", "db", "migration");

    @Test
    void elIndiceCoincideConLosArchivosDeLaCarpeta() throws IOException {
        List<String> enDisco;
        try (Stream<Path> archivos = Files.list(CARPETA)) {
            enDisco = archivos.map(p -> p.getFileName().toString())
                    .filter(nombre -> nombre.startsWith("V") && nombre.endsWith(".sql")).sorted().toList();
        }

        List<String> enIndice = Migrador.leerIndice(Files.readString(CARPETA.resolve("indice.txt")))
                .stream().sorted().toList();

        assertFalse(enDisco.isEmpty());
        assertEquals(enDisco, enIndice, "indice.txt y los V*.sql de " + CARPETA + " deben coincidir");
    }

    @Test
    void cadaArchivoDelIndiceEstaEnElClasspathYSeLeeComoUtf8() throws IOException {
        List<String> nombres = Migrador.leerIndice(Files.readString(CARPETA.resolve("indice.txt")));

        for (String nombre : nombres) {
            try (InputStream in = IndiceMigracionesTest.class.getClassLoader()
                    .getResourceAsStream("db/migration/" + nombre)) {
                assertNotNull(in, nombre + " debe estar en el classpath");
                String contenido = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(contenido.contains("�"), nombre + " no es UTF-8 válido");
            }
        }
    }

    @Test
    void leerIndiceIgnoraComentariosYLineasEnBlanco() {
        assertEquals(List.of("V1__a.sql", "V2__b.sql"),
                Migrador.leerIndice("# comentario\n\n  V1__a.sql  \r\nV2__b.sql\n\n"));
    }

    @Test
    void elNombreDeUnaMigracionSeInterpretaComoVersionYDescripcion() {
        Migrador.NombreMigracion n = Migrador.NombreMigracion.de("V10__disparador_citas_sin_bloqueos_de_mas.sql");

        assertEquals(10, n.version());
        assertEquals("disparador citas sin bloqueos de mas", n.descripcion());
        assertTrue(Migrador.NombreMigracion.de("V2__x.sql").version() < n.version());
    }

    @Test
    void unNombreQueNoEsUnaMigracionSeRechaza() {
        for (String malo : new String[] {"X1__a.sql", "V__a.sql", "V1_a.sql", "V1__a.txt", "Vuno__a.sql", "V1__.sql"}) {
            try {
                Migrador.NombreMigracion.de(malo);
                throw new AssertionError("debía rechazar " + malo);
            } catch (IllegalStateException esperado) {
                assertTrue(esperado.getMessage().contains(malo));
            }
        }
    }
}
