package com.datemanager.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorNegocioTest {

    @Test
    void losCamposConservanElOrdenDeInsercion() {
        Map<String, String> entrada = new LinkedHashMap<>();
        List<String> claves = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            claves.add("campo" + (19 - i));
            entrada.put("campo" + (19 - i), "mensaje " + i);
        }

        ErrorNegocio error = new ErrorNegocio(CodigoError.VALIDACION, "m", entrada);

        assertEquals(claves, new ArrayList<>(error.campos().keySet()));
        assertEquals(entrada, error.campos());
    }

    @Test
    void losCamposSonUnaCopiaInmutable() {
        Map<String, String> entrada = new LinkedHashMap<>();
        entrada.put("a", "x");
        ErrorNegocio error = new ErrorNegocio(CodigoError.VALIDACION, "m", entrada);

        entrada.put("b", "y");

        assertEquals(Map.of("a", "x"), error.campos());
        assertThrows(UnsupportedOperationException.class, () -> error.campos().put("c", "z"));
    }

    @Test
    void sinCamposSonNulos() {
        assertNull(new ErrorNegocio(CodigoError.CONFLICTO, "m").campos());
    }
}
