package com.datemanager.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ErroresBaseTest {

    /** Como llega en la práctica: la SQLException envuelta en una excepción sin marcar. */
    private static RuntimeException envuelta(int codigo) {
        return new ErrorSql(new SQLException("x", "S0001", codigo));
    }

    @ParameterizedTest
    @ValueSource(ints = {50000, 50010, 50099})
    void losNumerosDeDisparadorSeReconocen(int codigo) {
        assertTrue(ErroresBase.esViolacionDeDisparador(envuelta(codigo)));
        assertFalse(ErroresBase.esViolacionDeRestriccion(envuelta(codigo)));
        assertFalse(ErroresBase.esFalloDeBloqueo(envuelta(codigo)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1205, 2601, 49999, 50100})
    void losDemasErroresDeLaBaseNoSonDeDisparador(int codigo) {
        assertFalse(ErroresBase.esViolacionDeDisparador(envuelta(codigo)));
    }

    @ParameterizedTest
    @ValueSource(ints = {2627, 2601, 547})
    void losNumerosDeRestriccionSeReconocen(int codigo) {
        assertTrue(ErroresBase.esViolacionDeRestriccion(envuelta(codigo)));
        assertFalse(ErroresBase.esViolacionDeDisparador(envuelta(codigo)));
        assertFalse(ErroresBase.esFalloDeBloqueo(envuelta(codigo)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1205, 1222, 50010, 548, 102, 208})
    void losDemasNoSonDeRestriccion(int codigo) {
        assertFalse(ErroresBase.esViolacionDeRestriccion(envuelta(codigo)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1222, 1205})
    void losFallosDeBloqueoSeReconocen(int codigo) {
        assertTrue(ErroresBase.esFalloDeBloqueo(envuelta(codigo)));
        assertFalse(ErroresBase.esViolacionDeRestriccion(envuelta(codigo)));
        assertFalse(ErroresBase.esViolacionDeDisparador(envuelta(codigo)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2627, 50010, 1204, 1223})
    void losDemasNoSonFalloDeBloqueo(int codigo) {
        assertFalse(ErroresBase.esFalloDeBloqueo(envuelta(codigo)));
    }

    private static RuntimeException conEstado(int codigo, String estado) {
        return new ErrorSql(new SQLException("x", estado, codigo));
    }

    /** Lo que traducía el SQLStateSQLExceptionTranslator de Spring a DataIntegrityViolationException: clases 22, 23, 27, 44. */
    @ParameterizedTest
    @CsvSource({"515,23000", "8152,22001", "2628,22001", "8115,22003", "547,23000", "9999,22012", "1,27000",
            "2,44000", "0,23505", "0,22P02"})
    void unSqlStateDeIntegridadDeDatosEsViolacionDeRestriccionSeaCualSeaElNumero(int codigo, String estado) {
        assertTrue(ErroresBase.esViolacionDeRestriccion(conEstado(codigo, estado)));
        assertTrue(ErroresBase.esViolacionDeRestriccion(new RuntimeException(new RuntimeException(
                conEstado(codigo, estado)))), "también en la cadena de causas");
        assertFalse(ErroresBase.esFalloDeBloqueo(conEstado(codigo, estado)));
    }

    @ParameterizedTest
    @CsvSource({"102,42000", "208,42S02", "0,S0001", "1205,40001", "1222,S00051", "50010,S0001", "0,08S01",
            "0,21000", "0,24000", "0,2", "0,12345"})
    void otrosSqlStateNoSonViolacionDeRestriccion(int codigo, String estado) {
        assertFalse(ErroresBase.esViolacionDeRestriccion(conEstado(codigo, estado)));
    }

    /** El driver informa 8152 y 2628 con SQLState S0001: se reconocen por número (como el 409 de la aplicación original). */
    @ParameterizedTest
    @ValueSource(ints = {515, 8152, 2628, 8114, 8115, 544})
    void losErroresDeDatosDeSqlServerSeReconocenPorNumeroAunqueElEstadoSeaGeneral(int codigo) {
        assertTrue(ErroresBase.esViolacionDeRestriccion(conEstado(codigo, "S0001")));
    }

    @Test
    void unSqlStateNuloNoRompeNada() {
        assertFalse(ErroresBase.esViolacionDeRestriccion(new ErrorSql(new SQLException("x", (String) null, 0))));
        assertTrue(ErroresBase.esViolacionDeRestriccion(new ErrorSql(new SQLException("x", (String) null, 2627))));
    }

    @Test
    void elSqlStateSeBuscaEnTodaLaCadenaYUnCicloTerminaSinColgarse() {
        RuntimeException profunda = new IllegalStateException("a", new RuntimeException("b", conEstado(0, "23000")));
        assertTrue(ErroresBase.esViolacionDeRestriccion(profunda));

        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertFalse(ErroresBase.esViolacionDeRestriccion(a));
    }

    @Test
    void seBuscaEnTodaLaCadenaDeCausas() {
        RuntimeException profunda = new IllegalStateException("a",
                new RuntimeException("b", new RuntimeException(envuelta(50010))));
        assertTrue(ErroresBase.esViolacionDeDisparador(profunda));

        assertTrue(ErroresBase.esViolacionDeRestriccion(new RuntimeException(new RuntimeException(envuelta(2627)))));
        assertTrue(ErroresBase.esFalloDeBloqueo(new RuntimeException(new RuntimeException(envuelta(1205)))));
    }

    @Test
    void sinSqlExceptionONuloNo() {
        IllegalStateException sinSql = new IllegalStateException("50010 2627 1205");

        assertFalse(ErroresBase.esViolacionDeDisparador(sinSql));
        assertFalse(ErroresBase.esViolacionDeRestriccion(sinSql));
        assertFalse(ErroresBase.esFalloDeBloqueo(sinSql));
        assertFalse(ErroresBase.esViolacionDeDisparador(null));
        assertFalse(ErroresBase.esViolacionDeRestriccion(null));
        assertFalse(ErroresBase.esFalloDeBloqueo(null));
    }

    @Test
    void unaCadenaDeCausasConCicloTerminaSinColgarse() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);

        assertFalse(ErroresBase.esViolacionDeDisparador(a));
        assertFalse(ErroresBase.esViolacionDeRestriccion(a));
        assertFalse(ErroresBase.esFalloDeBloqueo(a));
    }
}
