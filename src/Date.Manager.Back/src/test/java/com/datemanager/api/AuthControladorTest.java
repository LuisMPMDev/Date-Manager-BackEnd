package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.datemanager.PruebaIntegracion;
import com.datemanager.infraestructura.seguridad.Bcrypt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthControladorTest extends PruebaIntegracion {

    private static final String LOGIN = "/api/auth/login";
    private static final String YO = "/api/auth/yo";
    private static final String PROTEGIDA = "/api/panel/_prueba/citas";
    private static final String PROBLEMA = "application/problem+json";

    /** Un solo hash BCrypt real para toda la clase (calcularlo es lo caro). */
    private static final String HASH_DEMO = new Bcrypt().codificar("demo123");

    private UUID rolId;
    private UUID usuarioId;

    @BeforeEach
    void crearUsuario() {
        rolId = UUID.randomUUID();
        ejecutar("INSERT INTO rol (id, nombre, descripcion) VALUES (?, ?, ?)", rolId, "Recepción", "Agenda");
        // Se insertan en desorden para comprobar que la respuesta los ordena según el catálogo.
        ejecutar("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, 'ver_citas')", rolId);
        ejecutar("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, 'ver_panel')", rolId);
        usuarioId = UUID.randomUUID();
        ejecutar("""
                INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id)
                VALUES (?, 'Marta', 'Gómez', 'marta@example.com', '300 555 0201', 'marta', ?, ?)
                """, usuarioId, HASH_DEMO, rolId);
    }

    private static Resp login(String usuario, String clave) {
        return post(LOGIN).cuerpo(Map.of("usuario", usuario, "clave", clave)).enviar();
    }

    private static String token() {
        Resp r = login("marta", "demo123");
        assertEquals(200, r.estado(), r::texto);
        return r.texto("token");
    }

    private static Resp conToken(String ruta, String token) {
        return get(ruta).conToken(token).enviar();
    }

    private static void exigir(int estado, String codigo, Resp r) {
        assertEquals(estado, r.estado(), r::texto);
        assertEquals(codigo, r.en("codigo"), r::texto);
    }

    @Test
    void loginCorrectoDevuelveTokenUsuarioSinClaveYPermisosDelRolEnOrdenDelCatalogo() {
        Resp r = login("marta", "demo123");

        assertEquals(200, r.estado(), r::texto);
        assertFalse(r.texto("token").isEmpty());
        assertEquals(usuarioId.toString(), r.en("usuario.id"));
        assertEquals("Marta", r.en("usuario.nombre"));
        assertEquals("Gómez", r.en("usuario.apellido"));
        assertEquals("marta@example.com", r.en("usuario.correo"));
        assertEquals("300 555 0201", r.en("usuario.telefono"));
        assertEquals("marta", r.en("usuario.usuario"));
        assertEquals(rolId.toString(), r.en("usuario.rolId"));
        assertFalse(r.existe("usuario.clave"));
        assertFalse(r.existe("usuario.claveHash"));
        assertEquals(rolId.toString(), r.en("rol.id"));
        assertEquals("Recepción", r.en("rol.nombre"));
        assertEquals("Agenda", r.en("rol.descripcion"));
        assertEquals(List.of("ver_panel", "ver_citas"), r.en("rol.permisos"));
        assertFalse(r.texto().contains("$2"));
        assertTrue(r.cabecera("Content-Type").startsWith("application/json"));
    }

    @Test
    void elUsuarioConEspaciosYMayusculasEntraIgual() {
        Resp r = login(" MARTA ", "demo123");

        assertEquals(200, r.estado(), r::texto);
        assertEquals(usuarioId.toString(), r.en("usuario.id"));
    }

    @Test
    void laClaveEsExacta() {
        assertEquals(401, login("marta", "DEMO123").estado());
        assertEquals(401, login("marta", " demo123").estado());
    }

    @Test
    void claveMalaYUsuarioInexistenteRespondenElMismo401() {
        Resp claveMala = login("marta", "otra");
        exigir(401, "CREDENCIALES_INVALIDAS", claveMala);
        assertFalse(claveMala.texto("mensaje").isEmpty());
        Resp inexistente = login("nadie", "demo123");
        assertEquals(401, inexistente.estado());

        assertEquals(claveMala.texto(), inexistente.texto());
    }

    @Test
    void faltanUsuarioOClaveEsUn400ConCampos() {
        Resp r = post(LOGIN).cuerpo(Map.of("usuario", "  ", "clave", "")).enviar();

        exigir(400, "VALIDACION", r);
        assertEquals("Escribe el usuario.", r.en("campos.usuario"));
        assertEquals("Escribe la contraseña.", r.en("campos.clave"));
    }

    @Test
    void laClaveSeGuardaComoHashBcrypt() {
        String guardada = consultarTexto("SELECT clave_hash FROM usuario WHERE id = ?", usuarioId);

        assertTrue(guardada.startsWith("$2"));
        assertNotEquals("demo123", guardada);
    }

    @Test
    void sinTokenResponde401NoAutenticado() {
        Resp r = get(PROTEGIDA).enviar();
        exigir(401, "NO_AUTENTICADO", r);
        assertTrue(r.cabecera("Content-Type").startsWith(PROBLEMA));
        assertFalse(r.texto("mensaje").isEmpty());
        exigir(401, "NO_AUTENTICADO", get(YO).enviar());
    }

    @Test
    void unTokenInvalidoResponde401NoAutenticado() {
        exigir(401, "NO_AUTENTICADO", conToken(PROTEGIDA, "no.es.un-token"));
        String manipulado = token() + "x";
        exigir(401, "NO_AUTENTICADO", conToken(PROTEGIDA, manipulado));
    }

    @Test
    void conTokenYPermisoLaRutaProtegidaResponde() {
        assertEquals(200, conToken(PROTEGIDA, token()).estado());
    }

    @Test
    void bastaUnoDeLosPermisosDeLaLista() {
        assertEquals(200, conToken("/api/panel/_prueba/cualquiera", token()).estado());
    }

    @Test
    void yoDevuelveUsuarioYRolSinTokenNuevo() {
        Resp r = conToken(YO, token());

        assertEquals(200, r.estado(), r::texto);
        assertFalse(r.existe("token"));
        assertEquals(usuarioId.toString(), r.en("usuario.id"));
        assertFalse(r.existe("usuario.clave"));
        assertEquals(List.of("ver_panel", "ver_citas"), r.en("rol.permisos"));
    }

    @Test
    void elTokenVenceALos60MinutosSegunElRelojDelContexto() {
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 8, 9, 0);
        fijarAhora(inicio);
        String token = token();

        fijarAhora(inicio.plusMinutes(59));
        assertEquals(200, conToken(PROTEGIDA, token).estado());

        fijarAhora(inicio.plusMinutes(61));
        exigir(401, "NO_AUTENTICADO", conToken(PROTEGIDA, token));
    }

    @Test
    void unUsuarioEliminadoTrasEmitirElTokenRecibe401() {
        String token = token();
        ejecutar("DELETE FROM usuario WHERE id = ?", usuarioId);

        exigir(401, "NO_AUTENTICADO", conToken(PROTEGIDA, token));
        assertEquals(401, conToken(YO, token).estado());
    }

    @Test
    void quitarleUnPermisoAlRolDa403EnLaSiguientePeticionSinVolverAEntrar() {
        String token = token();
        assertEquals(200, conToken(PROTEGIDA, token).estado());

        ejecutar("DELETE FROM rol_permiso WHERE rol_id = ? AND permiso = 'ver_citas'", rolId);

        Resp r = conToken(PROTEGIDA, token);
        exigir(403, "SIN_PERMISO", r);
        assertTrue(r.cabecera("Content-Type").startsWith(PROBLEMA));
        assertFalse(r.texto("mensaje").isEmpty());
    }

    @Test
    void unaExcepcionDeAutenticacionDentroDeUnControladorEs401NoUn500() {
        exigir(401, "NO_AUTENTICADO", conToken("/api/panel/_prueba/no-autenticado", token()));
    }

    /** En la versión Spring se llamaba al AccessDeniedHandler de la cadena de filtros; aquí, el 403 por HTTP. */
    @Test
    void elAccesoDenegadoResponde403SinPermisoComoProblemDetails() {
        ejecutar("DELETE FROM rol_permiso WHERE rol_id = ?", rolId);

        Resp r = conToken(PROTEGIDA, token());

        exigir(403, "SIN_PERMISO", r);
        assertTrue(r.cabecera("Content-Type").startsWith(PROBLEMA));
        assertFalse(r.texto("mensaje").isBlank());
        assertNull(r.cabecera("WWW-Authenticate"));
    }

    @Test
    void lasRutasPublicasDePruebaNoPidenToken() {
        assertEquals(409, get("/api/publico/_prueba/error-negocio/CONFLICTO").enviar().estado());
    }

    @Test
    void elLoginIgnoraUnaCabeceraAuthorizationRota() {
        Resp r = post(LOGIN).conToken("vencido.o.roto").cuerpo(Map.of("usuario", "marta", "clave", "demo123")).enviar();

        assertEquals(200, r.estado(), r::texto);
    }

    @Test
    void corsPermiteElOrigenConfiguradoYNoOtro() {
        Resp preflight = options(LOGIN).cabecera("Origin", "http://localhost:4200")
                .cabecera("Access-Control-Request-Method", "POST")
                .cabecera("Access-Control-Request-Headers", "content-type").enviar();
        assertEquals(200, preflight.estado());
        assertEquals("http://localhost:4200", preflight.cabecera("Access-Control-Allow-Origin"));
        Resp real = post(LOGIN).cabecera("Origin", "http://localhost:4200")
                .cuerpoCrudo("{\"usuario\":\"marta\",\"clave\":\"demo123\"}").enviar();
        assertEquals(200, real.estado());
        assertEquals("http://localhost:4200", real.cabecera("Access-Control-Allow-Origin"));

        Resp ajeno = options(LOGIN).cabecera("Origin", "http://malicioso.example")
                .cabecera("Access-Control-Request-Method", "POST").enviar();
        assertNull(ajeno.cabecera("Access-Control-Allow-Origin"));
        Resp ajenoReal = post(LOGIN).cabecera("Origin", "http://malicioso.example")
                .cuerpoCrudo("{\"usuario\":\"marta\",\"clave\":\"demo123\"}").enviar();
        assertNull(ajenoReal.cabecera("Access-Control-Allow-Origin"));
    }
}
