package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * El inicio del panel hace un número fijo de consultas, sin importar cuántos servicios, clientes o citas haya
 * (nada de una consulta por fila) y sin cargar toda la tabla de citas.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class InicioConsultasTest extends PruebaPanel {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 8, 0);
    private static final LocalDateTime AHORA_UTC = LocalDateTime.of(2026, 11, 9, 13, 0);

    @Autowired
    private EntityManagerFactory fabrica;

    private UUID lector;
    private int contador;

    @BeforeEach
    void preparar() {
        fijarAhora(AHORA);
        lector = crearUsuario("lector", crearRol("Lector", "ver_panel"));
    }

    private UUID servicio() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, ?, 60, 1, ?)",
                id, "S" + contador++, new BigDecimal("1000"));
        for (int dia = 1; dia <= 7; dia++) {
            jdbc.update("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                    + "VALUES (?, ?, ?, '08:00', '12:00')", id, UUID.randomUUID(), dia);
        }
        return id;
    }

    private UUID cliente() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, 'C', 'D', '1', ?)",
                id, id + "@x.co");
        return id;
    }

    /** Siembra {@code servicios} servicios, cada uno con citas en los próximos y últimos días, cada cita de un cliente. */
    private void sembrar(int servicios) {
        for (int s = 0; s < servicios; s++) {
            UUID servicio = servicio();
            for (int d = -20; d <= 5; d++) {
                String fecha = AHORA.toLocalDate().plusDays(d).toString();
                jdbc.update("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, "
                        + "creada_en, cancelada_en) VALUES (?, ?, ?, ?, '09:00', '10:00', 'Confirmada', ?, NULL)",
                        UUID.randomUUID(), servicio, cliente(), fecha, AHORA_UTC.minusHours(d + 30));
            }
        }
    }

    private long sentenciasDeUnaPeticion() throws Exception {
        Statistics estadisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        // La primera llamada calienta (permisos del token, etc.); se mide la segunda.
        obtener("/api/panel/inicio", lector).andExpect(status().isOk());
        estadisticas.clear();
        obtener("/api/panel/inicio", lector).andExpect(status().isOk());
        return estadisticas.getPrepareStatementCount();
    }

    @Test
    void elNumeroDeConsultasNoCreceConLosDatos() throws Exception {
        sembrar(1);
        long pocas = sentenciasDeUnaPeticion();
        sembrar(12);
        long muchas = sentenciasDeUnaPeticion();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cita", Integer.class)).isEqualTo(13 * 26);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cliente", Integer.class)).isEqualTo(13 * 26);
        assertThat(pocas).isPositive();
        assertThat(muchas).isEqualTo(pocas);
    }
}
