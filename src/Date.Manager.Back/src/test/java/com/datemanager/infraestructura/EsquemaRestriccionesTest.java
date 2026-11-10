package com.datemanager.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.stream.Stream;

import com.datemanager.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Una prueba por restricción de la migración V7: SQL directo que la viola (la base lanza
 * {@link DataIntegrityViolationException} con el nombre de la restricción) y casos límite válidos que se aceptan.
 * La aplicación valida antes, así que estas pruebas ejercen la última defensa saltándosela.
 */
class EsquemaRestriccionesTest extends PruebaIntegracion {

    private static final String INSERTAR_CITA = "INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, "
            + "hora_fin, estado, creada_en, cancelada_en) VALUES (?, ?, ?, '2030-01-07', '10:00', '11:00', ?, ?, ?)";

    private UUID servicio;
    private UUID cliente;
    private UUID rol;

    /** Prepara un servicio, un cliente y un rol válidos sobre los que probar. */
    private void preparar() {
        servicio = UUID.randomUUID();
        cliente = UUID.randomUUID();
        rol = UUID.randomUUID();
        jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'Corte', 60, 1, 0)",
                servicio);
        jdbc.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) "
                + "VALUES (?, 'Ana', 'Pérez', '300', 'ana@x.co')", cliente);
        jdbc.update("INSERT INTO rol (id, nombre, descripcion) VALUES (?, 'Recepción', 'Agenda')", rol);
    }

    private void afirmarRechazo(Runnable sql, String restriccion) {
        assertThatThrownBy(sql::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(restriccion);
    }

    private void insertarCita(String estado, String creadaEn, String canceladaEn) {
        jdbc.update(INSERTAR_CITA, UUID.randomUUID(), servicio, cliente, estado, creadaEn, canceladaEn);
    }

    private void insertarCliente(String nombre, String apellido, String telefono, String correo) {
        jdbc.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), nombre, apellido, telefono, correo);
    }

    private void insertarUsuario(String nombre, String apellido, String correo, String telefono, String usuario,
            String claveHash) {
        jdbc.update("INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), nombre, apellido, correo, telefono, usuario,
                claveHash, rol);
    }

    private String hashReal() {
        return new BCryptPasswordEncoder().encode("x");
    }

    // ---- Citas ----

    @Test
    void citaCanceladaSinFechaDeCancelacionSeRechaza() {
        preparar();
        afirmarRechazo(() -> insertarCita("Cancelada", "2030-01-01 08:00:00", null), "ck_cita_cancelada_coherente");
    }

    @Test
    void citaConfirmadaConFechaDeCancelacionSeRechaza() {
        preparar();
        afirmarRechazo(() -> insertarCita("Confirmada", "2030-01-01 08:00:00", "2030-01-02 08:00:00"),
                "ck_cita_cancelada_coherente");
    }

    @Test
    void citaAtendidaConFechaDeCancelacionSeRechaza() {
        preparar();
        afirmarRechazo(() -> insertarCita("Atendida", "2030-01-01 08:00:00", "2030-01-02 08:00:00"),
                "ck_cita_cancelada_coherente");
    }

    @Test
    void canceladaEnAnteriorACreadaEnSeRechaza() {
        preparar();
        afirmarRechazo(() -> insertarCita("Cancelada", "2030-01-02 08:00:00", "2030-01-01 08:00:00"),
                "ck_cita_cancelada_posterior");
    }

    @Test
    void unUpdateQueRompeLaCoherenciaDeLaCitaSeRechaza() {
        preparar();
        UUID cita = UUID.randomUUID();
        jdbc.update(INSERTAR_CITA, cita, servicio, cliente, "Confirmada", "2030-01-01 08:00:00", null);
        afirmarRechazo(() -> jdbc.update("UPDATE cita SET estado = 'Cancelada' WHERE id = ?", cita),
                "ck_cita_cancelada_coherente");
    }

    @Test
    void citaCanceladaConCancelacionIgualALaCreacionSeAcepta() {
        preparar();
        assertThatCode(() -> insertarCita("Cancelada", "2030-01-01 08:00:00", "2030-01-01 08:00:00"))
                .doesNotThrowAnyException();
    }

    @Test
    void citasConfirmadaYAtendidaSinCancelacionSeAceptan() {
        preparar();
        assertThatCode(() -> {
            jdbc.update(INSERTAR_CITA, UUID.randomUUID(), servicio, cliente, "Confirmada", "2030-01-01 08:00:00", null);
            jdbc.update("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, "
                    + "creada_en) VALUES (?, ?, ?, '2030-01-08', '10:00', '11:00', 'Atendida', '2030-01-01 08:00:00')",
                    UUID.randomUUID(), servicio, cliente);
        }).doesNotThrowAnyException();
    }

    // ---- Textos no vacíos ----

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void clienteConNombreVacioSeRechaza(String vacio) {
        afirmarRechazo(() -> insertarCliente(vacio, "B", "1", "a@x.co"), "ck_cliente_nombre_no_vacio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void clienteConApellidoVacioSeRechaza(String vacio) {
        afirmarRechazo(() -> insertarCliente("A", vacio, "1", "a@x.co"), "ck_cliente_apellido_no_vacio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void clienteConTelefonoVacioSeRechaza(String vacio) {
        afirmarRechazo(() -> insertarCliente("A", "B", vacio, "a@x.co"), "ck_cliente_telefono_no_vacio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void usuarioConNombreVacioSeRechaza(String vacio) {
        preparar();
        afirmarRechazo(() -> insertarUsuario(vacio, "B", "a@x.co", "1", "ana", hashReal()),
                "ck_usuario_nombre_no_vacio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void servicioConNombreVacioSeRechaza(String vacio) {
        afirmarRechazo(() -> jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) "
                + "VALUES (?, ?, 30, 1, 0)", UUID.randomUUID(), vacio), "ck_servicio_nombre_no_vacio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rolConNombreVacioSeRechaza(String vacio) {
        afirmarRechazo(() -> jdbc.update("INSERT INTO rol (id, nombre, descripcion) VALUES (?, ?, 'd')",
                UUID.randomUUID(), vacio), "ck_rol_nombre_no_vacio");
    }

    @Test
    void textosConTildesYEspaciosInternosSeAceptan() {
        preparar();
        assertThatCode(() -> {
            insertarCliente("María José", "Núñez Peña", "+57 300 555 0101", "maria@x.co");
            insertarUsuario("José", "Muñoz", "jose@x.co", "", "jose", hashReal());
            jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'Corte y barba', "
                    + "30, 1, 0)", UUID.randomUUID());
        }).doesNotThrowAnyException();
    }

    @Test
    void usuarioConTelefonoVacioYRolConDescripcionVaciaSeAceptan() {
        preparar();
        assertThatCode(() -> {
            jdbc.update("INSERT INTO rol (id, nombre, descripcion) VALUES (?, 'Sin descripción', '')",
                    UUID.randomUUID());
            insertarUsuario("Ana", "Pérez", "ana@x.co", "", "ana", hashReal());
        }).doesNotThrowAnyException();
    }

    /** El administrador inicial se crea sin apellido ni correo ni teléfono (AdministradorInicial). */
    @Test
    void usuarioSinApellidoNiCorreoComoElAdministradorInicialSeAcepta() {
        preparar();
        assertThatCode(() -> insertarUsuario("Administrador", "", "", "", "admin", hashReal()))
                .doesNotThrowAnyException();
    }

    // ---- Correos ----

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "sin-arroba", "a@b", "a b@c.co", "a@@b.co", "Ana@x.co", "a@B.co", "a@b.co ",
            " a@b.co", "a@b c.co", "@x.co", "a@.co"})
    void clienteConCorreoInvalidoSeRechaza(String correo) {
        afirmarRechazo(() -> insertarCliente("A", "B", "1", correo), "ck_cliente_correo_forma");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin-arroba", "a@b", "a b@c.co", "a@@b.co", "Ana@x.co", "a@B.co", "a@b.co ", " a@b.co",
            "a@b c.co", "@x.co", "a@.co"})
    void usuarioConCorreoInvalidoSeRechaza(String correo) {
        preparar();
        afirmarRechazo(() -> insertarUsuario("A", "B", correo, "1", "ana", hashReal()), "ck_usuario_correo_forma");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ñandú@correo.co", "josé@dominio.com", "a@b.co", "a.b+c@sub.dominio.co", "ana@x.com.co"})
    void correosConTildeEnieYSubdominiosSeAceptan(String correo) {
        preparar();
        assertThatCode(() -> {
            insertarCliente("A", "B", "1", correo);
            insertarUsuario("A", "B", correo, "1", "u" + UUID.randomUUID().toString().substring(0, 8), hashReal());
        }).doesNotThrowAnyException();
    }

    // ---- Usuario (nombre de usuario) ----

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", " ana", "ana ", "Ana", "anA"})
    void usuarioConNombreDeUsuarioInvalidoSeRechaza(String usuario) {
        preparar();
        afirmarRechazo(() -> insertarUsuario("A", "B", "a@x.co", "1", usuario, hashReal()),
                "ck_usuario_usuario_forma");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana", "ana.perez_2", "josé", "ñandú", "ana perez"})
    void nombresDeUsuarioEnMinusculasSeAceptan(String usuario) {
        preparar();
        assertThatCode(() -> insertarUsuario("A", "B", "a@x.co", "1", usuario, hashReal()))
                .doesNotThrowAnyException();
    }

    // ---- Hash de clave ----

    static Stream<String> hashesSinFormaBcrypt() {
        return Stream.of("x", "", "demo123", "$2a$10$corto", "$1a$10$" + "a".repeat(53), "$2a$10$" + "a".repeat(54),
                "$2a$1$" + "a".repeat(54), "$2a$10$" + "a".repeat(52) + " ", "$2a$10$" + "a".repeat(52) + "*");
    }

    @ParameterizedTest
    @MethodSource("hashesSinFormaBcrypt")
    void hashDeClaveSinFormaBcryptSeRechaza(String hash) {
        preparar();
        afirmarRechazo(() -> insertarUsuario("A", "B", "a@x.co", "1", "ana", hash), "ck_usuario_clave_hash_bcrypt");
    }

    @Test
    void hashBcryptRealDelCodificadorDeLaAplicacionSeAcepta() {
        preparar();
        String hash = hashReal();
        assertThat(hash).hasSize(60);
        assertThatCode(() -> insertarUsuario("A", "B", "a@x.co", "1", "ana", hash)).doesNotThrowAnyException();
    }

    // ---- Servicio y enlace ----

    @Test
    void servicioConDuracionMayorAUnDiaSeRechaza() {
        afirmarRechazo(() -> jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) "
                + "VALUES (?, 'S', 1441, 1, 0)", UUID.randomUUID()), "ck_servicio_duracion_max");
    }

    @Test
    void servicioConDuracionDeUnDiaExactoSeAcepta() {
        assertThatCode(() -> jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) "
                + "VALUES (?, 'S', 1440, 1, 0)", UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void enlaceDesactivadoAntesDeCrearseSeRechaza() {
        preparar();
        UUID creador = usuarioCreador();
        afirmarRechazo(() -> insertarEnlace(creador, "2030-01-02 08:00:00", "2030-01-01 08:00:00"),
                "ck_enlace_invitacion_desactivado");
    }

    @Test
    void enlaceDesactivadoEnElMismoInstanteOSinDesactivarSeAcepta() {
        preparar();
        UUID creador = usuarioCreador();
        assertThatCode(() -> {
            insertarEnlace(creador, "2030-01-02 08:00:00", "2030-01-02 08:00:00");
            insertarEnlace(creador, "2030-01-02 08:00:00", null);
        }).doesNotThrowAnyException();
    }

    private UUID usuarioCreador() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id) "
                + "VALUES (?, 'A', 'B', 'a@x.co', '', 'creador', ?, ?)", id, hashReal(), rol);
        return id;
    }

    private void insertarEnlace(UUID creador, String creadoEn, String desactivadoEn) {
        jdbc.update("INSERT INTO enlace_invitacion (id, codigo, creado_por, creado_en, vence_en, usos, "
                + "desactivado_en) VALUES (?, ?, ?, ?, '2031-01-01 00:00:00', 0, ?)", UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", ""), creador, creadoEn, desactivadoEn);
    }
}
