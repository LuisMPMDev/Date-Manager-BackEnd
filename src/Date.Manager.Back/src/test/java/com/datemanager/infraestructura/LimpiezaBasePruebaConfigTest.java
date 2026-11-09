package com.datemanager.infraestructura;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** El guardián de {@code flyway.clean()}: solo una base cuyo nombre acabe en «Test» se puede borrar. No toca datos. */
class LimpiezaBasePruebaConfigTest {

    @Test
    void aceptaUnaBaseDePruebas() {
        assertThatCode(() -> LimpiezaBasePruebaConfig.exigirBaseDePrueba("DateManagerTest")).doesNotThrowAnyException();
        assertThatCode(() -> LimpiezaBasePruebaConfig.exigirBaseDePrueba("datemanagertest")).doesNotThrowAnyException();
    }

    @Test
    void rechazaLaBaseReal() {
        assertThatThrownBy(() -> LimpiezaBasePruebaConfig.exigirBaseDePrueba("DateManager"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DateManager")
                .hasMessageContaining("Test");
    }

    @Test
    void rechazaUnNombreAusenteOEnBlanco() {
        for (String nombre : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> LimpiezaBasePruebaConfig.exigirBaseDePrueba(nombre))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rechazaUnNombreQueSoloContieneTestEnElMedio() {
        assertThatThrownBy(() -> LimpiezaBasePruebaConfig.exigirBaseDePrueba("TestDateManager"))
                .isInstanceOf(IllegalStateException.class);
    }
}
