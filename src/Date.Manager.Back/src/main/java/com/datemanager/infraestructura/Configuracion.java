package com.datemanager.infraestructura;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Configuración de la aplicación, leída de las variables de entorno {@code DATEMANAGER_*}. Si falta o es inválida
 * una variable, {@link #desdeEntorno(Map)} lanza {@link IllegalStateException} con el nombre de la variable y nunca
 * con su valor.
 */
public record Configuracion(
        String dbUrl,
        String dbUsuario,
        String dbClave,
        int dbPool,
        String jwtSecreto,
        ZoneId zona,
        List<String> corsOrigenes,
        String adminUsuario,
        String adminClave,
        boolean dev,
        int puerto) {

    private static final int BYTES_MINIMOS_SECRETO = 32;

    public Configuracion {
        corsOrigenes = List.copyOf(corsOrigenes);
    }

    public static Configuracion desdeEntorno(Map<String, String> entorno) {
        String jwtSecreto = obligatoria(entorno, "DATEMANAGER_JWT_SECRETO");
        if (jwtSecreto.getBytes(StandardCharsets.UTF_8).length < BYTES_MINIMOS_SECRETO) {
            // Nunca se muestra el valor, solo qué falta.
            throw new IllegalStateException("La variable DATEMANAGER_JWT_SECRETO falta o tiene menos de "
                    + BYTES_MINIMOS_SECRETO + " bytes.");
        }
        return new Configuracion(
                obligatoria(entorno, "DATEMANAGER_DB_URL"),
                obligatoria(entorno, "DATEMANAGER_DB_USUARIO"),
                obligatoria(entorno, "DATEMANAGER_DB_CLAVE"),
                entero(entorno, "DATEMANAGER_DB_POOL", 10, 1, Integer.MAX_VALUE),
                jwtSecreto,
                zona(entorno),
                origenes(opcional(entorno, "DATEMANAGER_CORS_ORIGENES", "http://localhost:4200")),
                opcional(entorno, "DATEMANAGER_ADMIN_USUARIO", ""),
                opcional(entorno, "DATEMANAGER_ADMIN_CLAVE", ""),
                "dev".equals(opcional(entorno, "DATEMANAGER_PERFIL", "").trim()),
                entero(entorno, "DATEMANAGER_PUERTO", 8080, 1, 65535));
    }

    private static String obligatoria(Map<String, String> entorno, String nombre) {
        String valor = entorno.get(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la variable " + nombre + ".");
        }
        return valor;
    }

    private static String opcional(Map<String, String> entorno, String nombre, String porDefecto) {
        String valor = entorno.get(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }

    private static int entero(Map<String, String> entorno, String nombre, int porDefecto, int minimo, int maximo) {
        String texto = entorno.get(nombre);
        if (texto == null || texto.isBlank()) {
            return porDefecto;
        }
        try {
            int valor = Integer.parseInt(texto.trim());
            if (valor >= minimo && valor <= maximo) {
                return valor;
            }
        } catch (NumberFormatException e) {
            // Se informa abajo, sin el valor.
        }
        throw new IllegalStateException("La variable " + nombre + " debe ser un entero entre " + minimo + " y "
                + maximo + ".");
    }

    private static ZoneId zona(Map<String, String> entorno) {
        try {
            return ZoneId.of(opcional(entorno, "DATEMANAGER_ZONA_HORARIA", "America/Bogota").trim());
        } catch (DateTimeException e) {
            throw new IllegalStateException("La variable DATEMANAGER_ZONA_HORARIA no es una zona horaria válida.");
        }
    }

    private static List<String> origenes(String texto) {
        return Arrays.stream(texto.split(",")).map(String::trim).filter(origen -> !origen.isEmpty()).toList();
    }

    /** Sin secretos (ni la URL, que puede llevar credenciales): el texto del record puede acabar en un registro. */
    @Override
    public String toString() {
        return "Configuracion[dbUsuario=" + dbUsuario + ", dbPool=" + dbPool + ", zona=" + zona
                + ", corsOrigenes=" + corsOrigenes + ", adminUsuario=" + adminUsuario + ", dev=" + dev
                + ", puerto=" + puerto + "]";
    }
}
