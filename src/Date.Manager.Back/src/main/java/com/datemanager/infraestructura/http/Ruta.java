package com.datemanager.infraestructura.http;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.datemanager.infraestructura.seguridad.Autenticador;

/**
 * Una operación de la API. {@code patron} usa segmentos literales y {@code {nombre}} (un segmento completo, no
 * vacío); {@code HEAD} y {@code OPTIONS} no se declaran: el servidor los resuelve.
 *
 * @param permisosAlgunoDe ids del catálogo {@code Permiso} (uno desconocido es un error al crear la ruta); basta con
 *     tener uno; vacío = solo exige sesión (ignorado si la ruta es pública)
 * @param publica no exige sesión y ni siquiera lee la cabecera {@code Authorization}
 * @param doc para OpenAPI; puede ser {@code null}
 */
public record Ruta(String metodo, String patron, Set<String> permisosAlgunoDe, boolean publica,
        Manejador manejador, DocumentoRuta doc) {

    private static final Set<String> METODOS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    public Ruta {
        if (metodo == null || !METODOS.contains(metodo)) {
            throw new IllegalArgumentException("Método no soportado en una ruta: " + metodo);
        }
        if (manejador == null) {
            throw new IllegalArgumentException("Falta el manejador de " + metodo + " " + patron);
        }
        permisosAlgunoDe = permisosAlgunoDe == null ? Set.of() : Set.copyOf(permisosAlgunoDe);
        if (publica && !permisosAlgunoDe.isEmpty()) {
            throw new IllegalArgumentException("Una ruta pública no puede exigir permisos: " + patron);
        }
        try {
            Autenticador.validarCatalogo(permisosAlgunoDe);
        } catch (IllegalArgumentException e) {
            // Un error de quien declara la ruta: que falle al registrarla, no en cada petición.
            throw new IllegalArgumentException(e.getMessage() + " (en " + metodo + " " + patron + ")", e);
        }
        segmentos(patron); // valida el patrón
    }

    /** Segmentos del patrón ({@code "/a/{id}"} da {@code ["a", "{id}"]}); lanza si está mal formado. */
    static List<String> segmentos(String patron) {
        if (patron == null || !patron.startsWith("/")) {
            throw new IllegalArgumentException("El patrón debe empezar por '/': " + patron);
        }
        List<String> segmentos = new ArrayList<>(List.of(patron.substring(1).split("/", -1)));
        Set<String> nombres = new HashSet<>();
        for (String segmento : segmentos) {
            if (esParametro(segmento)) {
                String nombre = segmento.substring(1, segmento.length() - 1);
                if (nombre.isEmpty() || nombre.contains("{") || nombre.contains("}") || !nombres.add(nombre)) {
                    throw new IllegalArgumentException("Parámetro inválido o repetido en " + patron);
                }
            } else if (segmento.contains("{") || segmento.contains("}")) {
                throw new IllegalArgumentException("Llave fuera de lugar en " + patron);
            }
        }
        if (segmentos.size() > 1 && segmentos.contains("")) {
            throw new IllegalArgumentException("Segmento vacío en " + patron);
        }
        return segmentos;
    }

    static boolean esParametro(String segmento) {
        return segmento.length() >= 2 && segmento.startsWith("{") && segmento.endsWith("}");
    }
}
