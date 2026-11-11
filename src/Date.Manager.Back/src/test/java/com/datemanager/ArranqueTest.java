package com.datemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.datemanager.infraestructura.AdministradorInicial;
import com.datemanager.infraestructura.Aplicacion;
import com.datemanager.infraestructura.Configuracion;
import com.datemanager.infraestructura.http.ManejadorErrores;
import org.junit.jupiter.api.Test;

class ArranqueTest extends PruebaIntegracion {

    private static final String BASE = "/api/publico/_prueba";

    @Test
    void elContextoArrancaYFlywayAplicaLaLineaBase() {
        assertTrue(migrador.versionesAplicadas().contains(1));
        assertEquals(migrador.ultimaVersionDisponible(), migrador.versionActual().orElseThrow());
    }

    @Test
    void errorDeNegocioConflictoResponde409ConCodigoYMensaje() {
        Resp r = get(BASE + "/error-negocio/CONFLICTO").enviar();

        assertEquals(409, r.estado());
        assertTrue(r.cabecera("Content-Type").startsWith("application/problem+json"), r.cabecera("Content-Type"));
        assertEquals("CONFLICTO", r.en("codigo"));
        assertEquals("x", r.en("mensaje"));
    }

    @Test
    void horarioNoDisponibleResponde409() {
        Resp r = get(BASE + "/error-negocio/HORARIO_NO_DISPONIBLE").enviar();

        assertEquals(409, r.estado());
        assertEquals("HORARIO_NO_DISPONIBLE", r.en("codigo"));
    }

    @Test
    void enlaceVencidoResponde410() {
        Resp r = get(BASE + "/error-negocio/ENLACE_VENCIDO").enviar();

        assertEquals(410, r.estado());
        assertEquals("ENLACE_VENCIDO", r.en("codigo"));
    }

    @Test
    void laValidacionDeBeanValidationResponde400ConLosCampos() {
        Resp r = post(BASE + "/validacion").cuerpoCrudo("{\"nombre\":\" \"}").enviar();

        assertEquals(400, r.estado());
        assertEquals("VALIDACION", r.en("codigo"));
        assertFalse(r.texto("mensaje").isEmpty());
        assertEquals("es obligatorio", r.en("campos.nombre"));
    }

    @Test
    void unCuerpoIlegibleResponde400ConCodigoDeValidacion() {
        Resp r = post(BASE + "/validacion").cuerpoCrudo("{no es json").enviar();

        assertEquals(400, r.estado());
        assertEquals("VALIDACION", r.en("codigo"));
    }

    @Test
    void unaRutaInexistenteResponde404ConCodigoYNoSeConvierteEn500() {
        Resp r = get(BASE + "/no-existe").enviar();

        assertEquals(404, r.estado());
        assertEquals("NO_ENCONTRADO", r.en("codigo"));
    }

    @Test
    void unaViolacionDeRestriccionDeLaBaseResponde409SinFiltrarDetalles() {
        Resp r = get(BASE + "/violacion-unica").enviar();

        assertEquals(409, r.estado());
        assertEquals("CONFLICTO", r.en("codigo"));
        assertFalse(r.texto().contains("detalle interno"));
    }

    @Test
    void unFalloDeBloqueoDeLaBaseResponde409SinFiltrarDetalles() {
        // Se registra un WARNING; aquí se silencia para no ensuciar la salida de pruebas.
        try (CapturaRegistro registro = new CapturaRegistro(ManejadorErrores.class, true)) {
            Resp r = get(BASE + "/bloqueo").enviar();

            assertEquals(409, r.estado());
            assertEquals("CONFLICTO", r.en("codigo"));
            assertEquals("Hay otra operación en curso; inténtalo de nuevo.", r.en("mensaje"));
            assertFalse(r.texto().contains("detalle interno"));
        }
    }

    @Test
    void unaExcepcionInesperadaResponde500SinFiltrarDetalles() {
        // La excepción se registra (SEVERE); aquí se silencia para no ensuciar la salida de pruebas.
        try (CapturaRegistro registro = new CapturaRegistro(ManejadorErrores.class, true)) {
            Resp r = get(BASE + "/inesperado").enviar();

            assertEquals(500, r.estado());
            assertEquals("ERROR_INTERNO", r.en("codigo"));
            assertFalse(r.texto().contains("detalle interno"));
        }
    }

    @Test
    void elRelojUsaLaZonaDelNegocioYSePuedeFijar() {
        assertEquals(ZoneId.of("America/Bogota"), reloj.getZone());

        fijarAhora(LocalDateTime.of(2026, 11, 8, 23, 30));

        // 23:30 en Bogotá es ya el día siguiente en UTC; "hoy" debe seguir siendo el de Bogotá.
        assertEquals(LocalDate.of(2026, 11, 8), LocalDate.now(reloj));
        assertEquals(LocalDateTime.of(2026, 11, 8, 23, 30), LocalDateTime.now(reloj));
    }

    // ---------------------------------------------------------------- Ciclo de vida de Aplicacion

    private static Configuracion configuracion(boolean dev, String adminUsuario, String adminClave) {
        return new Configuracion("jdbc:sqlserver://no-se-usa", "no-se-usa", "no-se-usa", 10, SECRETO, ZONA,
                List.of(ORIGEN_CORS), adminUsuario, adminClave, dev, 0);
    }

    private static int estadoDe(int puerto, String ruta) throws IOException, InterruptedException {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                .timeout(Duration.ofSeconds(10)).build();
        return cliente.send(peticion, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void iniciarMigraCreaElAdministradorYAbreElPuertoYDetenerLoCierraSinCerrarElPoolAjeno() throws Exception {
        Aplicacion app = new Aplicacion(configuracion(false, "jefa", "clave-de-prueba-123"), pool, reloj,
                new CodificadorFalso());
        try (CapturaRegistro registro = new CapturaRegistro(AdministradorInicial.class, true)) {
            app.iniciar();
            assertTrue(registro.texto().contains("Administrador inicial creado con el usuario «jefa»."),
                    registro.texto());
        }
        int puerto = app.puerto();
        try {
            assertEquals(401, estadoDe(puerto, "/api/panel/citas"));
            assertEquals(1, consultarInt("SELECT COUNT(*) FROM usuario WHERE usuario = 'jefa'"));
            assertEquals(migrador.ultimaVersionDisponible(), migrador.versionActual().orElseThrow());
        } finally {
            app.detener();
        }
        app.detener(); // repetir no tiene efecto

        assertThrows(ConnectException.class, () -> estadoDe(puerto, "/api/panel/citas"));
        // El pool era de quien llamó: sigue sirviendo.
        assertEquals(1, consultarInt("SELECT COUNT(*) FROM usuario"));
        assertThrows(IllegalStateException.class, app::iniciar);
    }

    @Test
    void sinPerfilDevNoSiembraYConPerfilDevSiembraLaBaseVacia() {
        Aplicacion sinDev = new Aplicacion(configuracion(false, "", ""), pool, reloj, new CodificadorFalso());
        try (CapturaRegistro registro = new CapturaRegistro(AdministradorInicial.class, true)) {
            sinDev.iniciar();
        } finally {
            sinDev.detener();
        }
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM usuario"));
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM cita"));

        // Con dev la semilla va primero: crea marta y andres, y el administrador de DATEMANAGER_ADMIN_* sobra.
        Aplicacion conDev = new Aplicacion(configuracion(true, "admin", "clave-de-prueba-123"), pool, reloj,
                new CodificadorFalso());
        try (CapturaRegistro registro = new CapturaRegistro(com.datemanager.infraestructura.SemillaDatos.class,
                true)) {
            conDev.iniciar();
            assertTrue(registro.texto().contains("Perfil dev: datos de ejemplo creados (2 roles, 2 usuarios, "
                    + "3 servicios, 8 clientes, 23 citas)."), registro.texto());
        } finally {
            conDev.detener();
        }
        assertEquals(List.of("andres", "marta"), columna("SELECT usuario FROM usuario ORDER BY usuario"));
        assertEquals(23, consultarInt("SELECT COUNT(*) FROM cita"));
    }

    @Test
    void unaClaveDeAdministradorDeMasDe72BytesHaceFallarElArranqueSinAbrirElPuertoNiCrearNada() {
        String larga = "x".repeat(73);
        Aplicacion app = new Aplicacion(configuracion(false, "admin", larga), pool, reloj);
        try {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, app::iniciar);
            assertNotNull(e.getMessage());
            assertFalse(e.getMessage().contains(larga));
            assertThrows(IllegalStateException.class, app::puerto);
        } finally {
            app.detener();
        }
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM usuario"));
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM rol"));
    }
}
