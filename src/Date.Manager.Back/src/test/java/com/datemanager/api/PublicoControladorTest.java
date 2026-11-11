package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.datemanager.infraestructura.http.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Zona del cliente: solo con el código del enlace, sin token. */
class PublicoControladorTest extends PruebaPanel {

    private static final String BASE = "/api/publico/invitaciones/";
    /** Lunes 9 de noviembre de 2026, 08:00 en Bogotá = 13:00 UTC. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 8, 0);
    private static final String MANANA = "2026-11-10";
    private static final String TOKEN_BASURA = "Bearer basura.vencida.xxx";

    private UUID admin;
    /** 30 min, todos los días de 09:00 a 12:00. */
    private UUID corte;
    private UUID inactivo;

    @BeforeEach
    void preparar() {
        fijarAhora(AHORA);
        admin = crearUsuario("admin", crearRol("Administrador", "gestionar_enlaces", "gestionar_citas", "ver_citas"));
        corte = crearServicio("Corte", 30, true);
        crearServicio("Barba", 30, true);
        inactivo = crearServicio("Antiguo", 30, false);
    }

    // ---------- Utilidades ----------

    private UUID crearServicio(String nombre, int duracion, boolean activo) {
        UUID id = UUID.randomUUID();
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, ?, ?, ?, 10)",
                id, nombre, duracion, activo);
        for (int dia = 1; dia <= 7; dia++) {
            ejecutar("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                    + "VALUES (?, ?, ?, '09:00', '12:00')", id, UUID.randomUUID(), dia);
        }
        return id;
    }

    /** Enlace creado por el panel; devuelve la respuesta con {id, codigo}. */
    private Resp crearEnlace(Integer duracion, Integer maxUsos) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        if (duracion != null) {
            cuerpo.put("duracionMinutos", duracion);
        }
        if (maxUsos != null) {
            cuerpo.put("maxUsos", maxUsos);
        }
        return exigir(201, post("/api/panel/enlaces").conToken(tokenDe(admin)).cuerpo(cuerpo).enviar());
    }

    private String codigo(Integer duracion, Integer maxUsos) {
        return crearEnlace(duracion, maxUsos).texto("codigo");
    }

    private void desactivarPorPanel(Resp enlace) {
        exigir(200, post("/api/panel/enlaces/" + enlace.texto("id") + "/desactivar").conToken(tokenDe(admin))
                .enviar());
    }

    private static Map<String, Object> reserva(UUID servicio, String fecha, String inicio, String correo) {
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("nombre", "Ana");
        cliente.put("apellido", "Ruiz");
        cliente.put("telefono", "300 111");
        cliente.put("correo", correo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("servicioId", servicio.toString());
        r.put("fecha", fecha);
        r.put("inicio", inicio);
        r.put("cliente", cliente);
        return r;
    }

    private static Resp estado(String codigo) {
        return get(BASE + codigo).enviar();
    }

    private static Resp servicios(String codigo) {
        return get(BASE + codigo + "/servicios").enviar();
    }

    private static Resp horarios(String codigo, Object servicio, String fecha) {
        return get(BASE + codigo + "/servicios/" + servicio + "/horarios?fecha=" + fecha).enviar();
    }

    private static Resp reservar(String codigo, Map<String, Object> cuerpo) {
        return post(BASE + codigo + "/citas").cuerpo(cuerpo).enviar();
    }

    private Resp reservar(String codigo, String inicio) {
        return reservar(codigo, reserva(corte, MANANA, inicio, "ana@x.co"));
    }

    private static int usos(String codigo) {
        return consultarInt("SELECT usos FROM enlace_invitacion WHERE codigo = ?", codigo);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> claves(Resp r) {
        return ((Map<String, Object>) r.json()).keySet();
    }

    /**
     * Cuerpo de error sin {@code instance} (la ruta que pidió el propio cliente): todo lo demás debe ser idéntico
     * para no dar pistas.
     */
    @SuppressWarnings("unchecked")
    private static String errorSinRuta(Resp r) {
        Map<String, Object> nodo = new TreeMap<>((Map<String, Object>) r.json());
        nodo.remove("instance");
        return Json.escribir(nodo);
    }

    // ---------- Código desconocido ----------

    @Test
    void unCodigoDesconocidoEs404SiempreConElMismoCuerpo() {
        String existente = codigo(null, null);
        String otraMayuscula = existente.chars().anyMatch(Character::isLowerCase)
                ? existente.toUpperCase() : existente.toLowerCase();
        List<String> desconocidos = List.of(
                "AAAAAAAAAAAAAAAAAAAAAA",        // bien formado, no existe
                "no-existe",                      // mal formado
                otraMayuscula,                    // el real con otras mayúsculas
                existente + "%20",                // el real con un espacio final
                existente.substring(1));          // el real sin su primer carácter

        Resp ref = exigir(404, "ENLACE_NO_ENCONTRADO", estado("AAAAAAAAAAAAAAAAAAAAAA"));
        String referencia = errorSinRuta(ref);
        Set<String> sinInstance = new java.util.HashSet<>(claves(ref));
        sinInstance.remove("instance");
        assertEquals(Set.of("type", "title", "status", "detail", "codigo", "mensaje"), sinInstance);

        List<String> cuerpos = new ArrayList<>();
        for (String codigo : desconocidos) {
            cuerpos.add(errorSinRuta(exigir(404, estado(codigo))));
            cuerpos.add(errorSinRuta(exigir(404, servicios(codigo))));
            cuerpos.add(errorSinRuta(exigir(404, horarios(codigo, corte, MANANA))));
            cuerpos.add(errorSinRuta(exigir(404, reservar(codigo, "09:00"))));
        }
        cuerpos.forEach(c -> assertEquals(referencia, c));
        assertEquals(0, contar("cita"));
        assertEquals(0, usos(existente));
    }

    @Test
    void unCodigoConBarraCodificadaOMuyLargoEs404NoUn500() {
        String existente = codigo(null, null);
        String largo = "A".repeat(2000);

        for (String codigo : List.of("abc%2Fdef", existente.substring(0, 11) + "%2F" + existente.substring(11),
                "%2F" + existente, largo)) {
            exigir(404, "ENLACE_NO_ENCONTRADO", estado(codigo));
            exigir(404, "ENLACE_NO_ENCONTRADO", servicios(codigo));
            exigir(404, "ENLACE_NO_ENCONTRADO", horarios(codigo, corte, MANANA));
            exigir(404, "ENLACE_NO_ENCONTRADO", reservar(codigo, "09:00"));
        }
        assertEquals(0, contar("cita"));
    }

    // ---------- Estado del enlace ----------

    @Test
    void elEstadoDeUnEnlaceActivoSoloDiceEstadoYVencimiento() {
        String codigo = codigo(null, 3);

        Resp r = exigir(200, estado(codigo));
        assertEquals("Activo", r.en("estado"));
        assertEquals("2026-11-10T13:00:00Z", r.en("venceEn"));
        assertEquals(Set.of("estado", "venceEn"), claves(r));
    }

    @Test
    void unEnlaceVencidoAgotadoODesactivadoRespondeSuEstadoCon200() {
        String vencido = codigo(60, null);
        String agotado = codigo(null, 1);
        Resp desactivado = crearEnlace(null, null);
        ejecutar("UPDATE enlace_invitacion SET usos = 1 WHERE codigo = ?", agotado);
        desactivarPorPanel(desactivado);
        fijarAhora(AHORA.plusMinutes(60)); // vence_en == ahora

        assertEquals("Vencido", exigir(200, estado(vencido)).en("estado"));
        assertEquals("Agotado", exigir(200, estado(agotado)).en("estado"));
        assertEquals("Desactivado", exigir(200, estado(desactivado.texto("codigo"))).en("estado"));
    }

    @Test
    void unMinutoAntesDeVencerSigueActivo() {
        String codigo = codigo(60, null);
        fijarAhora(AHORA.plusMinutes(59));

        assertEquals("Activo", estado(codigo).en("estado"));
        exigir(200, servicios(codigo));
    }

    @Test
    void conUnEnlaceNoActivoNoSeConsultaNiSeReserva() {
        String vencido = codigo(60, null);
        String agotado = codigo(null, 1);
        Resp desactivado = crearEnlace(null, null);
        ejecutar("UPDATE enlace_invitacion SET usos = 1 WHERE codigo = ?", agotado);
        desactivarPorPanel(desactivado);
        fijarAhora(AHORA.plusMinutes(60));

        Map<String, String> casos = Map.of(
                vencido, "ENLACE_VENCIDO",
                agotado, "ENLACE_AGOTADO",
                desactivado.texto("codigo"), "ENLACE_DESACTIVADO");
        for (Map.Entry<String, String> caso : casos.entrySet()) {
            String codigo = caso.getKey();
            exigir(410, caso.getValue(), servicios(codigo));
            exigir(410, caso.getValue(), horarios(codigo, corte, MANANA));
            exigir(410, caso.getValue(), reservar(codigo, "10:00"));
        }
        assertEquals(0, contar("cita"));
        assertEquals(0, contar("cliente"));
        assertEquals(1, usos(agotado));
    }

    @Test
    void desactivadoGanaAVencidoYAgotado() {
        Resp enlace = crearEnlace(60, 1);
        String codigo = enlace.texto("codigo");
        ejecutar("UPDATE enlace_invitacion SET usos = 1 WHERE codigo = ?", codigo);
        desactivarPorPanel(enlace);
        fijarAhora(AHORA.plusDays(2));

        assertEquals("Desactivado", estado(codigo).en("estado"));
        assertEquals("ENLACE_DESACTIVADO", reservar(codigo, "10:00").en("codigo"));
    }

    // ---------- Servicios y horarios ----------

    @Test
    void soloSeListanLosServiciosActivosPorNombre() {
        Resp r = exigir(200, servicios(codigo(null, null)));
        assertEquals(List.of("Barba", "Corte"), r.en("$[*].nombre"));
        assertEquals(corte.toString(), r.en("$[1].id"));
        assertEquals(30, r.numero("$[1].duracionMin").intValue());
        assertEquals("09:00", r.en("$[1].ventanas[0].inicio"));
    }

    @Test
    void horariosDevuelveSoloHorasLibres() {
        String codigo = codigo(null, null);
        exigir(201, reservar(codigo, "09:30"));

        Resp r = exigir(200, horarios(codigo, corte, MANANA));
        assertEquals("09:00", r.en("$[0]"));
        assertFalse(r.lista("$").contains("09:30"));
        assertTrue(r.lista("$").contains("11:30"));
    }

    @Test
    void horariosDeUnServicioInactivoODesconocidoSon404IgualesSinPistas() {
        String codigo = codigo(null, null);

        String deInactivo = errorSinRuta(exigir(404, "NO_ENCONTRADO", horarios(codigo, inactivo, MANANA)));
        String deDesconocido = errorSinRuta(exigir(404, horarios(codigo, UUID.randomUUID(), MANANA)));

        assertEquals(deDesconocido, deInactivo);
    }

    @Test
    void horariosConServicioOFechaMalFormadosSon400() {
        String codigo = codigo(null, null);

        exigir(400, "VALIDACION", horarios(codigo, "no-es-uuid", MANANA));
        exigir(400, "VALIDACION", horarios(codigo, corte, "2026-13-01"));
        exigir(400, "VALIDACION", horarios(codigo, corte, "10/11/2026"));
        exigir(400, "VALIDACION", get(BASE + codigo + "/servicios/" + corte + "/horarios").enviar());
    }

    // ---------- Reservar ----------

    @Test
    @SuppressWarnings("unchecked")
    void unEnlaceActivoPermiteElFlujoCompletoYLaCitaQuedaLigadaAlEnlace() {
        Resp enlace = crearEnlace(null, 5);
        String codigo = enlace.texto("codigo");

        assertEquals("Activo", estado(codigo).en("estado"));
        exigir(200, servicios(codigo));
        assertTrue(horarios(codigo, corte, MANANA).lista("$").contains("10:00"));
        Resp cita = exigir(201, reservar(codigo, "10:00"));
        assertEquals(corte.toString(), cita.en("servicioId"));
        assertEquals("Corte", cita.en("servicio"));
        assertEquals(MANANA, cita.en("fecha"));
        assertEquals("10:00", cita.en("inicio"));
        assertEquals("10:30", cita.en("fin"));
        assertEquals("Confirmada", cita.en("estado"));

        assertEquals(Set.of("servicioId", "servicio", "fecha", "inicio", "fin", "estado"), claves(cita));
        Map<String, Object> fila = filas("SELECT * FROM cita").get(0);
        assertTrue(fila.get("enlace_id").toString().equalsIgnoreCase(enlace.texto("id")));
        assertEquals("Confirmada", fila.get("estado"));
        assertEquals(1, usos(codigo));
        assertFalse(horarios(codigo, corte, MANANA).lista("$").contains("10:00"));
        // El panel ve la cita y el uso.
        assertEquals("10:00", obtener("/api/panel/citas", admin).en("$[0].inicio"));
        assertEquals(1, obtener("/api/panel/enlaces", admin).numero("$[0].usos").intValue());
    }

    @Test
    void laRespuestaNoIncluyeDatosDeOtrosClientes() {
        ejecutar("INSERT INTO cliente (id, nombre, apellido, telefono, correo) "
                + "VALUES (?, 'Beatriz', 'Secreta', '311 999 9999', 'bea@x.co')", UUID.randomUUID());
        String codigo = codigo(null, null);

        Resp r = exigir(201, reservar(codigo, reserva(corte, MANANA, "09:00", "BEA@x.co")));

        for (String dato : List.of("Beatriz", "Secreta", "311 999 9999", "bea@x.co", "clienteId", "telefono",
                "correo")) {
            assertFalse(r.texto().contains(dato), dato);
        }
        assertEquals(Set.of("servicioId", "servicio", "fecha", "inicio", "fin", "estado"), claves(r));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unaReservaFallidaNoGastaUnUso() {
        String codigo = codigo(null, 2);
        exigir(201, reservar(codigo, "09:00"));
        assertEquals(1, usos(codigo));

        // Horario ocupado.
        exigir(409, "HORARIO_NO_DISPONIBLE", reservar(codigo, "09:00"));
        // Horario mal alineado o en el pasado.
        exigir(409, reservar(codigo, "09:15"));
        exigir(409, reservar(codigo, reserva(corte, "2026-11-08", "10:00", "ana@x.co")));
        // Datos del cliente inválidos (validación de la aplicación y del cuerpo).
        assertTrue(exigir(400, reservar(codigo, reserva(corte, MANANA, "10:00", "no-es-correo")))
                .existe("campos.correo"));
        Map<String, Object> sinNombre = reserva(corte, MANANA, "10:00", "ana@x.co");
        ((Map<String, Object>) sinNombre.get("cliente")).put("nombre", " ");
        assertTrue(exigir(400, reservar(codigo, sinNombre)).existe("campos['cliente.nombre']"));
        // Hora mal escrita.
        exigir(400, reservar(codigo, "9:00"));
        // Servicio inactivo o desconocido.
        exigir(404, reservar(codigo, reserva(inactivo, MANANA, "10:00", "ana@x.co")));
        exigir(404, reservar(codigo, reserva(UUID.randomUUID(), MANANA, "10:00", "ana@x.co")));

        assertEquals(1, usos(codigo));
        assertEquals(1, contar("cita"));

        exigir(201, reservar(codigo, "09:30"));
        assertEquals(2, usos(codigo));
        exigir(410, "ENLACE_AGOTADO", reservar(codigo, "10:00"));
        assertEquals("Agotado", estado(codigo).en("estado"));
        assertEquals(2, usos(codigo));
        assertEquals(2, contar("cita"));
    }

    @Test
    void cancelarLaCitaNoDevuelveElUso() {
        String codigo = codigo(null, 1);
        exigir(201, reservar(codigo, "09:00"));
        String cita = consultarTexto("SELECT CONVERT(NVARCHAR(36), id) FROM cita");

        exigir(200, post("/api/panel/citas/" + cita + "/cancelar").conToken(tokenDe(admin)).enviar());

        assertEquals(1, usos(codigo));
        assertEquals("Agotado", estado(codigo).en("estado"));
        exigir(410, "ENLACE_AGOTADO", reservar(codigo, "09:00"));
    }

    @Test
    void siElEnlaceSeDesactivaAMitadDelFlujoLaReservaFalla() {
        Resp enlace = crearEnlace(null, null);
        String codigo = enlace.texto("codigo");
        exigir(200, horarios(codigo, corte, MANANA));

        desactivarPorPanel(enlace);

        exigir(410, "ENLACE_DESACTIVADO", reservar(codigo, "09:00"));
        assertEquals(0, contar("cita"));
    }

    @Test
    void siElEnlaceVenceAMitadDelFlujoLaReservaFalla() {
        String codigo = codigo(60, null);
        exigir(200, horarios(codigo, corte, MANANA));

        fijarAhora(AHORA.plusMinutes(60));

        exigir(410, "ENLACE_VENCIDO", reservar(codigo, "09:00"));
        assertEquals(0, contar("cita"));
    }

    @Test
    void dosReservasSimultaneasConUnEnlaceDeUnUsoDanUn201YUn410() throws Exception {
        String codigo = codigo(null, 1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);
        List<Resp> respuestas = new ArrayList<>();
        try {
            List<Future<Resp>> futuros = new ArrayList<>();
            for (String inicio : List.of("09:00", "10:00")) {
                Callable<Resp> tarea = () -> {
                    listos.countDown();
                    salida.await();
                    return reservar(codigo, inicio);
                };
                futuros.add(hilos.submit(tarea));
            }
            assertTrue(listos.await(10, TimeUnit.SECONDS));
            salida.countDown();
            for (Future<Resp> f : futuros) {
                respuestas.add(f.get(60, TimeUnit.SECONDS));
            }
        } finally {
            hilos.shutdownNow();
        }

        List<Integer> estados = new ArrayList<>(respuestas.stream().map(Resp::estado).toList());
        estados.sort(null);
        assertEquals(List.of(201, 410), estados, () -> respuestas.toString());
        Resp perdedora = respuestas.stream().filter(r -> r.estado() == 410).findFirst().orElseThrow();
        assertEquals("ENLACE_AGOTADO", perdedora.en("codigo"));
        assertEquals(1, usos(codigo));
        assertEquals(1, contar("cita"));
    }

    // ---------- Sin token, o con un token viejo ----------

    @Test
    void unTokenInvalidoEnLaZonaDelClienteSeIgnora() {
        String codigo = codigo(null, null);

        exigir(200, get(BASE + codigo).cabecera("Authorization", TOKEN_BASURA).enviar());
        exigir(200, get(BASE + codigo + "/servicios").cabecera("Authorization", TOKEN_BASURA).enviar());
        exigir(200, get(BASE + codigo + "/servicios/" + corte + "/horarios?fecha=" + MANANA)
                .cabecera("Authorization", TOKEN_BASURA).enviar());
        exigir(201, post(BASE + codigo + "/citas").cabecera("Authorization", TOKEN_BASURA)
                .cuerpo(reserva(corte, MANANA, "09:00", "ana@x.co")).enviar());
        exigir(404, "ENLACE_NO_ENCONTRADO", get(BASE + "AAAAAAAAAAAAAAAAAAAAAA")
                .cabecera("Authorization", TOKEN_BASURA).enviar());
    }

    @Test
    void unaReservaConUnTokenVencidoDeVerdadSeAtiende() {
        String codigo = codigo(null, null);
        String vencido = tokenDe(admin);
        fijarAhora(AHORA.plusMinutes(61)); // el token dura 60 min
        exigir(401, "NO_AUTENTICADO", get("/api/panel/enlaces").conToken(vencido).enviar());

        exigir(200, get(BASE + codigo).conToken(vencido).enviar());
        Resp r = exigir(201, post(BASE + codigo + "/citas").conToken(vencido)
                .cuerpo(reserva(corte, MANANA, "09:00", "ana@x.co")).enviar());
        assertEquals("Confirmada", r.en("estado"));
        assertEquals(1, usos(codigo));
    }

    @Test
    void unTokenInvalidoNoImpideIniciarSesion() {
        String usuario = "login-" + UUID.randomUUID().toString().substring(0, 8);
        crearUsuario(usuario, crearRol("R", "ver_panel"), "clave-1");

        Resp ok = exigir(200, post("/api/auth/login").cabecera("Authorization", TOKEN_BASURA)
                .cuerpo(Map.of("usuario", usuario, "clave", "clave-1")).enviar());
        assertFalse(ok.texto("token").isEmpty());
        // Con credenciales malas sigue siendo CREDENCIALES_INVALIDAS, no el 401 del token.
        exigir(401, "CREDENCIALES_INVALIDAS", post("/api/auth/login").cabecera("Authorization", TOKEN_BASURA)
                .cuerpo(Map.of("usuario", usuario, "clave", "mala")).enviar());
    }

    @Test
    void lasRutasDelPanelYYoSiguenExigiendoUnTokenValido() {
        exigir(401, "NO_AUTENTICADO", get("/api/panel/enlaces").cabecera("Authorization", TOKEN_BASURA).enviar());
        exigir(401, get("/api/auth/yo").cabecera("Authorization", TOKEN_BASURA).enviar());
        exigir(200, get("/api/auth/yo").conToken(tokenDe(admin)).enviar());
    }

    @Test
    void laZonaDelClienteFuncionaSinToken() {
        String codigo = codigo(null, null);

        exigir(200, estado(codigo));
        exigir(200, servicios(codigo));
        exigir(200, horarios(codigo, corte, MANANA));
        exigir(201, reservar(codigo, "09:00"));
        assertEquals(1, usos(codigo));
    }
}
