package com.datemanager.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import com.datemanager.PruebaIntegracion;
import com.datemanager.infraestructura.persistencia.BloqueoAdministracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class BloqueoAdministracionTest extends PruebaIntegracion {

    @Autowired
    private BloqueoAdministracion bloqueo;

    @Autowired
    private PlatformTransactionManager transacciones;

    /**
     * El driver confirma con un único {@code IF @@TRANCOUNT > 0 COMMIT TRAN}. Si el bloqueo abriera una
     * transacción anidada (contador 2), ese commit no confirmaría nada y el bloqueo global seguiría tomado.
     */
    @Test
    void elBloqueoNoAnidaLaTransaccionYUnSoloCommitLaCierra() {
        Integer niveles = new TransactionTemplate(transacciones).execute(estado -> {
            bloqueo.bloquear();
            return jdbc.queryForObject("SELECT @@TRANCOUNT", Integer.class);
        });

        assertThat(niveles).isEqualTo(1);
    }

    @Test
    void llamarlaDosVecesEnLaMismaTransaccionNoAnidaNiSeBloqueaASiMisma() {
        Integer niveles = new TransactionTemplate(transacciones).execute(estado -> {
            bloqueo.bloquear();
            bloqueo.bloquear();
            return jdbc.queryForObject("SELECT @@TRANCOUNT", Integer.class);
        });

        assertThat(niveles).isEqualTo(1);
    }
}
