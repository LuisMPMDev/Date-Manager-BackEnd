package com.datemanager.dominio;

/** Regla de negocio incumplida; el mensaje (en español) se muestra al usuario. */
public class ErrorNegocio extends RuntimeException {

    private final CodigoError codigo;

    public ErrorNegocio(CodigoError codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public CodigoError codigo() {
        return codigo;
    }
}
