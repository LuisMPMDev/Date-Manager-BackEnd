package com.datemanager.infraestructura.http;

/**
 * El texto no es JSON válido o no tiene la forma esperada ({@link Json#parse}, {@link Json#leer}). El mensaje
 * describe el problema y su posición o campo, pero nunca reproduce el contenido recibido.
 */
public class JsonInvalido extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JsonInvalido(String mensaje) {
        super(mensaje);
    }
}
