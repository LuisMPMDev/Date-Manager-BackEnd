package com.datemanager.infraestructura.http;

/** Lo que hace una ruta: recibe la solicitud ya enrutada y autenticada y devuelve la respuesta. */
@FunctionalInterface
public interface Manejador {

    /** Cualquier excepción se traduce con {@link ManejadorErrores}; no hace falta capturarla. */
    Respuesta atender(Solicitud solicitud) throws Exception;
}
