package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.datemanager.dominio.Permiso;
import org.junit.jupiter.api.Test;

/**
 * El recorrido completo de punta a punta con peticiones HTTP reales (solo el reloj es falso):
 * una administradora entra, crea un enlace de 24 h con 1 uso, el cliente (sin token) ve servicios y horarios y
 * reserva, la cita aparece en el panel, el enlace ya no admite otra reserva y, al cancelar la cita, el horario
 * vuelve a quedar libre.
 */
class RecorridoCompletoTest extends PruebaPanel {

    /** Lunes 9 de noviembre de 2026, 08:00 en Bogotá. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 8, 0);
    private static final String MANANA = "2026-11-10";
    private static final String CLAVE_ADMIN = "clave-de-prueba-del-recorrido";

    @Test
    void recorridoCompletoDeLaAdministradoraAlClienteYDeVuelta() {
        fijarAhora(AHORA);
        String[] todos = Arrays.stream(Permiso.values()).map(Permiso::id).toArray(String[]::new);
        crearUsuario("admin", crearRol("Administrador", todos), CLAVE_ADMIN);

        // 1. Login real con usuario y clave.
        Resp login = exigir(200, post("/api/auth/login")
                .cuerpo(Map.of("usuario", "admin", "clave", CLAVE_ADMIN)).enviar());
        assertEquals("admin", login.en("usuario.usuario"));
        assertFalse(login.existe("usuario.clave"));
        String token = login.texto("token");

        // El panel crea el servicio con sus ventanas (30 min, todos los días de 09:00 a 12:00).
        UUID servicio = UUID.randomUUID();
        List<Map<String, Object>> ventanas = new ArrayList<>();
        for (int dia = 1; dia <= 7; dia++) {
            ventanas.add(Map.of("dia", dia, "inicio", "09:00", "fin", "12:00"));
        }
        exigir(200, put("/api/panel/servicios/" + servicio).conToken(token)
                .cuerpo(Map.of("nombre", "Corte", "duracionMin", 30, "activo", true, "valor", 40000,
                        "ventanas", ventanas))
                .enviar());

        // 2. Enlace de 24 h con 1 uso.
        Resp enlace = exigir(201, post("/api/panel/enlaces").conToken(token)
                .cuerpo(Map.of("duracionMinutos", 1440, "maxUsos", 1)).enviar());
        assertEquals("Activo", enlace.en("estado"));
        igualNumero("1", enlace.numero("maxUsos"));
        igualNumero("0", enlace.numero("usos"));
        // 08:00 en Bogotá = 13:00 UTC; 24 h después.
        assertEquals("2026-11-10T13:00:00Z", enlace.en("venceEn"));
        String codigo = enlace.texto("codigo");
        String base = "/api/publico/invitaciones/" + codigo;

        // 3. El cliente, sin token: el enlace está Activo.
        assertEquals("Activo", exigir(200, get(base).enviar()).en("estado"));

        // 4. Servicios activos.
        Resp servicios = exigir(200, get(base + "/servicios").enviar());
        assertEquals(servicio.toString(), servicios.en("[0].id"));
        assertEquals("Corte", servicios.en("[0].nombre"));

        // 5. Horarios libres de mañana.
        Resp horarios = exigir(200, get(base + "/servicios/" + servicio + "/horarios?fecha=" + MANANA).enviar());
        assertTrue(horarios.lista("$").contains("09:00"), horarios.texto());
        assertTrue(horarios.lista("$").contains("11:30"), horarios.texto());

        // 6. Reserva.
        Resp reserva = exigir(201, post(base + "/citas").cuerpo(reserva(servicio, "09:00", "ana@example.com"))
                .enviar());
        assertEquals("Confirmada", reserva.en("estado"));
        assertEquals("09:00", reserva.en("inicio"));
        assertEquals("09:30", reserva.en("fin"));
        assertEquals("Agotado", get(base).enviar().en("estado"));

        // 7. La cita aparece en el panel.
        Resp citas = exigir(200, get("/api/panel/citas").conToken(token).enviar());
        assertEquals(1, citas.lista("$").size());
        assertEquals(servicio.toString(), citas.en("[0].servicioId"));
        assertEquals(MANANA, citas.en("[0].fecha"));
        assertEquals("09:00", citas.en("[0].inicio"));
        assertEquals("Confirmada", citas.en("[0].estado"));
        String citaId = citas.texto("[0].id");

        // 8. Una segunda reserva con el mismo enlace: 410 ENLACE_AGOTADO y no se crea nada.
        exigir(410, "ENLACE_AGOTADO", post(base + "/citas").cuerpo(reserva(servicio, "09:30", "bea@example.com"))
                .enviar());
        assertEquals(1, contar("cita"));
        assertEquals(1, contar("cliente"));

        // Con la cita confirmada, el horario de las 09:00 ya no se ofrece (por el panel; el enlace está agotado).
        Resp delPanel = exigir(200, get("/api/panel/servicios/" + servicio + "/horarios?fecha=" + MANANA)
                .conToken(token).enviar());
        assertFalse(delPanel.lista("$").contains("09:00"), delPanel.texto());
        assertTrue(delPanel.lista("$").contains("09:30"), delPanel.texto());

        // 9. El panel cancela la cita.
        Resp cancelada = exigir(200, post("/api/panel/citas/" + citaId + "/cancelar").conToken(token).enviar());
        assertEquals("Cancelada", cancelada.en("estado"));
        assertNotNull(cancelada.en("canceladaEn"));

        // 10. El horario vuelve a salir libre (por el panel: el enlace ya agotado no sirve para consultar).
        Resp otraVez = exigir(200, get("/api/panel/servicios/" + servicio + "/horarios?fecha=" + MANANA)
                .conToken(token).enviar());
        assertTrue(otraVez.lista("$").contains("09:00"), otraVez.texto());
        exigir(410, "ENLACE_AGOTADO", get(base + "/servicios/" + servicio + "/horarios?fecha=" + MANANA).enviar());
        // Y un enlace nuevo lo ofrece al cliente otra vez.
        String otro = exigir(201, post("/api/panel/enlaces").conToken(token).enviar()).texto("codigo");
        Resp alCliente = exigir(200, get("/api/publico/invitaciones/" + otro + "/servicios/" + servicio
                + "/horarios?fecha=" + MANANA).enviar());
        assertTrue(alCliente.lista("$").contains("09:00"), alCliente.texto());
        Resp confirmadas = exigir(200, get("/api/panel/citas?estado=Confirmada").conToken(token).enviar());
        assertEquals(0, confirmadas.lista("$").size());
        assertNull(confirmadas.en("[0]"));
    }

    private static Map<String, Object> reserva(UUID servicio, String inicio, String correo) {
        return Map.of("servicioId", servicio.toString(), "fecha", MANANA, "inicio", inicio,
                "cliente", Map.of("nombre", "Ana", "apellido", "Ruiz", "telefono", "300 111", "correo", correo));
    }
}
