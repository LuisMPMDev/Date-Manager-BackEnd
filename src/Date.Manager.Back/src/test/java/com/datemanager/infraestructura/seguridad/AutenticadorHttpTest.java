package com.datemanager.infraestructura.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.datemanager.ClienteHttpPrueba;
import com.datemanager.ClienteHttpPrueba.Resultado;
import com.datemanager.PruebaAdministracion;
import com.datemanager.RelojMutable;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;
import com.datemanager.dominio.Usuario;
import com.datemanager.infraestructura.http.Enrutador;
import com.datemanager.infraestructura.http.Respuesta;
import com.datemanager.infraestructura.http.Ruta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El {@link Autenticador} real enganchado al servidor HTTP real, con la base real. */
class AutenticadorHttpTest extends PruebaAdministracion {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-bytes!!";
    private static final String NO_AUTENTICADO = "Inicia sesión para continuar.";
    private static final String SIN_PERMISO = "No tienes permiso para realizar esta acción.";

    private final AtomicInteger manejadores = new AtomicInteger();
    private RelojMutable reloj;
    private ServicioJwt jwt;
    private ClienteHttpPrueba http;

    @BeforeEach
    void arrancar() {
        reloj = new RelojMutable(ZoneId.of("America/Bogota"));
        reloj.fijarAhora(LocalDateTime.of(2026, 11, 10, 9, 0, 0));
        jwt = new ServicioJwt(SECRETO, reloj);

        Enrutador rutas = new Enrutador();
        rutas.agregar(new Ruta("POST", "/api/auth/login", Set.of(), true, s -> {
            manejadores.incrementAndGet();
            return Respuesta.ok(Map.of("ok", true));
        }, null));
        rutas.agregar(new Ruta("GET", "/api/citas", Set.of("ver_citas", "gestionar_citas"), false, s -> {
            manejadores.incrementAndGet();
            return Respuesta.ok(Map.of("usuario", s.usuario().id().toString(), "rol", s.usuario().rolId().toString()));
        }, null));
        rutas.agregar(new Ruta("GET", "/api/yo", Set.of(), false, s -> {
            manejadores.incrementAndGet();
            return Respuesta.ok(Map.of("usuario", s.usuario().id().toString()));
        }, null));
        http = ClienteHttpPrueba.iniciar(rutas, new Autenticador(jwt, usuarios, roles, tx));
    }

    @AfterEach
    void parar() {
        http.close();
    }

    private static void esProblema(Resultado r, int estado, String codigo, String mensaje) {
        assertEquals(estado, r.estado(), r.cuerpo());
        Map<String, Object> cuerpo = r.json();
        assertEquals(codigo, cuerpo.get("codigo"));
        assertEquals(mensaje, cuerpo.get("mensaje"));
    }

    private String bearer(UUID usuario) {
        return "Bearer " + jwt.emitir(usuario);
    }

    // ------------------------------------------------------------------ ruta pública y login

    @Test
    void elLoginNoLeeLaCabeceraAuthorization() {
        for (String cabecera : new String[] {"Bearer basura", "Basic YTpi", "Bearer", "", "xxx yyy zzz",
                "Bearer " + "a".repeat(5000)}) {
            Resultado r = http.enviar("POST", "/api/auth/login", "{}", "Authorization", cabecera);
            assertEquals(200, r.estado(), "Authorization=[" + cabecera + "] -> " + r.cuerpo());
        }
        assertEquals(6, manejadores.get());
    }

    @Test
    void elLoginTampocoMiraUnTokenVencido() {
        UUID id = crearUsuario("vencido", crearRol("Base"));
        String cabecera = bearer(id);
        reloj.fijarAhora(LocalDateTime.of(2026, 11, 10, 11, 0, 0));

        assertEquals(200, http.enviar("POST", "/api/auth/login", "{}", "Authorization", cabecera).estado());
    }

    // ------------------------------------------------------------------ 401

    @Test
    void sinTokenOConUnTokenRaroEs401() {
        Resultado sin = http.get("/api/citas");
        esProblema(sin, 401, "NO_AUTENTICADO", NO_AUTENTICADO);
        assertEquals("Bearer", sin.cabecera("WWW-Authenticate"));

        for (String cabecera : new String[] {"Bearer basura", "Basic x", "Bearer", "Bearer  x", "bearer a.b.c"}) {
            esProblema(http.get("/api/citas", "Authorization", cabecera), 401, "NO_AUTENTICADO", NO_AUTENTICADO);
            esProblema(http.get("/api/yo", "Authorization", cabecera), 401, "NO_AUTENTICADO", NO_AUTENTICADO);
        }
        assertEquals(0, manejadores.get());
    }

    @Test
    void unTokenVencidoEs401() {
        UUID rol = crearRol("Con", Permiso.VER_CITAS);
        String cabecera = bearer(crearUsuario("ana", rol));
        assertEquals(200, http.get("/api/citas", "Authorization", cabecera).estado());

        reloj.fijarAhora(LocalDateTime.of(2026, 11, 10, 10, 0, 1));

        esProblema(http.get("/api/citas", "Authorization", cabecera), 401, "NO_AUTENTICADO", NO_AUTENTICADO);
    }

    @Test
    void unTokenDeUnUsuarioBorradoEs401() {
        UUID id = crearUsuario("borrado", crearRol("Con", Permiso.VER_CITAS));
        String cabecera = bearer(id);
        assertEquals(200, http.get("/api/citas", "Authorization", cabecera).estado());

        tx.escribir(() -> usuarios.eliminar(id));

        esProblema(http.get("/api/citas", "Authorization", cabecera), 401, "NO_AUTENTICADO", NO_AUTENTICADO);
    }

    // ------------------------------------------------------------------ 403 y 200

    @Test
    void conTokenValidoYSinElPermisoEs403() {
        UUID rol = crearRol("Sin", Permiso.GESTIONAR_CLIENTES);
        Resultado r = http.get("/api/citas", "Authorization", bearer(crearUsuario("ana", rol)));

        esProblema(r, 403, "SIN_PERMISO", SIN_PERMISO);
        assertNull(r.cabecera("WWW-Authenticate"));
        assertEquals(0, manejadores.get());
    }

    @Test
    void conTokenValidoYUnoDeLosPermisosEs200YElManejadorVeAlUsuario() {
        UUID rol = crearRol("Con", Permiso.GESTIONAR_CITAS);
        UUID id = crearUsuario("ana", rol);

        Resultado r = http.get("/api/citas", "Authorization", bearer(id));

        assertEquals(200, r.estado(), r.cuerpo());
        assertEquals(id.toString(), r.json().get("usuario"));
        assertEquals(rol.toString(), r.json().get("rol"));
    }

    @Test
    void unaRutaSoloDeSesionNoExigePermisos() {
        UUID id = crearUsuario("ana", crearRol("Vacío"));

        Resultado r = http.get("/api/yo", "Authorization", "bearer " + jwt.emitir(id));

        assertEquals(200, r.estado(), r.cuerpo());
        assertEquals(id.toString(), r.json().get("usuario"));
    }

    @Test
    void loQueCambiaEnLaBaseSurteEfectoEnLaSiguientePeticion() {
        UUID rol = crearRol("Cambiante", Permiso.VER_CITAS);
        UUID id = crearUsuario("ana", rol);
        String cabecera = bearer(id);
        assertEquals(200, http.get("/api/citas", "Authorization", cabecera).estado());

        tx.escribir(() -> roles.guardar(new Rol(rol, "Cambiante", "", Set.of(Permiso.GESTIONAR_CLIENTES))));
        esProblema(http.get("/api/citas", "Authorization", cabecera), 403, "SIN_PERMISO", SIN_PERMISO);

        UUID otroRol = crearRol("Otro", Permiso.GESTIONAR_CITAS);
        tx.escribir(() -> usuarios.guardar(
                new Usuario(id, "Nombre", "Apellido", "ana@correo.com", "", "ana", otroRol), null));
        assertEquals(200, http.get("/api/citas", "Authorization", cabecera).estado());
    }

    @Test
    void unaRutaConUnPermisoDesconocidoNoSePuedeRegistrar() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ruta("GET", "/api/x", Set.of("no_existe"), false, s -> Respuesta.sinContenido(), null));
    }
}
