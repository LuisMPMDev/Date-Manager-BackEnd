package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.datemanager.infraestructura.persistencia.ErrorSql;
import com.datemanager.infraestructura.persistencia.ErroresBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class EnlacesControladorTest extends PruebaPanel {

    private static final String ENLACES = "/api/panel/enlaces";
    /** Lunes 9 de noviembre de 2026, 08:00 en Bogotá = 13:00 UTC. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 8, 0);
    private static final String AHORA_UTC = "2026-11-09T13:00:00Z";

    private UUID rolAdmin;
    private UUID admin;

    @BeforeEach
    void preparar() {
        fijarAhora(AHORA);
        rolAdmin = crearRol("Administrador", "ver_panel", "ver_citas", "gestionar_usuarios", "gestionar_roles",
                "gestionar_enlaces");
        admin = crearUsuario("admin", rolAdmin);
        ejecutar("UPDATE usuario SET nombre = 'Marta', apellido = 'Gómez' WHERE id = ?", admin);
    }

    // ---------- Utilidades ----------

    private static Resp crear(UUID actor, Map<String, ?> cuerpo) {
        return post(ENLACES).conToken(tokenDe(actor)).cuerpo(cuerpo).enviar();
    }

    private Resp crearOk(Map<String, ?> cuerpo) {
        return exigir(201, crear(admin, cuerpo));
    }

    private static Resp desactivar(UUID actor, String id) {
        return post(ENLACES + "/" + id + "/desactivar").conToken(tokenDe(actor)).enviar();
    }

    private static Map<String, Object> cuerpo(Integer duracion, Integer maxUsos) {
        Map<String, Object> c = new HashMap<>();
        if (duracion != null) {
            c.put("duracionMinutos", duracion);
        }
        if (maxUsos != null) {
            c.put("maxUsos", maxUsos);
        }
        return c;
    }

    private static String desactivadoEn() {
        return consultarTexto("SELECT CONVERT(NVARCHAR(30), desactivado_en, 121) FROM enlace_invitacion");
    }

    // ---------- Crear ----------

    @Test
    void crearSinDatosDura24HorasSinLimiteDeUsos() {
        Resp enlace = exigir(201, crear(admin, Map.of()));
        assertFalse(enlace.texto("id").isEmpty());
        assertEquals("Activo", enlace.en("estado"));
        assertEquals(0, enlace.numero("usos").intValue());
        assertFalse(enlace.existe("maxUsos"));
        assertEquals(AHORA_UTC, enlace.en("creadoEn"));
        assertEquals("2026-11-10T13:00:00Z", enlace.en("venceEn"));
        assertEquals(admin.toString(), enlace.en("creadoPor"));
        assertEquals("Marta Gómez", enlace.en("creadoPorNombre"));

        String codigo = enlace.texto("codigo");
        assertTrue(codigo.length() >= 22 && codigo.matches("[A-Za-z0-9_-]+"), codigo);
        assertEquals("/agendar/" + codigo, enlace.en("ruta"));
        Map<String, Object> fila = filas("SELECT * FROM enlace_invitacion").get(0);
        assertEquals(codigo, fila.get("codigo"));
        assertEquals(0, fila.get("usos"));
        assertNull(fila.get("max_usos"));
        assertNull(fila.get("desactivado_en"));
        assertTrue(fila.get("creado_por").toString().equalsIgnoreCase(admin.toString()));
    }

    @Test
    void crearSinCuerpoEsComoUnCuerpoVacio() {
        Resp r = exigir(201, post(ENLACES).conToken(tokenDe(admin)).enviar());
        assertEquals("2026-11-10T13:00:00Z", r.en("venceEn"));
        assertFalse(r.existe("maxUsos"));
    }

    @Test
    void crearConDuracionYMaximoDeUsos() {
        Resp r = exigir(201, crear(admin, cuerpo(60, 3)));
        assertEquals(3, r.numero("maxUsos").intValue());
        assertEquals("2026-11-09T14:00:00Z", r.en("venceEn"));
        assertEquals("2026-12-09T13:00:00Z", exigir(201, crear(admin, cuerpo(43200, 1))).en("venceEn"));
    }

    @Test
    void duracionFueraDeRangoOMaximoDeUsosCeroSon400YNoSeGuardaNada() {
        assertTrue(exigir(400, "VALIDACION", crear(admin, cuerpo(59, null))).existe("campos.duracionMinutos"));
        assertTrue(exigir(400, crear(admin, cuerpo(43201, null))).existe("campos.duracionMinutos"));
        assertTrue(exigir(400, "VALIDACION", crear(admin, cuerpo(null, 0))).existe("campos.maxUsos"));
        assertTrue(exigir(400, crear(admin, cuerpo(1440, -2))).existe("campos.maxUsos"));
        exigir(400, "VALIDACION", crear(admin, Map.of("duracionMinutos", "mucho")));

        assertEquals(0, contar("enlace_invitacion"));
    }

    @Test
    void cadaEnlaceTieneUnCodigoDistinto() {
        String a = crearOk(Map.of()).texto("codigo");
        String b = crearOk(Map.of()).texto("codigo");

        assertNotEquals(a, b);
    }

    // ---------- Listar ----------

    @Test
    void listarDevuelveLosMasNuevosPrimeroConEstadoUsosVencimientoYCreador() {
        UUID otro = crearUsuario("luis", rolAdmin);
        ejecutar("UPDATE usuario SET nombre = 'Luis', apellido = 'Paz' WHERE id = ?", otro);
        Resp viejo = crearOk(cuerpo(60, 2));
        fijarAhora(AHORA.plusMinutes(10));
        String nuevo = exigir(201, crear(otro, Map.of())).texto("id");
        ejecutar("UPDATE enlace_invitacion SET usos = 1 WHERE id = ?", UUID.fromString(viejo.texto("id")));
        fijarAhora(AHORA.plusMinutes(60)); // el primero vence justo ahora

        Resp r = exigir(200, obtener(ENLACES, admin));
        assertEquals(2, r.lista("$").size());
        assertEquals(nuevo, r.en("$[0].id"));
        assertEquals("Activo", r.en("$[0].estado"));
        assertEquals(otro.toString(), r.en("$[0].creadoPor"));
        assertEquals("Luis Paz", r.en("$[0].creadoPorNombre"));
        assertEquals("2026-11-09T13:10:00Z", r.en("$[0].creadoEn"));
        assertEquals(viejo.en("id"), r.en("$[1].id"));
        assertEquals("Vencido", r.en("$[1].estado"));
        assertEquals(1, r.numero("$[1].usos").intValue());
        assertEquals(2, r.numero("$[1].maxUsos").intValue());
        assertEquals("2026-11-09T14:00:00Z", r.en("$[1].venceEn"));
        assertEquals(viejo.en("codigo"), r.en("$[1].codigo"));
        assertEquals("/agendar/" + viejo.texto("codigo"), r.en("$[1].ruta"));
        assertEquals("Marta Gómez", r.en("$[1].creadoPorNombre"));
    }

    @Test
    void listarMuestraAgotadoYDesactivado() {
        Resp agotado = crearOk(cuerpo(null, 1));
        fijarAhora(AHORA.plusMinutes(1));
        Resp desactivado = crearOk(Map.of());
        ejecutar("UPDATE enlace_invitacion SET usos = 1 WHERE id = ?", UUID.fromString(agotado.texto("id")));
        exigir(200, desactivar(admin, desactivado.texto("id")));

        Resp r = obtener(ENLACES, admin);
        assertEquals("Desactivado", r.en("$[0].estado"));
        assertEquals("Agotado", r.en("$[1].estado"));
    }

    // ---------- Desactivar ----------

    @Test
    void desactivarEsIdempotenteYConservaLaPrimeraFecha() {
        String id = crearOk(Map.of()).texto("id");
        fijarAhora(AHORA.plusMinutes(5));

        Resp r = exigir(200, desactivar(admin, id));
        assertEquals("Desactivado", r.en("estado"));
        assertEquals(id, r.en("id"));
        String primera = desactivadoEn();
        fijarAhora(AHORA.plusMinutes(30));
        assertEquals("Desactivado", exigir(200, desactivar(admin, id)).en("estado"));

        assertEquals(primera, desactivadoEn());
        assertTrue(primera.startsWith("2026-11-09 13:05:00"), primera);
    }

    @Test
    void desactivarUnoVencidoLoDejaDesactivado() {
        String id = crearOk(cuerpo(60, null)).texto("id");
        fijarAhora(AHORA.plusDays(3));

        assertEquals("Desactivado", exigir(200, desactivar(admin, id)).en("estado"));
    }

    @Test
    void desactivarUnoInexistenteEs404() {
        exigir(404, "NO_ENCONTRADO", desactivar(admin, UUID.randomUUID().toString()));
    }

    // ---------- Permisos ----------

    @Test
    void sinGestionarEnlacesTodoEs403() {
        String id = crearOk(Map.of()).texto("id");
        UUID sinPermiso = crearUsuario("recepcion", crearRol("Recepción", "ver_panel", "ver_citas",
                "gestionar_citas", "gestionar_servicios", "gestionar_clientes", "gestionar_usuarios"));

        exigir(403, "SIN_PERMISO", crear(sinPermiso, Map.of()));
        exigir(403, obtener(ENLACES, sinPermiso));
        exigir(403, desactivar(sinPermiso, id));

        assertEquals(1, contar("enlace_invitacion"));
        assertNull(desactivadoEn());
    }

    @Test
    void soloGestionarEnlacesBasta() {
        UUID soloEnlaces = crearUsuario("enlaces", crearRol("Enlaces", "gestionar_enlaces"));

        String id = exigir(201, crear(soloEnlaces, Map.of())).texto("id");
        assertEquals(1, exigir(200, obtener(ENLACES, soloEnlaces)).lista("$").size());
        exigir(200, desactivar(soloEnlaces, id));
    }

    @Test
    void sinTokenOConTokenInvalidoEs401() {
        String id = crearOk(Map.of()).texto("id");

        exigir(401, "NO_AUTENTICADO", get(ENLACES).enviar());
        exigir(401, post(ENLACES).cuerpoCrudo("{}").enviar());
        exigir(401, post(ENLACES + "/" + id + "/desactivar").enviar());
        exigir(401, get(ENLACES).cabecera("Authorization", "Bearer basura.que.no-vale").enviar());
        exigir(401, post(ENLACES).cabecera("Authorization", "Bearer basura").cuerpoCrudo("{}").enviar());

        assertEquals(1, contar("enlace_invitacion"));
    }

    // ---------- Usuarios que crearon enlaces ----------

    @Test
    void noSeEliminaAUnUsuarioQueCreoEnlaces() {
        UUID luis = crearUsuario("luis", rolAdmin);
        exigir(201, crear(luis, Map.of()));

        Resp r = exigir(409, "CONFLICTO", eliminar("/api/panel/usuarios/" + luis, admin));
        assertTrue(r.texto("mensaje").contains("enlaces"));

        assertEquals(1, consultarInt("SELECT COUNT(*) FROM usuario WHERE id = ?", luis));
        assertEquals(1, contar("enlace_invitacion"));
    }

    @Test
    void lasProteccionesDeUsuariosSiguenAntesQueLaDeEnlaces() {
        // Un actor sin gestionar_roles no puede tocar a un superior: 403, aunque el superior tenga enlaces.
        UUID gestor = crearUsuario("gestor", crearRol("Gestor", "gestionar_usuarios"));
        UUID otroAdmin = crearUsuario("otro-admin", rolAdmin);
        exigir(201, crear(otroAdmin, Map.of()));

        exigir(403, eliminar("/api/panel/usuarios/" + otroAdmin, gestor));
        // Y nadie se elimina a sí mismo (409 de siempre).
        exigir(409, eliminar("/api/panel/usuarios/" + admin, admin));

        assertEquals(3, contar("usuario"));
    }

    @Test
    void unUsuarioSinEnlacesSeSigueEliminando() {
        UUID luis = crearUsuario("luis", rolAdmin);
        exigir(201, crear(admin, Map.of()));

        exigir(204, eliminar("/api/panel/usuarios/" + luis, admin));
    }

    // ---------- Restricciones de la base ----------

    private void insertarEnlace(UUID id, String codigo, Integer maxUsos, int usos) {
        ejecutar("INSERT INTO enlace_invitacion (id, codigo, creado_por, creado_en, vence_en, max_usos, usos) "
                + "VALUES (?, ?, ?, '2026-11-09T13:00:00', '2026-11-10T13:00:00', ?, ?)",
                id, codigo, admin, maxUsos, usos);
    }

    private static void violaRestriccion(Executable sql) {
        ErrorSql e = assertThrows(ErrorSql.class, sql);
        assertTrue(ErroresBase.esViolacionDeRestriccion(e), "debía ser una violación de restricción");
    }

    @Test
    void laBaseImponeSusRestricciones() {
        insertarEnlace(UUID.randomUUID(), "AAAAAAAAAAAAAAAAAAAAAA", null, 0);

        // Código único.
        violaRestriccion(() -> insertarEnlace(UUID.randomUUID(), "AAAAAAAAAAAAAAAAAAAAAA", null, 0));
        // El código distingue mayúsculas (Base64 URL): otro en minúsculas es otro código.
        insertarEnlace(UUID.randomUUID(), "aaaaaaaaaaaaaaaaaaaaaa", null, 0);
        // max_usos nulo o >= 1; usos >= 0 y nunca por encima del máximo.
        violaRestriccion(() -> insertarEnlace(UUID.randomUUID(), "BBBBBBBBBBBBBBBBBBBBBB", 0, 0));
        violaRestriccion(() -> insertarEnlace(UUID.randomUUID(), "CCCCCCCCCCCCCCCCCCCCCC", null, -1));
        violaRestriccion(() -> insertarEnlace(UUID.randomUUID(), "DDDDDDDDDDDDDDDDDDDDDD", 1, 2));
        // creado_por es una llave foránea a usuario.
        violaRestriccion(() -> ejecutar("INSERT INTO enlace_invitacion "
                + "(id, codigo, creado_por, creado_en, vence_en, usos) VALUES (?, 'EEEEEEEEEEEEEEEEEEEEEE', ?, "
                + "'2026-11-09T13:00:00', '2026-11-10T13:00:00', 0)", UUID.randomUUID(), UUID.randomUUID()));
        // Borrar el usuario por SQL choca con la llave (sin cascada).
        violaRestriccion(() -> ejecutar("DELETE FROM usuario WHERE id = ?", admin));

        assertEquals(2, contar("enlace_invitacion"));
    }

    @Test
    void laCitaSoloPuedeApuntarAUnEnlaceExistente() {
        UUID servicio = UUID.randomUUID();
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'S', 30, 1, 0)",
                servicio);
        UUID cliente = UUID.randomUUID();
        ejecutar("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, 'A', 'B', '1', 'a@x.co')",
                cliente);
        UUID enlace = UUID.randomUUID();
        insertarEnlace(enlace, "AAAAAAAAAAAAAAAAAAAAAA", null, 0);
        String sql = "INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en, "
                + "enlace_id) VALUES (?, ?, ?, '2026-11-10', ?, ?, 'Confirmada', SYSUTCDATETIME(), ?)";

        ejecutar(sql, UUID.randomUUID(), servicio, cliente, "09:00", "09:30", enlace);
        violaRestriccion(() -> ejecutar(sql, UUID.randomUUID(), servicio, cliente, "10:00", "10:30",
                UUID.randomUUID()));
        // Y el enlace con citas no se puede borrar (sin cascada).
        violaRestriccion(() -> ejecutar("DELETE FROM enlace_invitacion WHERE id = ?", enlace));
    }
}
