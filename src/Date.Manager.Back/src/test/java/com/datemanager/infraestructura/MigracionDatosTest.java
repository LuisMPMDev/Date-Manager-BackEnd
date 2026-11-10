package com.datemanager.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * La migración sobre una base que ya tiene datos (V7, V8 y V9): con datos válidos termina bien y no cambia ninguna
 * fila; con datos que violan una regla aborta con un mensaje legible, deja la base exactamente como estaba (misma
 * versión de Flyway, mismas restricciones, índices y disparadores, mismas filas) y no corrige ningún dato.
 *
 * <p>No usa el contexto de Spring: maneja Flyway a mano sobre la misma base de pruebas ({@code DateManagerTest},
 * se niega a tocar otra) y, al terminar la clase, la deja en la última versión para no afectar a las demás pruebas.
 */
class MigracionDatosTest {

    private static final String V6 = "6";
    private static final String V7 = "7";
    private static final String V8 = "8";
    private static final String HASH = new BCryptPasswordEncoder(4).encode("demo123");
    private static final DriverManagerDataSource FUENTE = new DriverManagerDataSource(
            System.getenv("DATEMANAGER_TEST_DB_URL"),
            System.getenv("DATEMANAGER_TEST_DB_USUARIO"),
            System.getenv("DATEMANAGER_TEST_DB_CLAVE"));
    private static final JdbcTemplate JDBC = new JdbcTemplate(FUENTE);

    /** Identificadores de las filas «padre» válidas que crea {@link #baseValida}. */
    private record Base(UUID rol, UUID usuario, UUID servicio, UUID cliente) {
    }

    /**
     * Deja la base en la última versión para las demás pruebas. Pasa por la guarda de {@link #limpiar}; si algo falla
     * lo dice con un mensaje propio (el fallo se suma al resultado de las pruebas, no lo oculta) y, si la guarda se
     * niega, no se migra nada: nunca se aplican migraciones a una base que no es de pruebas.
     */
    @AfterAll
    static void restaurarEsquemaMasReciente() {
        Flyway flyway = flyway(null);
        try {
            limpiar(flyway);
            flyway.migrate();
        } catch (RuntimeException e) {
            throw new IllegalStateException("No se pudo restaurar el esquema más reciente de la base de pruebas; "
                    + "las demás pruebas pueden fallar hasta que se vuelva a migrar.", e);
        }
    }

    @Test
    void laGuardaSeNiegaAUnaBaseSinElSufijoTestYNoLimpiaNada() {
        AtomicBoolean limpio = new AtomicBoolean();

        Throwable error = catchThrowable(() -> limpiarSiEsBaseDePrueba("DateManager", () -> limpio.set(true)));

        assertThat(error).isInstanceOf(IllegalStateException.class).hasMessageContaining("DateManager");
        assertThat(limpio).as("no se llegó a limpiar").isFalse();
        limpiarSiEsBaseDePrueba("DateManagerTest", () -> limpio.set(true));
        assertThat(limpio).as("con el sufijo Test sí se limpia").isTrue();
    }

    // ---------------------------------------------------------------- Migración con datos válidos

    @Test
    void conDatosValidosMigraALaUltimaVersionSinTocarNingunaFila() {
        Flyway flyway = prepararHasta(V6);
        UUID servicio = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
        insertarDatosValidos(servicio, cliente);
        String datosAntes = instantaneaDeDatos();
        String esquemaAntes = instantaneaDeEsquema();

        flyway.migrate();

        assertThat(versionActual(flyway)).isEqualTo(ultimaVersion(flyway));
        assertThat(instantaneaDeDatos()).as("las filas no cambian al migrar").isEqualTo(datosAntes);
        assertThat(instantaneaDeEsquema()).as("la migración sí añade reglas").isNotEqualTo(esquemaAntes);
        assertThat(existe("sys.triggers", "tr_cita_sin_solape")).isTrue();
        assertThat(existe("sys.triggers", "tr_ventana_sin_cruce")).isTrue();
        assertThat(existe("sys.check_constraints", "ck_cliente_nombre_no_vacio")).isTrue();

        // Las reglas ya rigen sobre la base migrada: una cita solapada y un cliente sin nombre se rechazan.
        Throwable solape = catchThrowable(() -> insertarCita(servicio, cliente, "10:30", "11:30", "Confirmada"));
        assertThat(codigoSql(solape)).as("disparador de citas solapadas").isEqualTo(50010);
        Throwable sinNombre = catchThrowable(() -> JDBC.update("INSERT INTO cliente (id, nombre, apellido, telefono, "
                + "correo) VALUES (?, '  ', 'Gómez', '300', 'x@x.co')", UUID.randomUUID()));
        assertThat(sinNombre).hasMessageContaining("ck_cliente_nombre_no_vacio");
        assertThat(instantaneaDeDatos()).as("los rechazos no dejan filas").isEqualTo(datosAntes);
    }

    // ---------------------------------------------------------------- Aborto de V7 (una prueba por regla)

    static Stream<Arguments> datosQueViolanV7() {
        return Stream.of(
                caso("ck_cliente_nombre_no_vacio", (b) -> insertarCliente("  ", "Gómez", "300", "x@x.co")),
                caso("ck_cliente_apellido_no_vacio", (b) -> insertarCliente("Ana", "", "300", "otra@x.co")),
                caso("ck_cliente_telefono_no_vacio", (b) -> insertarCliente("Ana", "Pérez", "  ", "otra@x.co")),
                caso("ck_cliente_correo_forma", (b) -> insertarCliente("Ana", "Pérez", "300", "Otra@X.co")),
                caso("ck_cliente_correo_forma", (b) -> insertarCliente("Ana", "Pérez", "300", "otra@@x.co")),
                caso("ck_cita_cancelada_coherente", (b) -> JDBC.update("INSERT INTO cita (id, servicio_id, cliente_id, "
                        + "fecha, hora_inicio, hora_fin, estado, creada_en, cancelada_en) VALUES (?, ?, ?, "
                        + "'2030-01-07', '10:00', '11:00', 'Cancelada', '2030-01-01 09:00:00', NULL)",
                        UUID.randomUUID(), b.servicio(), b.cliente())),
                caso("ck_cita_cancelada_posterior", (b) -> JDBC.update("INSERT INTO cita (id, servicio_id, cliente_id, "
                        + "fecha, hora_inicio, hora_fin, estado, creada_en, cancelada_en) VALUES (?, ?, ?, "
                        + "'2030-01-07', '10:00', '11:00', 'Cancelada', '2030-01-01 09:00:00', "
                        + "'2030-01-01 08:00:00')", UUID.randomUUID(), b.servicio(), b.cliente())),
                caso("ck_usuario_clave_hash_bcrypt", (b) -> insertarUsuario(b.rol(), "luis", "texto plano")),
                caso("ck_usuario_usuario_forma", (b) -> insertarUsuario(b.rol(), "Luis", HASH)),
                caso("ck_usuario_nombre_no_vacio", (b) -> insertarUsuario(UUID.randomUUID(), b.rol(), "  ",
                        "luis@x.co", "luis", HASH)),
                caso("ck_usuario_correo_forma", (b) -> insertarUsuario(UUID.randomUUID(), b.rol(), "Luis",
                        "luis@@x.co", "luis", HASH)),
                caso("ck_servicio_nombre_no_vacio", (b) -> JDBC.update("INSERT INTO servicio (id, nombre, "
                        + "duracion_min, activo, valor) VALUES (?, '  ', 30, 1, 0)", UUID.randomUUID())),
                caso("ck_rol_nombre_no_vacio", (b) -> JDBC.update("INSERT INTO rol (id, nombre, descripcion) "
                        + "VALUES (?, '  ', 'Sin nombre')", UUID.randomUUID())),
                caso("ck_servicio_duracion_max", (b) -> JDBC.update("INSERT INTO servicio (id, nombre, duracion_min, "
                        + "activo, valor) VALUES (?, 'Largo', 1441, 1, 0)", UUID.randomUUID())),
                caso("ck_enlace_invitacion_desactivado", (b) -> JDBC.update("INSERT INTO enlace_invitacion (id, "
                        + "codigo, creado_por, creado_en, vence_en, desactivado_en) VALUES (?, 'codigo-invalido', ?, "
                        + "'2030-01-01 10:00:00', '2030-02-01 10:00:00', '2030-01-01 09:00:00')",
                        UUID.randomUUID(), b.usuario())));
    }

    private static Arguments caso(String regla, Consumer<Base> filaInvalida) {
        return Arguments.of(regla, filaInvalida);
    }

    @ParameterizedTest(name = "V7 aborta por {0}")
    @MethodSource("datosQueViolanV7")
    void v7AbortaLimpioSiUnaFilaViolaUnaRegla(String regla, Consumer<Base> filaInvalida) {
        Flyway flyway = prepararHasta(V6);
        filaInvalida.accept(baseValida());

        abortaSinTocarNada(flyway, V6, "Migración V7 abortada", regla);
        assertThat(existe("sys.check_constraints", regla)).as("la restricción no se creó a medias").isFalse();
    }

    // ---------------------------------------------------------------- Aborto de V8 y de V9

    @Test
    void v8AbortaLimpioSiYaHayCitasSolapadas() {
        Flyway flyway = prepararHasta(V6);
        Base base = baseValida();
        // Válidas para V7 (una no cancelada cada una, sin cancelada_en) pero solapadas entre sí.
        insertarCita(base.servicio(), base.cliente(), "10:00", "11:00", "Confirmada");
        insertarCita(base.servicio(), base.cliente(), "10:30", "11:30", "Atendida");
        flyway(V7).migrate();

        abortaSinTocarNada(flyway, V7, "Migración V8 abortada", "citas solapadas");
        assertThat(existe("sys.triggers", "tr_cita_sin_solape")).isFalse();
        assertThat(existe("sys.indexes", "ix_cita_servicio_ocupada")).isFalse();
    }

    @Test
    void v9AbortaLimpioSiYaHayVentanasCruzadas() {
        Flyway flyway = prepararHasta(V6);
        Base base = baseValida();
        insertarVentana(base.servicio(), 1, "09:00", "12:00");
        insertarVentana(base.servicio(), 1, "11:00", "14:00");
        flyway(V8).migrate();

        abortaSinTocarNada(flyway, V8, "Migración V9 abortada", "ventanas cruzadas");
        assertThat(existe("sys.triggers", "tr_ventana_sin_cruce")).isFalse();
    }

    /**
     * Migra hasta la última versión esperando que falle con un mensaje que nombra el motivo, y comprueba que la base
     * quedó exactamente como estaba antes: misma versión, sin intento fallido en el historial, mismo esquema y
     * mismas filas.
     */
    private void abortaSinTocarNada(Flyway flyway, String versionPrevia, String aborto, String motivo) {
        assertThat(versionActual(flyway)).isEqualTo(versionPrevia);
        String datosAntes = instantaneaDeDatos();
        String esquemaAntes = instantaneaDeEsquema();

        Throwable error = catchThrowable(flyway::migrate);

        assertThat(error).as("la migración debía abortar").isInstanceOf(FlywayException.class);
        assertThat(error.getMessage()).contains(aborto, motivo, "No se modificó ningún dato");
        assertThat(codigoSql(error)).as("THROW 50001 de la auditoría").isEqualTo(50001);
        assertThat(versionActual(flyway)).as("la base sigue en su versión").isEqualTo(versionPrevia);
        assertThat(JDBC.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0", Integer.class))
                .as("el intento fallido no deja registro").isZero();
        assertThat(instantaneaDeEsquema()).as("sin restricciones, índices ni disparadores a medias")
                .isEqualTo(esquemaAntes);
        assertThat(instantaneaDeDatos()).as("la auditoría no corrige ni borra datos").isEqualTo(datosAntes);
    }

    // ---------------------------------------------------------------- Apoyo: Flyway y base

    /** Flyway sobre la base de pruebas; {@code target} nulo = última versión. */
    private static Flyway flyway(String target) {
        var configuracion = Flyway.configure().dataSource(FUENTE).locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            configuracion.target(target);
        }
        return configuracion.load();
    }

    /**
     * Único camino a {@code clean()} de esta clase: antes comprueba que la conexión apunta a una base cuyo nombre
     * termina en «Test» y, si no, lanza {@link IllegalStateException} sin limpiar nada.
     */
    private static void limpiar(Flyway flyway) {
        limpiarSiEsBaseDePrueba(JDBC.queryForObject("SELECT DB_NAME()", String.class), flyway::clean);
    }

    /** La guarda de {@link #limpiar}, separada para poder probarla con un nombre de base ajeno sin limpiar nada. */
    private static void limpiarSiEsBaseDePrueba(String nombreDeLaBase, Runnable limpieza) {
        LimpiezaBasePruebaConfig.exigirBaseDePrueba(nombreDeLaBase);
        limpieza.run();
    }

    /** Limpia la base de pruebas (con la guarda) y migra hasta {@code version}. */
    private static Flyway prepararHasta(String version) {
        Flyway flyway = flyway(null);
        limpiar(flyway);
        flyway(version).migrate();
        return flyway;
    }

    private static String versionActual(Flyway flyway) {
        MigrationInfo actual = flyway.info().current();
        return actual == null ? null : actual.getVersion().getVersion();
    }

    private static String ultimaVersion(Flyway flyway) {
        MigrationInfo[] todas = flyway.info().all();
        return todas[todas.length - 1].getVersion().getVersion();
    }

    private static boolean existe(String vista, String nombre) {
        return JDBC.queryForObject("SELECT COUNT(*) FROM " + vista + " WHERE name = ?", Integer.class, nombre) > 0;
    }

    /** Todas las filas de todas las tablas de datos, en orden estable, para comparar antes y después. */
    private static String instantaneaDeDatos() {
        StringBuilder resultado = new StringBuilder();
        List<String> tablas = JDBC.queryForList("SELECT name FROM sys.tables WHERE name <> 'flyway_schema_history' "
                + "ORDER BY name", String.class);
        for (String tabla : tablas) {
            resultado.append(tabla).append(JDBC.queryForList("SELECT * FROM " + tabla + " ORDER BY 1, 2"))
                    .append('\n');
        }
        return resultado.toString();
    }

    /** Nombres de restricciones, índices y disparadores de las tablas de datos. */
    private static String instantaneaDeEsquema() {
        List<String> nombres = new ArrayList<>(JDBC.queryForList("SELECT name FROM sys.objects WHERE "
                + "is_ms_shipped = 0 AND name NOT LIKE 'flyway%' ORDER BY name", String.class));
        nombres.addAll(JDBC.queryForList("SELECT i.name FROM sys.indexes AS i JOIN sys.tables AS t "
                + "ON t.object_id = i.object_id WHERE i.name IS NOT NULL AND t.name <> 'flyway_schema_history' "
                + "ORDER BY i.name", String.class));
        return String.join("\n", nombres);
    }

    /** Código de error de SQL Server de la primera {@link SQLException} en la cadena de causas. */
    private static Integer codigoSql(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getErrorCode();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- Apoyo: filas

    /** Un rol, un usuario, un servicio y un cliente válidos, sobre los que colgar la fila inválida de cada caso. */
    private static Base baseValida() {
        Base base = new Base(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        JDBC.update("INSERT INTO rol (id, nombre, descripcion) VALUES (?, 'Recepción', 'Agenda')", base.rol());
        insertarUsuario(base.usuario(), base.rol(), "marta", HASH);
        JDBC.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'Corte', 60, 1, 0)",
                base.servicio());
        JDBC.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) "
                + "VALUES (?, 'Ana', 'Pérez', '300', 'ana@x.co')", base.cliente());
        return base;
    }

    /**
     * Datos que la aplicación realmente guarda: un correo con eñe, un teléfono de usuario y una descripción de rol
     * vacíos, un administrador sin apellido ni correo, ventanas contiguas, citas de los tres estados y un enlace.
     * Las citas cuelgan del servicio y el cliente recibidos.
     */
    private static void insertarDatosValidos(UUID servicio, UUID cliente) {
        UUID rol = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID enlace = UUID.randomUUID();
        JDBC.update("INSERT INTO rol (id, nombre, descripcion) VALUES (?, 'Administrador', '')", rol);
        JDBC.update("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, 'ver_panel'), (?, 'gestionar_citas')",
                rol, rol);
        JDBC.update("INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id) "
                + "VALUES (?, 'Administrador', '', '', '', 'admin', ?, ?)", admin, HASH, rol);
        insertarUsuario(UUID.randomUUID(), rol, "andrés muñoz", HASH);
        JDBC.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'Corte', 60, 1, 25000.50)",
                servicio);
        insertarVentana(servicio, 1, "09:00", "12:00");
        insertarVentana(servicio, 1, "12:00", "18:00");
        insertarVentana(servicio, 3, "09:00", "12:00");
        JDBC.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) "
                + "VALUES (?, 'Ana María', 'Muñoz', '300 123', 'ana.muñoz@correo.co')", cliente);
        JDBC.update("INSERT INTO enlace_invitacion (id, codigo, creado_por, creado_en, vence_en, max_usos, usos, "
                + "desactivado_en) VALUES (?, 'Abc_def-123', ?, '2030-01-01 10:00:00', '2030-02-01 10:00:00', 5, 1, "
                + "'2030-01-02 10:00:00')", enlace, admin);
        insertarCita(servicio, cliente, "10:00", "11:00", "Confirmada");
        insertarCita(servicio, cliente, "11:00", "12:00", "Atendida");
        insertarCita(servicio, cliente, "10:30", "11:30", "Cancelada");
        JDBC.update("UPDATE cita SET enlace_id = ? WHERE hora_inicio = '11:00'", enlace);
    }

    private static void insertarCliente(String nombre, String apellido, String telefono, String correo) {
        JDBC.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), nombre, apellido, telefono, correo);
    }

    private static void insertarUsuario(UUID rol, String usuario, String claveHash) {
        insertarUsuario(UUID.randomUUID(), rol, usuario, claveHash);
    }

    private static void insertarUsuario(UUID id, UUID rol, String usuario, String claveHash) {
        insertarUsuario(id, rol, "Marta", "marta@x.co", usuario, claveHash);
    }

    private static void insertarUsuario(UUID id, UUID rol, String nombre, String correo, String usuario,
            String claveHash) {
        JDBC.update("INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id) "
                + "VALUES (?, ?, 'Ruiz', ?, '300', ?, ?, ?)", id, nombre, correo, usuario, claveHash, rol);
    }

    private static void insertarVentana(UUID servicio, int dia, String inicio, String fin) {
        JDBC.update("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                + "VALUES (?, ?, ?, ?, ?)", servicio, UUID.randomUUID(), dia, inicio, fin);
    }

    /** Cita del 2030-01-07; si está cancelada lleva cancelada_en, y si no, no (coherente con V7). */
    private static void insertarCita(UUID servicio, UUID cliente, String inicio, String fin, String estado) {
        JDBC.update("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en, "
                + "cancelada_en) VALUES (?, ?, ?, '2030-01-07', ?, ?, ?, '2030-01-01 08:00:00', "
                + "CASE WHEN ? = 'Cancelada' THEN '2030-01-01 09:00:00' END)",
                UUID.randomUUID(), servicio, cliente, inicio, fin, estado, estado);
    }
}
