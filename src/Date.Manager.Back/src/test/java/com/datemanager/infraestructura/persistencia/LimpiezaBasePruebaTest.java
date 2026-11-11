package com.datemanager.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** El guardián de {@code Migrador.limpiar()}: solo una base cuyo nombre acabe en «Test» se puede borrar. No toca datos. */
class LimpiezaBasePruebaTest {

    @Test
    void aceptaUnaBaseDePruebas() {
        assertDoesNotThrow(() -> Migrador.exigirBaseDePrueba("DateManagerTest"));
        assertDoesNotThrow(() -> Migrador.exigirBaseDePrueba("datemanagertest"));
    }

    @Test
    void rechazaLaBaseReal() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Migrador.exigirBaseDePrueba("DateManager"));

        assertTrue(e.getMessage().contains("DateManager"));
        assertTrue(e.getMessage().contains("Test"));
    }

    @Test
    void rechazaUnNombreAusenteOEnBlanco() {
        for (String nombre : new String[] {null, "", "   "}) {
            assertThrows(IllegalStateException.class, () -> Migrador.exigirBaseDePrueba(nombre));
        }
    }

    @Test
    void rechazaUnNombreQueSoloContieneTestEnElMedio() {
        assertThrows(IllegalStateException.class, () -> Migrador.exigirBaseDePrueba("TestDateManager"));
    }
}
