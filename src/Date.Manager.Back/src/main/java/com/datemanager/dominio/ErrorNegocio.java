package com.datemanager.dominio;

import java.util.Map;

/** Regla de negocio incumplida; el mensaje (en español) se muestra al usuario. */
public class ErrorNegocio extends RuntimeException {

    private final CodigoError codigo;
    private final Map<String, String> campos;

    public ErrorNegocio(CodigoError codigo, String mensaje) {
        this(codigo, mensaje, null);
    }

    /** Con {@code campos}: mensaje de error por cada campo inválido (solo para {@code VALIDACION}). */
    public ErrorNegocio(CodigoError codigo, String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.codigo = codigo;
        this.campos = campos == null ? null : Map.copyOf(campos);
    }

    public CodigoError codigo() {
        return codigo;
    }

    /** Campos inválidos con su mensaje, o {@code null} si el error no es de un campo concreto. */
    public Map<String, String> campos() {
        return campos;
    }
}
