package com.datemanager.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
    @ValueSource(ints = {0, 1205, 1222, 50010, 515, 2628, 548})
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
