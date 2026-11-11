package com.datemanager.infraestructura.http;

/**
 * Respuesta de un {@link Manejador}. El {@code cuerpo} se serializa con {@link Json#escribir}; {@code null} no
 * lleva cuerpo.
 */
public record Respuesta(int estado, Object cuerpo) {

    public static Respuesta ok(Object cuerpo) {
        return new Respuesta(200, cuerpo);
    }

    public static Respuesta creada(Object cuerpo) {
        return new Respuesta(201, cuerpo);
    }

    public static Respuesta sinContenido() {
        return new Respuesta(204, null);
    }

    /** Un 200 con esos bytes tal cual (p. ej. una página HTML), sin pasar por JSON. */
    public static Respuesta contenido(String tipo, byte[] bytes) {
        return new Respuesta(200, new Contenido(tipo, bytes));
    }

    /** Cuerpo que no es JSON: se envía tal cual con ese {@code Content-Type}. */
    public record Contenido(String tipo, byte[] bytes) {
    }
}
