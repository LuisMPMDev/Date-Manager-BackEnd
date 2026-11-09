package com.datemanager.infraestructura;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Solo en pruebas (vive en {@code src/test}, no viaja en el jar): deja la base de pruebas en limpio y vuelve a
 * migrar al iniciar. Como {@code clean()} borra todo, antes comprueba que la conexión apunta a una base cuyo
 * nombre termina en «Test»; si no, se niega (el mismo login es dueño de la base real y de la de pruebas).
 */
@Configuration
@Profile("test")
public class LimpiezaBasePruebaConfig {

    private static final String SUFIJO = "test";

    @Bean
    public FlywayMigrationStrategy limpiarYMigrar() {
        return flyway -> {
            exigirBaseDePrueba(nombreDeLaBase(flyway.getConfiguration().getDataSource()));
            flyway.clean();
            flyway.migrate();
        };
    }

    /** Lanza {@link IllegalStateException} salvo que {@code nombre} termine en «Test» (sin distinguir mayúsculas). */
    static void exigirBaseDePrueba(String nombre) {
        if (nombre == null || !nombre.strip().toLowerCase(Locale.ROOT).endsWith(SUFIJO)) {
            throw new IllegalStateException("Flyway clean se rechaza: la base conectada es «" + nombre
                    + "» y su nombre no termina en «Test». Revisa DATEMANAGER_TEST_DB_URL: las pruebas "
                    + "solo pueden borrar una base de pruebas.");
        }
    }

    private static String nombreDeLaBase(DataSource origen) {
        try (Connection conexion = origen.getConnection();
                Statement consulta = conexion.createStatement();
                ResultSet filas = consulta.executeQuery("SELECT DB_NAME()")) {
            return filas.next() ? filas.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer el nombre de la base antes de limpiarla.", e);
        }
    }
}
