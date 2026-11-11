package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.datemanager.infraestructura.Aplicacion;
import com.datemanager.infraestructura.Configuracion;
import com.datemanager.infraestructura.persistencia.Pool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El inicio del panel hace un número fijo de consultas, sin importar cuántos servicios, clientes o citas haya
 * (nada de una consulta por fila) y sin cargar toda la tabla de citas. En la versión Spring se contaban con las
 * estadísticas de Hibernate; aquí, con una aplicación propia sobre un pool cuyas conexiones cuentan cada sentencia
 * preparada (un controlador JDBC de prueba que envuelve al real).
 */
class InicioConsultasTest extends PruebaPanel {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 8, 0);
    private static final LocalDateTime AHORA_UTC = LocalDateTime.of(2026, 11, 9, 13, 0);
    private static final String PREFIJO = "jdbc:contador:";
    private static final AtomicLong SENTENCIAS = new AtomicLong();

    /** Acepta {@code jdbc:contador:<url real>} y devuelve conexiones que cuentan sus sentencias. */
    private static final class ControladorContador implements Driver {

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            Connection real = DriverManager.getConnection(url.substring(PREFIJO.length()), info);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] { Connection.class }, (proxy, metodo, args) -> {
                        String nombre = metodo.getName();
                        if (nombre.equals("prepareStatement") || nombre.equals("prepareCall")
                                || nombre.equals("createStatement")) {
                            SENTENCIAS.incrementAndGet();
                        }
                        try {
                            return metodo.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith(PREFIJO);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    private static final HttpClient CLIENTE = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private ControladorContador controlador;
    private Pool poolContador;
    private Aplicacion app;
    private UUID lector;
    private int contador;

    @BeforeEach
    void preparar() throws SQLException {
        fijarAhora(AHORA);
        lector = crearUsuario("lector", crearRol("Lector", "ver_panel"));
        controlador = new ControladorContador();
        DriverManager.registerDriver(controlador);
        poolContador = new Pool(PREFIJO + System.getenv("DATEMANAGER_TEST_DB_URL"),
                System.getenv("DATEMANAGER_TEST_DB_USUARIO"), System.getenv("DATEMANAGER_TEST_DB_CLAVE"), 2);
        Configuracion cfg = new Configuracion("jdbc:sqlserver://no-se-usa", "no-se-usa", "no-se-usa", 2, SECRETO,
                ZONA, List.of(ORIGEN_CORS), "", "", false, 0);
        app = new Aplicacion(cfg, poolContador, reloj);
        app.iniciar();
    }

    @AfterEach
    void cerrar() throws SQLException {
        app.detener();
        poolContador.cerrar();
        DriverManager.deregisterDriver(controlador);
    }

    private UUID servicio() {
        UUID id = UUID.randomUUID();
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, ?, 60, 1, ?)",
                id, "S" + contador++, new BigDecimal("1000"));
        for (int dia = 1; dia <= 7; dia++) {
            ejecutar("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                    + "VALUES (?, ?, ?, '08:00', '12:00')", id, UUID.randomUUID(), dia);
        }
        return id;
    }

    private UUID cliente() {
        UUID id = UUID.randomUUID();
        ejecutar("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, 'C', 'D', '1', ?)",
                id, id + "@x.co");
        return id;
    }

    /** Siembra {@code servicios} servicios, cada uno con citas en los próximos y últimos días, cada cita de un cliente. */
    private void sembrar(int servicios) {
        for (int s = 0; s < servicios; s++) {
            UUID servicio = servicio();
            for (int d = -20; d <= 5; d++) {
                String fecha = AHORA.toLocalDate().plusDays(d).toString();
                ejecutar("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, "
                        + "creada_en, cancelada_en) VALUES (?, ?, ?, ?, '09:00', '10:00', 'Confirmada', ?, NULL)",
                        UUID.randomUUID(), servicio, cliente(), fecha, AHORA_UTC.minusHours(d + 30));
            }
        }
    }

    private int pedirInicio() throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + app.puerto() + "/api/panel/inicio"))
                .header("Authorization", "Bearer " + tokenDe(lector)).GET().build();
        return CLIENTE.send(peticion, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private long sentenciasDeUnaPeticion() throws Exception {
        // La primera llamada calienta (conexiones del pool, etc.); se mide la segunda.
        assertEquals(200, pedirInicio());
        SENTENCIAS.set(0);
        assertEquals(200, pedirInicio());
        return SENTENCIAS.get();
    }

    @Test
    void elNumeroDeConsultasNoCreceConLosDatos() throws Exception {
        sembrar(1);
        long pocas = sentenciasDeUnaPeticion();
        sembrar(12);
        long muchas = sentenciasDeUnaPeticion();
        assertEquals(13 * 26, contar("cita"));
        assertEquals(13 * 26, contar("cliente"));
        assertTrue(pocas > 0);
        assertEquals(pocas, muchas);
    }
}
