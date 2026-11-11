package com.datemanager.infraestructura.http;

/**
 * Cómo se documenta una ruta en OpenAPI.
 *
 * @param etiqueta grupo de la ruta (p. ej. {@code "Citas"})
 * @param resumen frase corta de lo que hace
 * @param cuerpo {@code record} del cuerpo de la petición, o {@code null} si no lleva
 * @param respuesta {@code record} de la respuesta, o {@code null} si no devuelve cuerpo
 * @param lista {@code true} si la respuesta es una lista de {@code respuesta}
 * @param estadoOk estado HTTP de éxito (200, 201, 204...)
 */
public record DocumentoRuta(String etiqueta, String resumen, Class<?> cuerpo, Class<?> respuesta, boolean lista,
        int estadoOk) {
}
