package com.datemanager.aplicacion;

/** Codifica claves para guardarlas (solo el hash) y comprueba una clave contra un hash guardado. */
public interface CodificadorClave {

    String codificar(String clave);

    boolean coincide(String clave, String hash);
}
