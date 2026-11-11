package com.datemanager.dominio;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        // Copia inmutable que conserva el orden de inserción (Map.copyOf lo desordena): el primer campo es el del mensaje.
        this.campos = campos == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(campos));
    }

    public CodigoError codigo() {
        return codigo;
    }

    /** Campos inválidos con su mensaje, o {@code null} si el error no es de un campo concreto. */
    public Map<String, String> campos() {
        return campos;
    }
}
