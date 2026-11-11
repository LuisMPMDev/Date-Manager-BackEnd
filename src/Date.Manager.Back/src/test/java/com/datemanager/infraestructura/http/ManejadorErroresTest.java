package com.datemanager.infraestructura.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.infraestructura.persistencia.ErrorSql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
                    new SQLException("Invalid object name. valor-de-la-base", "S0001", 208), "/api/citas");

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

    // ---- Errores de la base ----

    private static final String MENSAJE_CONFLICTO = "No se pudo guardar porque entra en conflicto con datos que ya existen.";
    private static final String MENSAJE_EN_CURSO = "Hay otra operación en curso; inténtalo de nuevo.";

    /** Captura lo que el manejador registra, con el texto ya formateado y el mensaje de la excepción adjunta. */
    private static final class Captura extends Handler {
        final List<LogRecord> registros = new ArrayList<>();

        @Override
        public void publish(LogRecord registro) {
            registros.add(registro);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        /** Todo lo registrado como un solo texto: mensaje formateado, parámetros y la excepción, si la hubiera. */
        String texto() {
            StringBuilder t = new StringBuilder();
            for (LogRecord r : registros) {
                t.append(new java.util.logging.SimpleFormatter().format(r));
            }
            return t.toString();
        }
    }

    private static Respuesta conCaptura(Throwable e, Captura captura) {
        Logger registro = Logger.getLogger(ManejadorErrores.class.getName());
        Level nivel = registro.getLevel();
        boolean padres = registro.getUseParentHandlers();
        registro.setLevel(Level.ALL);
        registro.setUseParentHandlers(false);
        registro.addHandler(captura);
        try {
            return ManejadorErrores.aRespuesta(e, "/api/citas");
        } finally {
            registro.removeHandler(captura);
            registro.setUseParentHandlers(padres);
            registro.setLevel(nivel);
        }
    }

    private static ErrorSql sql(int codigo) {
        return new ErrorSql(new SQLException("Violation of valor-de-la-base", "23000", codigo));
    }

    @ParameterizedTest
    @ValueSource(ints = {2627, 2601, 547})
    void unaViolacionDeRestriccionEsUn409ConElMensajeDeConflictoSinTextoDeLaBase(int codigo) {
        Captura captura = new Captura();

        Respuesta r = conCaptura(new RuntimeException("envoltorio", sql(codigo)), captura);

        assertEquals(409, r.estado());
        assertEquals("{\"type\":\"about:blank\",\"title\":\"Conflict\",\"status\":409,"
                + "\"detail\":\"" + MENSAJE_CONFLICTO + "\",\"instance\":\"/api/citas\","
                + "\"codigo\":\"CONFLICTO\",\"mensaje\":\"" + MENSAJE_CONFLICTO + "\"}",
                Json.escribir(r.cuerpo()));
        assertFalse(Json.escribir(r.cuerpo()).contains("valor-de-la-base"));
        assertFalse(captura.texto().contains("valor-de-la-base"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1222, 1205})
    void unFalloDeBloqueoEsUn409ConElMensajeDeOperacionEnCursoYSeRegistraSoloLaClase(int codigo) {
        Captura captura = new Captura();

        Respuesta r = conCaptura(sql(codigo), captura);

        assertEquals(409, r.estado());
        ManejadorErrores.Problema problema = assertInstanceOf(ManejadorErrores.Problema.class, r.cuerpo());
        assertEquals("CONFLICTO", problema.codigo());
        assertEquals(MENSAJE_EN_CURSO, problema.mensaje());
        assertEquals(MENSAJE_EN_CURSO, problema.detail());
        assertEquals(1, captura.registros.size());
        assertEquals(Level.WARNING, captura.registros.get(0).getLevel());
        assertNull(captura.registros.get(0).getThrown(), "sin traza: solo el nombre de la clase");
        assertTrue(captura.texto().contains("ErrorSql"), captura.texto());
        assertFalse(captura.texto().contains("valor-de-la-base"));
        assertFalse(captura.texto().contains("23000"));
    }

    @ParameterizedTest
    @ValueSource(ints = {50000, 50010, 50099})
    void elRechazoDeUnDisparadorEsUn409ConElMensajeDeConflictoYSeRegistraSoloLaClase(int codigo) {
        Captura captura = new Captura();

        Respuesta r = conCaptura(new IllegalStateException("otro envoltorio", sql(codigo)), captura);

        assertEquals(409, r.estado());
        ManejadorErrores.Problema problema = assertInstanceOf(ManejadorErrores.Problema.class, r.cuerpo());
        assertEquals("CONFLICTO", problema.codigo());
        assertEquals(MENSAJE_CONFLICTO, problema.mensaje());
        assertFalse(Json.escribir(problema).contains("valor-de-la-base"));
        assertEquals(1, captura.registros.size());
        assertEquals(Level.WARNING, captura.registros.get(0).getLevel());
        assertNull(captura.registros.get(0).getThrown());
        assertTrue(captura.texto().contains("IllegalStateException"), captura.texto());
        assertFalse(captura.texto().contains("valor-de-la-base"));
    }

    @Test
    void otroErrorDeLaBaseSigueSiendoUn500() {
        Logger registro = Logger.getLogger(ManejadorErrores.class.getName());
        Level nivel = registro.getLevel();
        registro.setLevel(Level.OFF);
        try {
            assertEquals(500, ManejadorErrores.aRespuesta(sql(515)).estado());
            assertEquals(500, ManejadorErrores.aRespuesta(sql(0)).estado());
            assertEquals(500, ManejadorErrores.aRespuesta(sql(50100)).estado());
        } finally {
            registro.setLevel(nivel);
        }
    }

    @Test
    void unErrorDeNegocioPrevaleceSobreUnaCausaDeLaBase() {
        ErrorNegocio negocio = new ErrorNegocio(CodigoError.SIN_PERMISO, "No tienes permiso para realizar esta acción.");
        negocio.initCause(sql(2627));

        assertEquals(403, ManejadorErrores.aRespuesta(negocio).estado());
    }
}
