package com.datemanager.infraestructura.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import org.junit.jupiter.api.Test;

class ManejadorErroresTest {

    @Test
    void cadaCodigoTieneSuEstadoHttp() {
        Map<CodigoError, Integer> esperado = Map.ofEntries(
                Map.entry(CodigoError.VALIDACION, 400),
                Map.entry(CodigoError.NO_AUTENTICADO, 401),
                Map.entry(CodigoError.CREDENCIALES_INVALIDAS, 401),
                Map.entry(CodigoError.SIN_PERMISO, 403),
                Map.entry(CodigoError.NO_ENCONTRADO, 404),
                Map.entry(CodigoError.ENLACE_NO_ENCONTRADO, 404),
                Map.entry(CodigoError.HORARIO_NO_DISPONIBLE, 409),
                Map.entry(CodigoError.ESTADO_INVALIDO, 409),
                Map.entry(CodigoError.CITA_NO_HA_INICIADO, 409),
                Map.entry(CodigoError.CONFLICTO, 409),
                Map.entry(CodigoError.ENLACE_VENCIDO, 410),
                Map.entry(CodigoError.ENLACE_AGOTADO, 410),
                Map.entry(CodigoError.ENLACE_DESACTIVADO, 410),
                Map.entry(CodigoError.ERROR_INTERNO, 500));

        assertEquals(Set.of(CodigoError.values()), esperado.keySet());
        esperado.forEach((codigo, estado) ->
                assertEquals(estado, ManejadorErrores.estadoHttp(codigo), codigo.name()));
    }

    @Test
    void unErrorDeNegocioSeSerializaComoProblemDetailsConCodigoYMensaje() {
        Respuesta r = ManejadorErrores.aRespuesta(
                new ErrorNegocio(CodigoError.HORARIO_NO_DISPONIBLE, "Ese horario ya no está disponible."),
                "/api/citas");

        assertEquals(409, r.estado());
        assertEquals("{\"type\":\"about:blank\",\"title\":\"Conflict\",\"status\":409,"
                + "\"detail\":\"Ese horario ya no está disponible.\",\"instance\":\"/api/citas\","
                + "\"codigo\":\"HORARIO_NO_DISPONIBLE\",\"mensaje\":\"Ese horario ya no está disponible.\"}",
                Json.escribir(r.cuerpo()));
    }

    @Test
    void losCamposDeUnaValidacionViajanYSinInstanceSiNoSeConoce() {
        Map<String, String> campos = Map.of("nombre", "no puede estar vacío");

        Respuesta r = ManejadorErrores.aRespuesta(
                new ErrorNegocio(CodigoError.VALIDACION, "Hay datos inválidos en la petición.", campos));

        assertEquals(400, r.estado());
        assertEquals("{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,"
                + "\"detail\":\"Hay datos inválidos en la petición.\",\"codigo\":\"VALIDACION\","
                + "\"mensaje\":\"Hay datos inválidos en la petición.\","
                + "\"campos\":{\"nombre\":\"no puede estar vacío\"}}",
                Json.escribir(r.cuerpo()));
    }

    @Test
    void unJsonInvalidoEsUn400ConElMensajeGenericoQueNoReflejaLoRecibido() {
        Respuesta r = ManejadorErrores.aRespuesta(new JsonInvalido("JSON inválido en la posición 3: secreto"));

        assertEquals(400, r.estado());
        ManejadorErrores.Problema problema = assertInstanceOf(ManejadorErrores.Problema.class, r.cuerpo());
        assertEquals("VALIDACION", problema.codigo());
        assertEquals("Hay datos inválidos en la petición.", problema.mensaje());
        assertFalse(Json.escribir(problema).contains("secreto"));
        assertNull(problema.instance());
        assertNull(problema.campos());
    }

    @Test
    void cualquierOtraExcepcionEsUn500SinDetallesNiTrazas() {
        Logger registro = Logger.getLogger(ManejadorErrores.class.getName());
        Level nivel = registro.getLevel();
        registro.setLevel(Level.OFF);
        try {
            Respuesta r = ManejadorErrores.aRespuesta(
                    new SQLException("La cita se solapa. valor-de-la-base", "S0001", 50010), "/api/citas");

            assertEquals(500, r.estado());
            String json = Json.escribir(r.cuerpo());
            assertTrue(json.contains("\"codigo\":\"ERROR_INTERNO\""));
            assertTrue(json.contains("\"mensaje\":\"Ocurrió un error inesperado. Inténtalo de nuevo más tarde.\""));
            assertFalse(json.contains("valor-de-la-base"));
            assertFalse(json.contains("S0001"));
        } finally {
            registro.setLevel(nivel);
        }
    }
}
