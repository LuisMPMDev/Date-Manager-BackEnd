package com.datemanager.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.infraestructura.http.ManejadorErrores;

/**
 * Validación de los cuerpos de petición, en lugar de las anotaciones de Bean Validation de la versión Spring: acumula
 * un mensaje por campo (el primero que falla gana) y {@link #lanzarSiHay()} los devuelve todos juntos como un
 * {@code VALIDACION} «Hay datos inválidos en la petición.» con {@code campos}. Los campos anidados llevan el prefijo
 * del padre ({@code cliente.nombre}), como en Spring.
 */
final class Validacion {

    private final Map<String, String> campos;
    private final String prefijo;

    Validacion() {
        this(new LinkedHashMap<>(), "");
    }

    private Validacion(Map<String, String> campos, String prefijo) {
        this.campos = campos;
        this.prefijo = prefijo;
    }

    /** Validación de un objeto anidado: sus errores van a este mismo resultado con el prefijo {@code campo.}. */
    Validacion anidada(String campo) {
        return new Validacion(campos, prefijo + campo + ".");
    }

    /** {@code @NotNull}. */
    Validacion noNulo(String campo, Object valor, String mensaje) {
        if (valor == null) {
            error(campo, mensaje);
        }
        return this;
    }

    /** {@code @NotBlank}: ni nulo ni solo espacios (como Hibernate Validator, recorta con {@code trim}). */
    Validacion noEnBlanco(String campo, String valor, String mensaje) {
        if (valor == null || valor.trim().isEmpty()) {
            error(campo, mensaje);
        }
        return this;
    }

    /** {@code @NotEmpty}: ni nulo ni vacío (los espacios cuentan). */
    Validacion noVacio(String campo, String valor, String mensaje) {
        if (valor == null || valor.isEmpty()) {
            error(campo, mensaje);
        }
        return this;
    }

    /** {@code @Size(max)}: nulo vale; cuenta unidades UTF-16, como {@code String.length()}. */
    Validacion largoMaximo(String campo, String valor, int maximo, String mensaje) {
        if (valor != null && valor.length() > maximo) {
            error(campo, mensaje);
        }
        return this;
    }

    void lanzarSiHay() {
        if (!campos.isEmpty()) {
            throw new ErrorNegocio(CodigoError.VALIDACION, ManejadorErrores.MENSAJE_DATOS_INVALIDOS, campos);
        }
    }

    private void error(String campo, String mensaje) {
        campos.putIfAbsent(prefijo + campo, mensaje);
    }
}
