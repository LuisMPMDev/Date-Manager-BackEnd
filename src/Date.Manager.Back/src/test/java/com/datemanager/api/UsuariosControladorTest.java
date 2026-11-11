package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsuariosControladorTest extends PruebaPanel {

    private static final String USUARIOS = "/api/panel/usuarios/";

    private UUID rolAdmin;
    private UUID rolRecepcion;
    private UUID admin;

    @BeforeEach
    void crearAdministrador() {
        rolAdmin = crearRol("Administrador", "ver_panel", "ver_citas", "gestionar_usuarios", "gestionar_roles");
        rolRecepcion = crearRol("Recepción", "ver_citas");
        admin = crearUsuario("admin", rolAdmin);
    }

    /** Cuerpo completo de un usuario; {@code clave} nula omite el campo. */
    private Map<String, Object> cuerpo(String usuario, UUID rolId, String clave) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", "Lucía");
        cuerpo.put("apellido", "Rivas");
        cuerpo.put("correo", usuario.strip() + "@example.com");
        cuerpo.put("telefono", "300 555 0202");
        cuerpo.put("usuario", usuario);
        if (clave != null) {
            cuerpo.put("clave", clave);
        }
        cuerpo.put("rolId", rolId.toString());
        return cuerpo;
    }

    private static int filasConUsuario(String usuario) {
        return consultarInt("SELECT COUNT(*) FROM usuario WHERE usuario = ?", usuario);
    }

    private static String rolDe(UUID usuario) {
        return consultarTexto("SELECT CONVERT(NVARCHAR(36), rol_id) FROM usuario WHERE id = ?", usuario);
    }

    private UUID actorSinGestionarRoles(String... permisosDelRol) {
        return crearUsuario("gestor", crearRol("Gestor", permisosDelRol));
    }

    // --- Guardar -----------------------------------------------------------------------------------------

    @Test
    void crearUnUsuarioNuevoGuardaElHashBcryptYNoDevuelveLaClave() {
        UUID id = UUID.randomUUID();

        Resp r = exigir(200, guardar(USUARIOS + id, admin, cuerpo("lucia", rolRecepcion, "Secreta-123")));
        assertEquals(id.toString(), r.en("id"));
        assertEquals("Lucía", r.en("nombre"));
        assertEquals("lucia", r.en("usuario"));
        assertEquals(rolRecepcion.toString(), r.en("rolId"));
        assertFalse(r.existe("clave"));
        assertFalse(r.existe("claveHash"));
        assertFalse(r.texto().contains("Secreta-123"));
        assertFalse(r.texto().contains("$2"));

        String hash = hashDe(id);
        assertTrue(hash.startsWith("$2"));
        assertFalse(hash.contains("Secreta-123"));
        assertTrue(BCRYPT.coincide("Secreta-123", hash));
    }

    @Test
    void crearSinClaveEs400ConElCampo() {
        for (String clave : new String[] { null, "", "   " }) {
            Resp r = exigir(400, "VALIDACION", guardar(USUARIOS + UUID.randomUUID(), admin,
                    cuerpo("lucia", rolRecepcion, clave)));
            assertFalse(r.texto("campos.clave").isEmpty());
        }
        assertEquals(0, filasConUsuario("lucia"));
    }

    @Test
    void editarConClaveVaciaOAusenteConservaElHash() {
        UUID ana = crearUsuario("ana", rolRecepcion, "clave-de-ana");
        String hashAntes = hashDe(ana);

        Map<String, Object> cambios = cuerpo("ana", rolRecepcion, "");
        cambios.put("nombre", "Ana María");
        assertEquals("Ana María", exigir(200, guardar(USUARIOS + ana, admin, cambios)).en("nombre"));
        assertEquals(hashAntes, hashDe(ana));

        exigir(200, guardar(USUARIOS + ana, admin, cuerpo("ana", rolRecepcion, null)));
        assertEquals(hashAntes, hashDe(ana));
        assertTrue(BCRYPT.coincide("clave-de-ana", hashDe(ana)));
    }

    @Test
    void editarConClaveNuevaCambiaElHash() {
        UUID ana = crearUsuario("ana", rolRecepcion, "clave-de-ana");
        String hashAntes = hashDe(ana);

        Resp r = exigir(200, guardar(USUARIOS + ana, admin, cuerpo("ana", rolRecepcion, "otra-clave-nueva")));
        assertFalse(r.texto().contains("otra-clave-nueva"));

        assertNotEquals(hashAntes, hashDe(ana));
        assertTrue(hashDe(ana).startsWith("$2"));
        assertTrue(BCRYPT.coincide("otra-clave-nueva", hashDe(ana)));
        assertFalse(BCRYPT.coincide("clave-de-ana", hashDe(ana)));
    }

    @Test
    void usuarioYCorreoSeNormalizanAlGuardar() {
        UUID id = UUID.randomUUID();
        Map<String, Object> datos = cuerpo(" Lucia ", rolRecepcion, "abc12345");
        datos.put("correo", "  Lucia@Example.COM ");

        Resp r = exigir(200, guardar(USUARIOS + id, admin, datos));
        assertEquals("lucia", r.en("usuario"));
        assertEquals("lucia@example.com", r.en("correo"));

        assertEquals("lucia", consultarTexto("SELECT usuario FROM usuario WHERE id = ?", id));
    }

    @Test
    void usuarioDuplicadoSinImportarEspaciosNiMayusculasEs409YNuncaHayDosFilas() {
        crearUsuario("marta", rolRecepcion);

        Resp r = exigir(409, "CONFLICTO", guardar(USUARIOS + UUID.randomUUID(), admin,
                cuerpo("Marta ", rolRecepcion, "abc12345")));
        assertFalse(r.texto("mensaje").isEmpty());
        exigir(409, guardar(USUARIOS + UUID.randomUUID(), admin, cuerpo(" MARTA", rolRecepcion, "abc12345")));

        assertEquals(1, filasConUsuario("marta"));
        assertEquals(2, contar("usuario"));
    }

    @Test
    void cambiarElUsuarioDeOtroAUnoQueYaExisteEs409() {
        crearUsuario("marta", rolRecepcion);
        UUID ana = crearUsuario("ana", rolRecepcion);

        exigir(409, "CONFLICTO", guardar(USUARIOS + ana, admin, cuerpo("Marta", rolRecepcion, null)));

        assertEquals("ana", consultarTexto("SELECT usuario FROM usuario WHERE id = ?", ana));
    }

    @Test
    void conservarElPropioUsuarioConOtraMayusculaNoEsDuplicado() {
        UUID ana = crearUsuario("ana", rolRecepcion);

        exigir(200, guardar(USUARIOS + ana, admin, cuerpo("Ana ", rolRecepcion, null)));
    }

    @Test
    void dosPeticionesSimultaneasConElMismoUsuarioDejanUnaSolaFila() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tareas = new ArrayList<>();
            for (String variante : new String[] { "carla", " Carla " }) {
                tareas.add(() -> guardar(USUARIOS + UUID.randomUUID(), admin,
                        cuerpo(variante, rolRecepcion, "abc12345")).estado());
            }
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> futuro : hilos.invokeAll(tareas)) {
                estados.add(futuro.get());
            }
            estados.sort(null);
            assertEquals(List.of(200, 409), estados);
        } finally {
            hilos.shutdownNow();
        }
        assertEquals(1, filasConUsuario("carla"));
    }

    @Test
    void correoInvalidoEs400() {
        for (String correo : new String[] { "lucia", "lucia@correo", "lucia@@example.com", "a b@example.com" }) {
            Map<String, Object> datos = cuerpo("lucia", rolRecepcion, "abc12345");
            datos.put("correo", correo);
            Resp r = exigir(400, "VALIDACION", guardar(USUARIOS + UUID.randomUUID(), admin, datos));
            assertFalse(r.texto("campos.correo").isEmpty());
        }
        assertEquals(0, filasConUsuario("lucia"));
    }

    @Test
    void losCamposObligatoriosEnBlancoSon400ConSusCampos() {
        Map<String, Object> datos = cuerpo("lucia", rolRecepcion, "abc12345");
        datos.put("nombre", " ");
        datos.put("apellido", "");
        datos.put("usuario", "  ");
        datos.put("correo", "");

        Resp r = exigir(400, "VALIDACION", guardar(USUARIOS + UUID.randomUUID(), admin, datos));
        assertEquals("Hay datos inválidos en la petición.", r.en("mensaje"));
        assertEquals("Escribe el nombre.", r.en("campos.nombre"));
        assertEquals("Escribe el apellido.", r.en("campos.apellido"));
        assertEquals("Escribe el nombre de usuario.", r.en("campos.usuario"));
        assertEquals("Escribe el correo.", r.en("campos.correo"));
    }

    @Test
    void losCamposDemasiadoLargosSon400ConLosMensajesDeSiempre() {
        Map<String, Object> datos = cuerpo("lucia", rolRecepcion, "abc12345");
        datos.put("nombre", "n".repeat(101));
        datos.put("apellido", "a".repeat(101));
        datos.put("telefono", "1".repeat(41));
        datos.put("usuario", "u".repeat(101));
        datos.put("correo", "c".repeat(255));

        Resp r = exigir(400, "VALIDACION", guardar(USUARIOS + UUID.randomUUID(), admin, datos));
        assertEquals("El nombre es demasiado largo.", r.en("campos.nombre"));
        assertEquals("El apellido es demasiado largo.", r.en("campos.apellido"));
        assertEquals("El teléfono es demasiado largo.", r.en("campos.telefono"));
        assertEquals("El nombre de usuario es demasiado largo.", r.en("campos.usuario"));
        assertEquals("El correo es demasiado largo.", r.en("campos.correo"));
    }

    @Test
    void elTelefonoEsOpcional() {
        Map<String, Object> datos = cuerpo("lucia", rolRecepcion, "abc12345");
        datos.remove("telefono");

        exigir(200, guardar(USUARIOS + UUID.randomUUID(), admin, datos));
    }

    @Test
    void unRolInexistenteOAusenteEs400ConElCampoRolId() {
        Map<String, Object> inexistente = cuerpo("lucia", rolRecepcion, "abc12345");
        inexistente.put("rolId", UUID.randomUUID().toString());
        Resp r = exigir(400, "VALIDACION", guardar(USUARIOS + UUID.randomUUID(), admin, inexistente));
        assertFalse(r.texto("campos.rolId").isEmpty());

        Map<String, Object> ausente = cuerpo("lucia", rolRecepcion, "abc12345");
        ausente.remove("rolId");
        Resp sinRol = exigir(400, guardar(USUARIOS + UUID.randomUUID(), admin, ausente));
        assertEquals("Elige un rol.", sinRol.en("campos.rolId"));

        assertEquals(0, filasConUsuario("lucia"));
    }

    @Test
    void unaClaveDeMasDe72BytesEs400PorqueBcryptNoLaSoporta() {
        Resp r = exigir(400, guardar(USUARIOS + UUID.randomUUID(), admin, cuerpo("lucia", rolRecepcion, "x".repeat(73))));
        assertFalse(r.texto("campos.clave").isEmpty());
    }

    // --- Listar ------------------------------------------------------------------------------------------

    @Test
    void listarNoIncluyeLaClaveNiElHash() {
        crearUsuario("ana", rolRecepcion, "clave-de-ana");

        Resp r = exigir(200, obtener("/api/panel/usuarios", admin));
        assertEquals(2, r.lista("$").size());
        assertEquals("admin", r.en("$[0].usuario"));
        assertEquals("ana", r.en("$[1].usuario"));
        assertNull(r.en("$[0].clave"));
        assertNull(r.en("$[0].claveHash"));
        assertFalse(r.texto().contains("$2"));
        assertFalse(r.texto().contains("clave-de-ana"));
    }

    // --- Escalada de privilegios --------------------------------------------------------------------------

    @Test
    void sinGestionarRolesNoSePuedeAsignarUnRolConPermisosDeMas() {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas");
        UUID masPoderoso = crearRol("Con servicios", "ver_citas", "gestionar_servicios");

        exigir(403, "SIN_PERMISO", guardar(USUARIOS + UUID.randomUUID(), gestor,
                cuerpo("lucia", masPoderoso, "abc12345")));
        exigir(403, guardar(USUARIOS + UUID.randomUUID(), gestor, cuerpo("lucia", rolAdmin, "abc12345")));

        assertEquals(0, filasConUsuario("lucia"));
    }

    @Test
    void sinGestionarRolesSiSePuedeAsignarUnRolConUnSubconjunto() {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas", "ver_panel");

        exigir(200, guardar(USUARIOS + UUID.randomUUID(), gestor, cuerpo("lucia", rolRecepcion, "abc12345")));
        // Un rol idéntico al propio también es un subconjunto.
        UUID igual = crearRol("Igual", "gestionar_usuarios", "ver_citas", "ver_panel");
        exigir(200, guardar(USUARIOS + UUID.randomUUID(), gestor, cuerpo("lola", igual, "abc12345")));
    }

    @Test
    void sinGestionarRolesTampocoSePuedeSubirDeRolAUnUsuarioExistente() {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas");
        UUID ana = crearUsuario("ana", rolRecepcion);

        exigir(403, guardar(USUARIOS + ana, gestor, cuerpo("ana", rolAdmin, null)));

        assertEquals(rolRecepcion.toString(), rolDe(ana).toLowerCase());
    }

    @Test
    void sinGestionarRolesNoSePuedeEditarAUnUsuarioConUnRolMasPoderosoNiCambiarleLaClave() {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas");
        String hashAntes = hashDe(admin);

        // El rol que quedaría es el del administrador: supera los permisos del actor.
        exigir(403, "SIN_PERMISO", guardar(USUARIOS + admin, gestor, cuerpo("admin", rolAdmin, "toma-el-control")));

        assertEquals(hashAntes, hashDe(admin));
    }

    @Test
    void conGestionarRolesSePuedeAsignarCualquierRol() {
        exigir(200, guardar(USUARIOS + UUID.randomUUID(), admin, cuerpo("lucia", rolAdmin, "abc12345")));
    }

    // --- Protecciones de último administrador y de uno mismo ---------------------------------------------

    @Test
    void nadieSeEliminaASiMismo() {
        crearUsuario("otro-admin", rolAdmin);

        exigir(409, "CONFLICTO", eliminar(USUARIOS + admin, admin));

        assertEquals(1, filasConUsuario("admin"));
    }

    @Test
    void noSeEliminaAlUltimoUsuarioConGestionarRoles() {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios");

        exigir(409, "CONFLICTO", eliminar(USUARIOS + admin, gestor));

        assertEquals(1, filasConUsuario("admin"));
    }

    @Test
    void sinGestionarRolesNoSePuedeEliminarAUnUsuarioConUnRolMasPoderoso() {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas");
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin); // queda otro gestor: no es el último

        exigir(403, "SIN_PERMISO", eliminar(USUARIOS + otroAdmin, gestor));

        assertEquals(1, filasConUsuario("otro-admin"));
    }

    @Test
    void sinGestionarRolesNoSePuedeDegradarAUnSuperiorNiCambiarleLaClaveEnLaMismaPeticion() {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas");
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);
        String hashAntes = hashDe(otroAdmin);

        // El nuevo rol (Recepción) es subconjunto del actor, pero el rol actual del objetivo no lo es.
        exigir(403, "SIN_PERMISO", guardar(USUARIOS + otroAdmin, gestor,
                cuerpo("otro-admin", rolRecepcion, "toma-el-control")));

        assertEquals(hashAntes, hashDe(otroAdmin));
        assertEquals(rolAdmin.toString(), rolDe(otroAdmin).toLowerCase());
    }

    @Test
    void sePuedeEliminarAUnGestorDeRolesSiQuedaOtro() {
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);

        exigir(204, eliminar(USUARIOS + otroAdmin, admin));

        assertEquals(1, contar("usuario"));
    }

    @Test
    void sePuedeEliminarAUnUsuarioComun() {
        UUID ana = crearUsuario("ana", rolRecepcion);

        exigir(204, eliminar(USUARIOS + ana, admin));

        assertEquals(0, filasConUsuario("ana"));
    }

    @Test
    void eliminarUnUsuarioInexistenteEs404() {
        exigir(404, "NO_ENCONTRADO", eliminar(USUARIOS + UUID.randomUUID(), admin));
    }

    @Test
    void noSeDegradaAlUltimoUsuarioConGestionarRoles() {
        UUID gestor = actorSinGestionarRoles("gestionar_usuarios", "ver_citas");

        exigir(409, "CONFLICTO", guardar(USUARIOS + admin, gestor, cuerpo("admin", rolRecepcion, null)));

        assertEquals(rolAdmin.toString(), rolDe(admin).toLowerCase());
    }

    @Test
    void elUnicoAdministradorNoPuedeDegradarseASiMismo() {
        exigir(409, "CONFLICTO", guardar(USUARIOS + admin, admin, cuerpo("admin", rolRecepcion, null)));
    }

    @Test
    void sePuedeDegradarAUnGestorDeRolesSiQuedaOtro() {
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);

        Resp r = exigir(200, guardar(USUARIOS + otroAdmin, admin, cuerpo("otro-admin", rolRecepcion, null)));
        assertEquals(rolRecepcion.toString(), r.en("rolId"));
    }

    @Test
    void dosDegradacionesSimultaneasNoDejanAlSistemaSinGestorDeRoles() throws Exception {
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            // Cada uno se degrada a sí mismo a la vez: solo uno puede salir bien.
            List<Callable<Integer>> tareas = List.of(
                    () -> guardar(USUARIOS + admin, admin, cuerpo("admin", rolRecepcion, null)).estado(),
                    () -> guardar(USUARIOS + otroAdmin, otroAdmin, cuerpo("otro-admin", rolRecepcion, null))
                            .estado());
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> futuro : hilos.invokeAll(tareas)) {
                estados.add(futuro.get());
            }
            estados.sort(null);
            assertEquals(List.of(200, 409), estados);
        } finally {
            hilos.shutdownNow();
        }
        assertEquals(1, consultarInt("SELECT COUNT(*) FROM usuario WHERE rol_id = ?", rolAdmin));
    }

    // --- Permisos de la ruta ------------------------------------------------------------------------------

    @Test
    void sinTokenEs401() {
        exigir(401, "NO_AUTENTICADO", get("/api/panel/usuarios").enviar());
        exigir(401, put(USUARIOS + UUID.randomUUID()).cuerpoCrudo("{}").enviar());
    }

    @Test
    void sinGestionarUsuariosTodoEs403AunqueSeaGestorDeRoles() {
        UUID soloRoles = crearUsuario("rosa", crearRol("Solo roles", "gestionar_roles"));
        UUID ana = crearUsuario("ana", rolRecepcion);

        exigir(403, "SIN_PERMISO", obtener("/api/panel/usuarios", soloRoles));
        exigir(403, guardar(USUARIOS + UUID.randomUUID(), soloRoles, cuerpo("lucia", rolRecepcion, "abc12345")));
        exigir(403, eliminar(USUARIOS + ana, soloRoles));

        assertEquals(3, contar("usuario"));
        assertEquals(0, filasConUsuario("lucia"));
    }

    // --- Ningún rastro de la clave ------------------------------------------------------------------------

    /** Junta todo lo registrado con java.util.logging y lo escrito en la salida estándar y de errores. */
    private static final class Captura extends Handler {
        private final StringBuilder texto = new StringBuilder();
        private final SimpleFormatter formato = new SimpleFormatter();

        @Override
        public synchronized void publish(LogRecord registro) {
            texto.append(formato.format(registro));
            if (registro.getThrown() != null) {
                StringWriter traza = new StringWriter();
                registro.getThrown().printStackTrace(new PrintWriter(traza));
                texto.append(traza);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    @Test
    void laClaveNuncaApareceEnRespuestasDeErrorNiEnElLog() {
        crearUsuario("marta", rolRecepcion);
        UUID sinRoles = actorSinGestionarRoles("gestionar_usuarios");
        String clave = "Clave-Ultra-Secreta-987";

        Logger raiz = Logger.getLogger("");
        Level nivelAntes = raiz.getLevel();
        Captura captura = new Captura();
        captura.setLevel(Level.ALL);
        raiz.addHandler(captura);
        raiz.setLevel(Level.ALL);
        PrintStream salidaAntes = System.out;
        PrintStream erroresAntes = System.err;
        ByteArrayOutputStream consola = new ByteArrayOutputStream();
        PrintStream copia = new PrintStream(consola, true, StandardCharsets.UTF_8);
        System.setOut(copia);
        System.setErr(copia);
        String respuestas;
        try {
            // Error de validación, de conflicto y de permiso, todos con la clave en el cuerpo de la petición.
            Map<String, Object> invalido = cuerpo("lucia", rolRecepcion, clave);
            invalido.put("correo", "malo");
            respuestas = guardar(USUARIOS + UUID.randomUUID(), admin, invalido).texto();
            respuestas += guardar(USUARIOS + UUID.randomUUID(), admin, cuerpo("marta", rolRecepcion, clave)).texto();
            respuestas += guardar(USUARIOS + UUID.randomUUID(), sinRoles, cuerpo("lola", rolAdmin, clave)).texto();
        } finally {
            System.setOut(salidaAntes);
            System.setErr(erroresAntes);
            raiz.removeHandler(captura);
            raiz.setLevel(nivelAntes);
        }

        assertFalse(respuestas.isEmpty());
        assertFalse(respuestas.contains(clave));
        assertFalse(captura.texto.toString().contains(clave));
        assertFalse(consola.toString(StandardCharsets.UTF_8).contains(clave));
    }

    @Test
    void laPeticionYElDtoNoMuestranLaClaveEnToString() {
        var peticion = new UsuarioPeticion("A", "B", "a@b.co", "1", "ab", "Clave-Que-No-Debe-Salir", UUID.randomUUID());

        assertFalse(peticion.toString().contains("Clave-Que-No-Debe-Salir"));
    }
}
