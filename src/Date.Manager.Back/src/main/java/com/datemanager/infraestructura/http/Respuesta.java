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
}
