package com.datemanager.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.datemanager.CodificadorFalso;
import com.datemanager.PruebaAdministracion;
import com.datemanager.aplicacion.UsuariosServicio.Datos;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Usuario;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsuariosServicioTest extends PruebaAdministracion {

    private static final String REPETIDO = "Ya existe otro usuario con ese nombre de usuario.";

    private UUID rolAdmin;
    private UUID rolRecepcion;
    private UUID admin;

    @BeforeEach
    void crearAdministrador() {
        rolAdmin = crearRol("Administrador", Permiso.VER_PANEL, Permiso.GESTIONAR_USUARIOS, Permiso.GESTIONAR_ROLES,
                Permiso.VER_CITAS);
        rolRecepcion = crearRol("Recepción", Permiso.VER_CITAS);
        admin = crearUsuario("admin", rolAdmin);
    }

    private static Datos datos(String usuario, UUID rolId, String clave) {
        return new Datos("Ana", "Pérez", "ana@correo.com", "300 111", usuario, clave, rolId);
    }

    /** Un usuario con gestionar_usuarios pero sin gestionar_roles, más los permisos indicados. */
    private UUID actorSinGestionarRoles(Permiso... permisos) {
        UUID rol = crearRol("Gestor de usuarios " + nuevoId(), permisos);
        return crearUsuario("gestor-" + nuevoId().toString().substring(0, 8), rol);
    }

    // ---------- Guardar: datos ----------

    @Test
    void crearUnUsuarioNuevoGuardaSoloElHashDeLaClave() {
        UUID id = nuevoId();

        Usuario creado = usuariosServicio.guardar(id, datos("ana", rolRecepcion, "clave-secreta"), admin);

        assertEquals(new Usuario(id, "Ana", "Pérez", "ana@correo.com", "300 111", "ana", rolRecepcion), creado);
        assertEquals(codificador.codificar("clave-secreta"), hashDe(id));
        assertNotEquals("clave-secreta", hashDe(id));
    }

    @Test
    void crearSinClaveEsValidacionConElCampo() {
        for (String clave : new String[] { null, "", "   " }) {
            ErrorNegocio error = exigirError(CodigoError.VALIDACION,
                    () -> usuariosServicio.guardar(nuevoId(), datos("ana", rolRecepcion, clave), admin));

            assertEquals(Map.of("clave", "Escribe la clave."), error.campos());
        }
        assertEquals(0, filasConUsuario("ana"));
    }

    @Test
    void editarConClaveVaciaOAusenteConservaElHash() {
        UUID id = nuevoId();
        usuariosServicio.guardar(id, datos("ana", rolRecepcion, "clave-1"), admin);
        String antes = hashDe(id);

        usuariosServicio.guardar(id, new Datos("Ana María", "Pérez", "ana@correo.com", "", "ana", null, rolRecepcion),
                admin);
        usuariosServicio.guardar(id, new Datos("Ana María", "Pérez", "ana@correo.com", "", "ana", "  ", rolRecepcion),
                admin);

        assertEquals(antes, hashDe(id));
        assertEquals("Ana María", consultarTexto("SELECT nombre FROM usuario WHERE id = ?", id));
        assertEquals(1, filasConUsuario("ana"));
    }

    @Test
    void editarConClaveNuevaCambiaElHash() {
        UUID id = nuevoId();
        usuariosServicio.guardar(id, datos("ana", rolRecepcion, "clave-1"), admin);

        usuariosServicio.guardar(id, datos("ana", rolRecepcion, "clave-2"), admin);

        assertEquals(codificador.codificar("clave-2"), hashDe(id));
    }

    @Test
    void usuarioYCorreoSeNormalizanYLosTextosSeRecortan() {
        UUID id = nuevoId();

        Usuario creado = usuariosServicio.guardar(id,
                new Datos(" Ana ", " Pérez ", "  ANA@Correo.COM ", null, "  CarLa  ", "clave", rolRecepcion), admin);

        assertEquals("carla", creado.usuario());
        assertEquals("ana@correo.com", creado.correo());
        assertEquals("Ana", creado.nombre());
        assertEquals("Pérez", creado.apellido());
        assertEquals("", creado.telefono());
        assertEquals("carla", consultarTexto("SELECT usuario FROM usuario WHERE id = ?", id));
    }

    @Test
    void textoConTildesEnyeYEmojiSeGuardaYSeListaIntacto() {
        UUID id = nuevoId();

        usuariosServicio.guardar(id, new Datos("José 😀", "Ñandú Peña", "jose@correo.com", "☎ 300", "josé_ñ",
                "clave-ñ-😀", rolRecepcion), admin);

        Usuario listado = usuariosServicio.listar().stream().filter(u -> u.id().equals(id)).findFirst().orElseThrow();
        assertEquals("José 😀", listado.nombre());
        assertEquals("Ñandú Peña", listado.apellido());
        assertEquals("josé_ñ", listado.usuario());
        assertEquals("☎ 300", listado.telefono());
        assertEquals("José 😀", consultarTexto("SELECT nombre FROM usuario WHERE id = ?", id));
        assertEquals(codificador.codificar("clave-ñ-😀"), hashDe(id));
    }

    @Test
    void listarVienePorNombreDeUsuarioYNoIncluyeLaClave() {
        usuariosServicio.guardar(nuevoId(), datos("carla", rolRecepcion, "c"), admin);
        usuariosServicio.guardar(nuevoId(), datos("beto", rolRecepcion, "b"), admin);

        List<Usuario> lista = usuariosServicio.listar();

        assertEquals(List.of("admin", "beto", "carla"), lista.stream().map(Usuario::usuario).toList());
        assertFalse(lista.toString().contains(CodificadorFalso.hash("c").substring(7)));
    }

    // ---------- Guardar: validación ----------

    @Test
    void correoInvalidoEsValidacionConElCampo() {
        ErrorNegocio error = exigirError(CodigoError.VALIDACION, () -> usuariosServicio.guardar(nuevoId(),
                new Datos("Ana", "Pérez", "no-es-correo", "", "ana", "clave", rolRecepcion), admin));

        assertEquals(Map.of("correo", "Escribe un correo válido, por ejemplo nombre@correo.com."), error.campos());
    }

    @Test
    void unRolInexistenteOAusenteEsValidacionConElCampoRolId() {
        for (UUID rol : new UUID[] { null, nuevoId() }) {
            ErrorNegocio error = exigirError(CodigoError.VALIDACION,
                    () -> usuariosServicio.guardar(nuevoId(), datos("ana", rol, "clave"), admin));

            assertEquals(Map.of("rolId", "Elige un rol."), error.campos());
        }
    }

    @Test
    void unaClaveDeMasDe72BytesEsValidacion() {
        String ochentaBytes = "ñ".repeat(37); // 74 bytes en UTF-8

        ErrorNegocio error = exigirError(CodigoError.VALIDACION,
                () -> usuariosServicio.guardar(nuevoId(), datos("ana", rolRecepcion, ochentaBytes), admin));

        assertEquals(Map.of("clave", "La clave no puede pasar de 72 bytes."), error.campos());
        usuariosServicio.guardar(nuevoId(), datos("ana", rolRecepcion, "ñ".repeat(36)), admin); // 72 bytes: vale
    }

    @Test
    void unActorQueNoExisteNoEstaAutenticado() {
        exigirError(CodigoError.NO_AUTENTICADO,
                () -> usuariosServicio.guardar(nuevoId(), datos("ana", rolRecepcion, "clave"), nuevoId()));
        UUID ana = crearUsuario("ana", rolRecepcion);
        exigirError(CodigoError.NO_AUTENTICADO, () -> usuariosServicio.eliminar(ana, nuevoId()));
    }

    // ---------- Guardar: usuario repetido ----------

    @Test
    void usuarioDuplicadoSinImportarEspaciosNiMayusculasEsConflictoYNuncaHayDosFilas() {
        usuariosServicio.guardar(nuevoId(), datos("carla", rolRecepcion, "clave"), admin);

        ErrorNegocio error = exigirError(CodigoError.CONFLICTO,
                () -> usuariosServicio.guardar(nuevoId(), datos("  CARLA ", rolRecepcion, "clave"), admin));

        assertEquals(REPETIDO, error.getMessage());
        assertEquals(1, filasConUsuario("carla"));
    }

    @Test
    void cambiarElUsuarioDeOtroAUnoQueYaExisteEsConflicto() {
        UUID ana = nuevoId();
        usuariosServicio.guardar(ana, datos("ana", rolRecepcion, "clave"), admin);
        usuariosServicio.guardar(nuevoId(), datos("beto", rolRecepcion, "clave"), admin);

        exigirError(CodigoError.CONFLICTO, () -> usuariosServicio.guardar(ana, datos("beto", rolRecepcion, null), admin));

        assertEquals(1, filasConUsuario("ana"));
    }

    @Test
    void conservarElPropioUsuarioConOtraMayusculaNoEsDuplicado() {
        UUID ana = nuevoId();
        usuariosServicio.guardar(ana, datos("ana", rolRecepcion, "clave"), admin);

        usuariosServicio.guardar(ana, datos("ANA", rolRecepcion, null), admin);

        assertEquals(1, filasConUsuario("ana"));
    }

    /**
     * Si la lectura previa no ve al otro usuario (otra instancia que escribe a la vez), la restricción única de la
     * base es la última defensa y también sale como conflicto con el mismo mensaje.
     */
    @Test
    void siLaLecturaNoVioAlDuplicadoLaRestriccionUnicaDeLaBaseTambienEsConflicto() {
        usuariosServicio.guardar(nuevoId(), datos("carla", rolRecepcion, "clave"), admin);
        UsuarioRepositorio ciego = new UsuarioRepositorio(tx) {
            @Override
            public Optional<UsuarioConClave> buscarPorUsuario(String usuario) {
                return Optional.empty();
            }
        };
        UsuariosServicio servicio = new UsuariosServicio(tx, ciego, roles, codificador, bloqueo, enlaces);

        ErrorNegocio error = exigirError(CodigoError.CONFLICTO,
                () -> servicio.guardar(nuevoId(), datos("Carla", rolRecepcion, "clave"), admin));

        assertEquals(REPETIDO, error.getMessage());
        assertEquals(1, filasConUsuario("carla"));
        // La transacción se revirtió completa y la conexión sigue sirviendo.
        usuariosServicio.guardar(nuevoId(), datos("otra", rolRecepcion, "clave"), admin);
    }

    @Test
    void dosPeticionesSimultaneasConElMismoUsuarioDejanUnaSolaFila() throws Exception {
        List<Object> resultados = enParalelo(
                () -> usuariosServicio.guardar(nuevoId(), datos("carla", rolRecepcion, "abc12345"), admin),
                () -> usuariosServicio.guardar(nuevoId(), datos(" Carla ", rolRecepcion, "abc12345"), admin));

        assertEquals(1, resultados.stream().filter(r -> r instanceof Usuario).count(), "una sale bien: " + resultados);
        assertTrue(resultados.stream().filter(r -> !(r instanceof Usuario))
                .allMatch(r -> r instanceof ErrorNegocio e && e.codigo() == CodigoError.CONFLICTO), "la otra es 409");
        assertEquals(1, filasConUsuario("carla"));
    }

    // ---------- Guardar: escalada de permisos ----------

    @Test
    void sinGestionarRolesNoSePuedeAsignarUnRolConPermisosDeMas() {
        UUID gestor = actorSinGestionarRoles(Permiso.GESTIONAR_USUARIOS, Permiso.VER_CITAS);

        exigirError(CodigoError.SIN_PERMISO,
                () -> usuariosServicio.guardar(nuevoId(), datos("ana", rolAdmin, "clave"), gestor));

        assertEquals(0, filasConUsuario("ana"));
    }

    @Test
    void sinGestionarRolesSiSePuedeAsignarUnRolConUnSubconjunto() {
        UUID gestor = actorSinGestionarRoles(Permiso.GESTIONAR_USUARIOS, Permiso.VER_CITAS);

        usuariosServicio.guardar(nuevoId(), datos("ana", rolRecepcion, "clave"), gestor);

        assertEquals(1, filasConUsuario("ana"));
    }

    @Test
    void sinGestionarRolesTampocoSePuedeSubirDeRolAUnUsuarioExistente() {
        UUID gestor = actorSinGestionarRoles(Permiso.GESTIONAR_USUARIOS, Permiso.VER_CITAS);
        UUID ana = crearUsuario("ana", rolRecepcion);

        exigirError(CodigoError.SIN_PERMISO, () -> usuariosServicio.guardar(ana, datos("ana", rolAdmin, null), gestor));

        assertEquals(rolRecepcion.toString(),
                consultarTexto("SELECT CAST(rol_id AS NVARCHAR(36)) FROM usuario WHERE id = ?", ana).toLowerCase());
    }

    @Test
    void sinGestionarRolesNoSePuedeEditarAUnUsuarioConUnRolMasPoderosoNiCambiarleLaClave() {
        UUID gestor = actorSinGestionarRoles(Permiso.GESTIONAR_USUARIOS, Permiso.VER_CITAS);
        String hashAntes = hashDe(admin);

        // El rol que quedaría es el del administrador: supera los permisos del actor.
        exigirError(CodigoError.SIN_PERMISO,
                () -> usuariosServicio.guardar(admin, datos("admin", rolAdmin, "toma-el-control"), gestor));

        assertEquals(hashAntes, hashDe(admin));
    }

    @Test
    void sinGestionarRolesNoSePuedeDegradarAUnSuperiorNiCambiarleLaClaveEnLaMismaPeticion() {
        UUID gestor = actorSinGestionarRoles(Permiso.GESTIONAR_USUARIOS, Permiso.VER_CITAS);
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);
        String hashAntes = hashDe(otroAdmin);

        // El nuevo rol (Recepción) es subconjunto del actor, pero el rol actual del objetivo no lo es.
        exigirError(CodigoError.SIN_PERMISO,
                () -> usuariosServicio.guardar(otroAdmin, datos("otro-admin", rolRecepcion, "toma-el-control"), gestor));

        assertEquals(hashAntes, hashDe(otroAdmin));
        assertEquals(1, consultarInt("SELECT COUNT(*) FROM usuario WHERE id = ? AND rol_id = ?", otroAdmin, rolAdmin));
    }

    @Test
    void conGestionarRolesSePuedeAsignarCualquierRol() {
        usuariosServicio.guardar(nuevoId(), datos("ana", rolAdmin, "clave"), admin);

        assertEquals(1, filasConUsuario("ana"));
    }

    // ---------- Guardar: último gestor de roles ----------

    @Test
    void noSeDegradaAlUltimoUsuarioConGestionarRoles() {
        ErrorNegocio error = exigirError(CodigoError.CONFLICTO,
                () -> usuariosServicio.guardar(admin, datos("admin", rolRecepcion, null), admin));

        assertEquals("Debe quedar al menos un usuario que pueda gestionar roles.", error.getMessage());
        assertEquals(1, gestoresDeRoles());
    }

    @Test
    void sePuedeDegradarAUnGestorDeRolesSiQuedaOtro() {
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);

        usuariosServicio.guardar(otroAdmin, datos("otro-admin", rolRecepcion, null), admin);

        assertEquals(1, gestoresDeRoles());
    }

    @Test
    void dosDegradacionesSimultaneasNoDejanAlSistemaSinGestorDeRoles() throws Exception {
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);

        // Cada uno se degrada a sí mismo a la vez: solo uno puede salir bien.
        List<Object> resultados = enParalelo(
                () -> usuariosServicio.guardar(admin, datos("admin", rolRecepcion, null), admin),
                () -> usuariosServicio.guardar(otroAdmin, datos("otro-admin", rolRecepcion, null), otroAdmin));

        assertEquals(1, resultados.stream().filter(r -> r instanceof Usuario).count(), "una sale bien: " + resultados);
        assertTrue(resultados.stream().filter(r -> !(r instanceof Usuario))
                .allMatch(r -> r instanceof ErrorNegocio e && e.codigo() == CodigoError.CONFLICTO), "la otra es 409");
        assertEquals(1, consultarInt("SELECT COUNT(*) FROM usuario WHERE rol_id = ?", rolAdmin));
    }

    // ---------- Eliminar ----------

    @Test
    void nadieSeEliminaASiMismo() {
        crearUsuario("otro-admin", rolAdmin);

        ErrorNegocio error = exigirError(CodigoError.CONFLICTO, () -> usuariosServicio.eliminar(admin, admin));

        assertEquals("No puedes eliminar tu propio usuario.", error.getMessage());
    }

    @Test
    void noSeEliminaAlUltimoUsuarioConGestionarRoles() {
        UUID gestor = actorSinGestionarRoles(Permiso.GESTIONAR_USUARIOS);

        ErrorNegocio error = exigirError(CodigoError.CONFLICTO, () -> usuariosServicio.eliminar(admin, gestor));

        assertEquals("Debe quedar al menos un usuario que pueda gestionar roles.", error.getMessage());
        assertEquals(1, filasConUsuario("admin"));
    }

    @Test
    void sinGestionarRolesNoSePuedeEliminarAUnUsuarioConUnRolMasPoderoso() {
        UUID gestor = actorSinGestionarRoles(Permiso.GESTIONAR_USUARIOS, Permiso.VER_CITAS);
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin); // queda otro gestor: no es el último

        exigirError(CodigoError.SIN_PERMISO, () -> usuariosServicio.eliminar(otroAdmin, gestor));

        assertEquals(1, filasConUsuario("otro-admin"));
    }

    @Test
    void sePuedeEliminarAUnGestorDeRolesSiQuedaOtro() {
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);

        usuariosServicio.eliminar(otroAdmin, admin);

        assertEquals(0, filasConUsuario("otro-admin"));
        assertEquals(1, gestoresDeRoles());
    }

    @Test
    void sePuedeEliminarAUnUsuarioComun() {
        UUID ana = crearUsuario("ana", rolRecepcion);

        usuariosServicio.eliminar(ana, admin);

        assertEquals(0, filasConUsuario("ana"));
    }

    @Test
    void eliminarUnUsuarioInexistenteEsNoEncontrado() {
        ErrorNegocio error = exigirError(CodigoError.NO_ENCONTRADO, () -> usuariosServicio.eliminar(nuevoId(), admin));

        assertEquals("El usuario no existe.", error.getMessage());
    }

    @Test
    void unUsuarioQueCreoEnlacesNoSeEliminaYSeConserva() {
        UUID ana = crearUsuario("ana", rolRecepcion);
        ejecutar("""
                INSERT INTO enlace_invitacion (id, codigo, creado_por, creado_en, vence_en, max_usos, usos)
                VALUES (?, 'codigo-de-prueba', ?, SYSUTCDATETIME(), DATEADD(DAY, 1, SYSUTCDATETIME()), NULL, 0)
                """, nuevoId(), ana);
        assertTrue(tx.leer(() -> enlaces.existeCreadoPor(ana)));
        assertFalse(tx.leer(() -> enlaces.existeCreadoPor(admin)));

        ErrorNegocio error = exigirError(CodigoError.CONFLICTO, () -> usuariosServicio.eliminar(ana, admin));

        assertEquals("No se puede eliminar un usuario que creó enlaces de invitación.", error.getMessage());
        assertEquals(1, filasConUsuario("ana"));
    }

    @Test
    void laClaveNoApareceEnElTextoDeLosDatos() {
        Datos datos = datos("ana", rolRecepcion, "clave-que-no-debe-salir");

        assertFalse(datos.toString().contains("clave-que-no-debe-salir"));
        assertEquals("Datos[usuario=ana, rolId=" + rolRecepcion + "]", datos.toString());
    }

}
