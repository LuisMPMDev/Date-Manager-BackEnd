package com.datemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * {@link Main} en un proceso hijo (con las clases compiladas y el driver de SQL Server en el classpath): sin la
 * configuración obligatoria termina con código 1, un mensaje claro y sin mostrar ningún valor secreto.
 */
class MainTest {

    private static final String SECRETO_CORTO = "secreto-corto-NO-MOSTRAR";
    private static final String CLAVE_BASE = "clave-de-base-NO-MOSTRAR";

    /** Lo que hizo el proceso: código de salida y toda su salida (estándar y de errores juntas). */
    private record Resultado(int codigo, String salida) {
    }

    private static String ubicacion(Class<?> clase) throws URISyntaxException {
        return Path.of(clase.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }

    private static Resultado ejecutar(Map<String, String> variables) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = ubicacion(Main.class) + File.pathSeparator
                + ubicacion(Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver"));
        ProcessBuilder proceso = new ProcessBuilder(java, "-cp", classpath, Main.class.getName())
                .redirectErrorStream(true);
        // Nada del entorno real (que puede tener una configuración válida): solo las variables de la prueba.
        proceso.environment().keySet().removeIf(nombre -> nombre.startsWith("DATEMANAGER_"));
        proceso.environment().putAll(variables);
        Process hijo = proceso.start();
        hijo.getOutputStream().close();
        byte[] bytes;
        try {
            bytes = hijo.getInputStream().readAllBytes();
            if (!hijo.waitFor(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Main no terminó a tiempo.");
            }
        } catch (IOException | InterruptedException e) {
            hijo.destroyForcibly();
            throw e;
        } finally {
            if (hijo.isAlive()) {
                hijo.destroyForcibly();
            }
        }
        return new Resultado(hijo.exitValue(), new String(bytes, Charset.defaultCharset()));
    }

    private static Map<String, String> validasMenos(String... quitar) {
        Map<String, String> variables = new java.util.HashMap<>(Map.of(
                "DATEMANAGER_DB_URL", "jdbc:sqlserver://127.0.0.1:1;databaseName=NoExiste",
                "DATEMANAGER_DB_USUARIO", "usuario-de-prueba",
                "DATEMANAGER_DB_CLAVE", CLAVE_BASE,
                "DATEMANAGER_JWT_SECRETO", "secreto-falso-solo-para-pruebas-0123456789abcdef"));
        List.of(quitar).forEach(variables::remove);
        return variables;
    }

    @Test
    void conUnSecretoCortoTerminaConCodigo1SinMostrarElSecreto() throws Exception {
        Map<String, String> variables = validasMenos();
        variables.put("DATEMANAGER_JWT_SECRETO", SECRETO_CORTO);

        Resultado r = ejecutar(variables);

        assertEquals(1, r.codigo(), r.salida());
        assertTrue(r.salida().contains("DATEMANAGER_JWT_SECRETO"), r.salida());
        assertTrue(r.salida().contains("32 bytes"), r.salida());
        assertFalse(r.salida().contains(SECRETO_CORTO), r.salida());
        assertFalse(r.salida().contains(CLAVE_BASE), r.salida());
    }

    @Test
    void sinUnaVariableObligatoriaTerminaConCodigo1YLaNombra() throws Exception {
        Resultado r = ejecutar(validasMenos("DATEMANAGER_DB_URL"));

        assertEquals(1, r.codigo(), r.salida());
        assertTrue(r.salida().contains("Falta la variable DATEMANAGER_DB_URL."), r.salida());
        assertFalse(r.salida().contains(CLAVE_BASE), r.salida());
    }

    @Test
    void siLaBaseNoRespondeTerminaConCodigo1SinMostrarSecretos() throws Exception {
        Resultado r = ejecutar(validasMenos());

        assertEquals(1, r.codigo(), r.salida());
        assertTrue(r.salida().contains("No se pudo arrancar Date Manager API."), r.salida());
        assertFalse(r.salida().contains(CLAVE_BASE), r.salida());
        assertFalse(r.salida().contains("secreto-falso-solo-para-pruebas"), r.salida());
    }
}
