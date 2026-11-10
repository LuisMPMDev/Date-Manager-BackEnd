package com.datemanager.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.orm.jpa.JpaSystemException;

class ErroresBaseTest {

    private static UncategorizedSQLException envuelta(int codigo) {
        return new UncategorizedSQLException("guardar", "INSERT ...", new SQLException("x", "S0001", codigo));
    }

    @ParameterizedTest
    @ValueSource(ints = {50000, 50010, 50099})
    void losNumerosDeDisparadorSeReconocen(int codigo) {
        assertThat(ErroresBase.esViolacionDeDisparador(envuelta(codigo))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1205, 2601, 49999, 50100})
    void losDemasErroresDeLaBaseNo(int codigo) {
        assertThat(ErroresBase.esViolacionDeDisparador(envuelta(codigo))).isFalse();
    }

    @Test
    void seBuscaEnTodaLaCadenaDeCausas() {
        RuntimeException profunda = new JpaSystemException(new RuntimeException(envuelta(50010)));

        assertThat(ErroresBase.esViolacionDeDisparador(profunda)).isTrue();
    }

    @Test
    void sinSqlExceptionONuloNo() {
        assertThat(ErroresBase.esViolacionDeDisparador(new IllegalStateException("50010"))).isFalse();
        assertThat(ErroresBase.esViolacionDeDisparador(null)).isFalse();
    }
}
