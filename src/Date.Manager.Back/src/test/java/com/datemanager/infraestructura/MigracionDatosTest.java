package com.datemanager.infraestructura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.datemanager.PruebaBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * La migración sobre una base que ya tiene datos (V7, V8 y V9): con datos válidos termina bien y no cambia ninguna
 * fila; con datos que violan una regla aborta con un mensaje legible, deja la base exactamente como estaba (misma
 * versión, mismas restricciones, índices y disparadores, mismas filas) y no corrige ningún dato.
 *
 * <p>Maneja el migrador a mano sobre la misma base de pruebas ({@code DateManagerTest}; {@code limpiar()} se niega a
 * tocar otra) y, al terminar la clase, la deja en la última versión para no afectar a las demás pruebas.
 */
class MigracionDatosTest extends PruebaBase {

    private static final int V6 = 6;
    private static final int V7 = 7;
    private static final int V8 = 8;
    /** Con la forma de un hash BCrypt (60 caracteres); el esquema solo comprueba la forma. */
    private static final String HASH = "$2a$04$" + "A".repeat(53);

    /** Identificadores de las filas «padre» válidas que crea {@link #baseValida}. */
    private record Base(UUID rol, UUID usuario, UUID servicio, UUID cliente) {
    }

    /**
     * Deja la base en la última versión para las demás pruebas. Pasa por la guarda de {@code limpiar()}; si algo falla
     * lo dice con un mensaje propio (el fallo se suma al resultado de las pruebas, no lo oculta).
     */
    @AfterAll
    static void restaurarEsquemaMasReciente() {
        try {
            migrador.limpiar();
            migrador.migrar();
        } catch (RuntimeException e) {
            throw new IllegalStateException("No se pudo restaurar el esquema más reciente de la base de pruebas; "
                    + "las demás pruebas pueden fallar hasta que se vuelva a migrar.", e);
        }
    }

    // ---------------------------------------------------------------- Migración con datos válidos

    @Test
    void conDatosValidosMigraALaUltimaVersionSinTocarNingunaFila() {
        prepararHasta(V6);
        UUID servicio = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
        insertarDatosValidos(servicio, cliente);
        String datosAntes = instantaneaDeDatos();
        String esquemaAntes = instantaneaDeEsquema();

        migrador.migrar();

        assertEquals(Optional.of(migrador.ultimaVersionDisponible()), migrador.versionActual());
        assertEquals(datosAntes, instantaneaDeDatos(), "las filas no cambian al migrar");
        assertNotEquals(esquemaAntes, instantaneaDeEsquema(), "la migración sí añade reglas");
        assertTrue(existe("sys.triggers", "tr_cita_sin_solape"));
        assertTrue(existe("sys.triggers", "tr_ventana_sin_cruce"));
        assertTrue(existe("sys.check_constraints", "ck_cliente_nombre_no_vacio"));

        // Las reglas ya rigen sobre la base migrada: una cita solapada y un cliente sin nombre se rechazan.
        Throwable solape = assertThrows(Throwable.class,
                () -> insertarCita(servicio, cliente, "10:30", "11:30", "Confirmada"));
        assertEquals(50010, codigoSql(solape), "disparador de citas solapadas");
        Throwable sinNombre = assertThrows(Throwable.class, () -> ejecutar("INSERT INTO cliente (id, nombre, apellido, "
                + "telefono, correo) VALUES (?, '  ', 'Gómez', '300', 'x@x.co')", UUID.randomUUID()));
        assertTrue(mensajeSql(sinNombre).contains("ck_cliente_nombre_no_vacio"), mensajeSql(sinNombre));
        assertEquals(datosAntes, instantaneaDeDatos(), "los rechazos no dejan filas");
    }

    // ---------------------------------------------------------------- Aborto de V7 (una prueba por regla)

    static Stream<Arguments> datosQueViolanV7() {
        return Stream.of(
                caso("ck_cliente_nombre_no_vacio", (b) -> insertarCliente("  ", "Gómez", "300", "x@x.co")),
                caso("ck_cliente_apellido_no_vacio", (b) -> insertarCliente("Ana", "", "300", "otra@x.co")),
                caso("ck_cliente_telefono_no_vacio", (b) -> insertarCliente("Ana", "Pérez", "  ", "otra@x.co")),
                caso("ck_cliente_correo_forma", (b) -> insertarCliente("Ana", "Pérez", "300", "Otra@X.co")),
                caso("ck_cliente_correo_forma", (b) -> insertarCliente("Ana", "Pérez", "300", "otra@@x.co")),
                caso("ck_cita_cancelada_coherente", (b) -> ejecutar("INSERT INTO cita (id, servicio_id, cliente_id, "
                        + "fecha, hora_inicio, hora_fin, estado, creada_en, cancelada_en) VALUES (?, ?, ?, "
                        + "'2030-01-07', '10:00', '11:00', 'Cancelada', '2030-01-01 09:00:00', NULL)",
                        UUID.randomUUID(), b.servicio(), b.cliente())),
                caso("ck_cita_cancelada_posterior", (b) -> ejecutar("INSERT INTO cita (id, servicio_id, cliente_id, "
                        + "fecha, hora_inicio, hora_fin, estado, creada_en, cancelada_en) VALUES (?, ?, ?, "
                        + "'2030-01-07', '10:00', '11:00', 'Cancelada', '2030-01-01 09:00:00', "
                        + "'2030-01-01 08:00:00')", UUID.randomUUID(), b.servicio(), b.cliente())),
                caso("ck_usuario_clave_hash_bcrypt", (b) -> insertarUsuario(b.rol(), "luis", "texto plano")),
                caso("ck_usuario_usuario_forma", (b) -> insertarUsuario(b.rol(), "Luis", HASH)),
                caso("ck_usuario_nombre_no_vacio", (b) -> insertarUsuario(UUID.randomUUID(), b.rol(), "  ",
                        "luis@x.co", "luis", HASH)),
                caso("ck_usuario_correo_forma", (b) -> insertarUsuario(UUID.randomUUID(), b.rol(), "Luis",
                        "luis@@x.co", "luis", HASH)),
                caso("ck_servicio_nombre_no_vacio", (b) -> ejecutar("INSERT INTO servicio (id, nombre, "
                        + "duracion_min, activo, valor) VALUES (?, '  ', 30, 1, 0)", UUID.randomUUID())),
                caso("ck_rol_nombre_no_vacio", (b) -> ejecutar("INSERT INTO rol (id, nombre, descripcion) "
                        + "VALUES (?, '  ', 'Sin nombre')", UUID.randomUUID())),
                caso("ck_servicio_duracion_max", (b) -> ejecutar("INSERT INTO servicio (id, nombre, duracion_min, "
                        + "activo, valor) VALUES (?, 'Largo', 1441, 1, 0)", UUID.randomUUID())),
                caso("ck_enlace_invitacion_desactivado", (b) -> ejecutar("INSERT INTO enlace_invitacion (id, "
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
        prepararHasta(V6);
        filaInvalida.accept(baseValida());

        abortaSinTocarNada(V6, "Migración V7 abortada", regla);
        assertFalse(existe("sys.check_constraints", regla), "la restricción no se creó a medias");
    }

    // ---------------------------------------------------------------- Aborto de V8 y de V9

    @Test
    void v8AbortaLimpioSiYaHayCitasSolapadas() {
        prepararHasta(V6);
        Base base = baseValida();
        // Válidas para V7 (una no cancelada cada una, sin cancelada_en) pero solapadas entre sí.
        insertarCita(base.servicio(), base.cliente(), "10:00", "11:00", "Confirmada");
        insertarCita(base.servicio(), base.cliente(), "10:30", "11:30", "Atendida");
        migrador.migrarHasta(V7);

        abortaSinTocarNada(V7, "Migración V8 abortada", "citas solapadas");
        assertFalse(existe("sys.triggers", "tr_cita_sin_solape"));
        assertFalse(existe("sys.indexes", "ix_cita_servicio_ocupada"));
    }

    @Test
    void v9AbortaLimpioSiYaHayVentanasCruzadas() {
        prepararHasta(V6);
        Base base = baseValida();
        insertarVentana(base.servicio(), 1, "09:00", "12:00");
        insertarVentana(base.servicio(), 1, "11:00", "14:00");
        migrador.migrarHasta(V8);

        abortaSinTocarNada(V8, "Migración V9 abortada", "ventanas cruzadas");
        assertFalse(existe("sys.triggers", "tr_ventana_sin_cruce"));
    }

    /**
     * Migra hasta la última versión esperando que falle con un mensaje que nombra el motivo, y comprueba que la base
     * quedó exactamente como estaba antes: misma versión, sin intento fallido en el historial, mismo esquema y
     * mismas filas.
     */
    private void abortaSinTocarNada(int versionPrevia, String aborto, String motivo) {
        assertEquals(Optional.of(versionPrevia), migrador.versionActual());
        String datosAntes = instantaneaDeDatos();
        String esquemaAntes = instantaneaDeEsquema();

        Throwable error = assertThrows(IllegalStateException.class, migrador::migrar, "la migración debía abortar");

        assertNotNull(error.getMessage());
        assertTrue(error.getMessage().contains(aborto), error.getMessage());
        assertTrue(error.getMessage().contains(motivo), error.getMessage());
        assertTrue(error.getMessage().contains("No se modificó ningún dato"), error.getMessage());
        assertEquals(50001, codigoSql(error), "THROW 50001 de la auditoría");
        assertEquals(Optional.of(versionPrevia), migrador.versionActual(), "la base sigue en su versión");
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM historial_migraciones WHERE success = 0"),
                "el intento fallido no deja registro");
        assertEquals(esquemaAntes, instantaneaDeEsquema(), "sin restricciones, índices ni disparadores a medias");
        assertEquals(datosAntes, instantaneaDeDatos(), "la auditoría no corrige ni borra datos");
    }

    // ---------------------------------------------------------------- Apoyo: migrador y base

    /** Limpia la base de pruebas (con la guarda) y migra hasta {@code version}. */
    private static void prepararHasta(int version) {
        migrador.limpiar();
        migrador.migrarHasta(version);
    }

    private static boolean existe(String vista, String nombre) {
        return consultarInt("SELECT COUNT(*) FROM " + vista + " WHERE name = ?", nombre) > 0;
    }

    /** Todas las filas de todas las tablas de datos, en orden estable, para comparar antes y después. */
    private static String instantaneaDeDatos() {
        StringBuilder resultado = new StringBuilder();
        List<String> tablas = columna("SELECT name FROM sys.tables "
                + "WHERE name NOT IN ('flyway_schema_history', 'historial_migraciones') ORDER BY name");
        for (String tabla : tablas) {
            resultado.append(tabla).append(filas("SELECT * FROM " + tabla + " ORDER BY 1, 2")).append('\n');
        }
        return resultado.toString();
    }

    /** Nombres de restricciones, índices y disparadores de las tablas de datos. */
    private static String instantaneaDeEsquema() {
        List<String> nombres = new ArrayList<>(columna("SELECT name FROM sys.objects WHERE is_ms_shipped = 0 "
                + "AND name NOT LIKE 'flyway%' AND name NOT LIKE '%historial_migraciones%' ORDER BY name"));
        nombres.addAll(columna("SELECT i.name FROM sys.indexes AS i JOIN sys.tables AS t "
                + "ON t.object_id = i.object_id WHERE i.name IS NOT NULL "
                + "AND t.name NOT IN ('flyway_schema_history', 'historial_migraciones') ORDER BY i.name"));
        return String.join("\n", nombres);
    }

    // ---------------------------------------------------------------- Apoyo: filas

    /** Un rol, un usuario, un servicio y un cliente válidos, sobre los que colgar la fila inválida de cada caso. */
    private static Base baseValida() {
        Base base = new Base(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ejecutar("INSERT INTO rol (id, nombre, descripcion) VALUES (?, 'Recepción', 'Agenda')", base.rol());
        insertarUsuario(base.usuario(), base.rol(), "marta", HASH);
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'Corte', 60, 1, 0)",
                base.servicio());
        ejecutar("INSERT INTO cliente (id, nombre, apellido, telefono, correo) "
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
        ejecutar("INSERT INTO rol (id, nombre, descripcion) VALUES (?, 'Administrador', '')", rol);
        ejecutar("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, 'ver_panel'), (?, 'gestionar_citas')",
                rol, rol);
        ejecutar("INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id) "
                + "VALUES (?, 'Administrador', '', '', '', 'admin', ?, ?)", admin, HASH, rol);
        insertarUsuario(UUID.randomUUID(), rol, "andrés muñoz", HASH);
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'Corte', 60, 1, 25000.50)",
                servicio);
        insertarVentana(servicio, 1, "09:00", "12:00");
        insertarVentana(servicio, 1, "12:00", "18:00");
        insertarVentana(servicio, 3, "09:00", "12:00");
        ejecutar("INSERT INTO cliente (id, nombre, apellido, telefono, correo) "
                + "VALUES (?, 'Ana María', 'Muñoz', '300 123', 'ana.muñoz@correo.co')", cliente);
        ejecutar("INSERT INTO enlace_invitacion (id, codigo, creado_por, creado_en, vence_en, max_usos, usos, "
                + "desactivado_en) VALUES (?, 'Abc_def-123', ?, '2030-01-01 10:00:00', '2030-02-01 10:00:00', 5, 1, "
                + "'2030-01-02 10:00:00')", enlace, admin);
        insertarCita(servicio, cliente, "10:00", "11:00", "Confirmada");
        insertarCita(servicio, cliente, "11:00", "12:00", "Atendida");
        insertarCita(servicio, cliente, "10:30", "11:30", "Cancelada");
        ejecutar("UPDATE cita SET enlace_id = ? WHERE hora_inicio = '11:00'", enlace);
    }

    private static void insertarCliente(String nombre, String apellido, String telefono, String correo) {
        ejecutar("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, ?, ?, ?, ?)",
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
        ejecutar("INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id) "
                + "VALUES (?, ?, 'Ruiz', ?, '300', ?, ?, ?)", id, nombre, correo, usuario, claveHash, rol);
    }

    private static void insertarVentana(UUID servicio, int dia, String inicio, String fin) {
        ejecutar("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                + "VALUES (?, ?, ?, ?, ?)", servicio, UUID.randomUUID(), dia, inicio, fin);
    }

    /** Cita del 2030-01-07; si está cancelada lleva cancelada_en, y si no, no (coherente con V7). */
    private static void insertarCita(UUID servicio, UUID cliente, String inicio, String fin, String estado) {
        ejecutar("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en, "
                + "cancelada_en) VALUES (?, ?, ?, '2030-01-07', ?, ?, ?, '2030-01-01 08:00:00', "
                + "CASE WHEN ? = 'Cancelada' THEN '2030-01-01 09:00:00' END)",
                UUID.randomUUID(), servicio, cliente, inicio, fin, estado, estado);
    }
}
