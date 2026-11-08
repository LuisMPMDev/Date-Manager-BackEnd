package com.datemanager.infraestructura;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Solo en el perfil {@code test}: deja la base de pruebas en limpio y vuelve a migrar al iniciar. */
@Configuration
@Profile("test")
public class LimpiezaBasePruebaConfig {

    @Bean
    public FlywayMigrationStrategy limpiarYMigrar() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
