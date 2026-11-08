package com.datemanager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de todas las pruebas de integración: arranca la aplicación con el perfil {@code test}
 * (base {@code DateManagerTest}), expone {@link MockMvc} y un reloj que cada prueba puede fijar.
 * Antes de cada prueba restablece el reloj y vacía las tablas de datos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PruebaIntegracion.RelojDePrueba.class)
public abstract class PruebaIntegracion {

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojDePrueba {

        /** Sustituye (por ser primario) al reloj del sistema de {@code RelojConfig}. */
        @Bean
        @Primary
        RelojMutable relojMutable(@Value("${datemanager.zona-horaria}") String zona) {
            return new RelojMutable(ZoneId.of(zona));
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected RelojMutable reloj;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void prepararPrueba() {
        reloj.restablecer();
        limpiarDatos();
    }

    /** Fija "ahora" a esa hora de pared en la zona del negocio (no en la de la JVM). */
    protected void fijarAhora(LocalDateTime ahora) {
        reloj.fijarAhora(ahora);
    }

    protected Clock reloj() {
        return reloj;
    }

    /**
     * Vacía todas las tablas de usuario (todas menos el historial de Flyway) sin depender de una lista
     * fija ni del orden de las claves foráneas: desactiva las restricciones, borra y las vuelve a validar.
     */
    protected void limpiarDatos() {
        List<String> tablas = jdbc.queryForList("""
                SELECT QUOTENAME(TABLE_SCHEMA) + '.' + QUOTENAME(TABLE_NAME)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_TYPE = 'BASE TABLE' AND TABLE_NAME <> 'flyway_schema_history'
                """, String.class);
        for (String tabla : tablas) {
            jdbc.execute("ALTER TABLE " + tabla + " NOCHECK CONSTRAINT ALL");
        }
        for (String tabla : tablas) {
            jdbc.execute("DELETE FROM " + tabla);
        }
        for (String tabla : tablas) {
            jdbc.execute("ALTER TABLE " + tabla + " WITH CHECK CHECK CONSTRAINT ALL");
        }
    }
}
