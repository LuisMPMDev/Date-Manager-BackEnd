package com.datemanager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.datemanager.aplicacion.CodificadorClave;

/**
 * Codificador de pruebas: no es BCrypt ni es seguro. El «hash» es determinista y tiene la forma que exige el esquema
 * ({@code $2a$10$} más 53 caracteres alfanuméricos, 60 en total), para poder guardarlo en {@code usuario.clave_hash}.
 */
public final class CodificadorFalso implements CodificadorClave {

    /** El hash falso de {@code clave}. */
    public static String hash(String clave) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(clave.getBytes(StandardCharsets.UTF_8));
            return "$2a$10$" + HexFormat.of().formatHex(resumen).substring(0, 53);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String codificar(String clave) {
        return hash(clave);
    }

    @Override
    public boolean coincide(String clave, String hash) {
        return hash != null && hash.equals(hash(clave));
    }
}
