package com.datemanager.infraestructura.persistencia;

import com.datemanager.dominio.Permiso;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Guarda cada permiso con su id snake_case; un valor desconocido en la base es un error interno. */
@Converter
public class PermisoConvertidor implements AttributeConverter<Permiso, String> {

    @Override
    public String convertToDatabaseColumn(Permiso permiso) {
        return permiso == null ? null : permiso.id();
    }

    @Override
    public Permiso convertToEntityAttribute(String id) {
        if (id == null) {
            return null;
        }
        for (Permiso permiso : Permiso.values()) {
            if (permiso.id().equals(id)) {
                return permiso;
            }
        }
        throw new IllegalStateException("Permiso desconocido guardado en la base: " + id);
    }
}
