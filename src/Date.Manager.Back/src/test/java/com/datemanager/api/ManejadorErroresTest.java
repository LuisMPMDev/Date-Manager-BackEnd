package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.datemanager.dominio.CodigoError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ManejadorErroresTest {

    @Test
    void cadaCodigoTieneSuEstadoHttp() {
        Map<CodigoError, HttpStatus> esperado = Map.ofEntries(
                Map.entry(CodigoError.VALIDACION, HttpStatus.BAD_REQUEST),
                Map.entry(CodigoError.NO_AUTENTICADO, HttpStatus.UNAUTHORIZED),
                Map.entry(CodigoError.CREDENCIALES_INVALIDAS, HttpStatus.UNAUTHORIZED),
                Map.entry(CodigoError.SIN_PERMISO, HttpStatus.FORBIDDEN),
                Map.entry(CodigoError.NO_ENCONTRADO, HttpStatus.NOT_FOUND),
                Map.entry(CodigoError.ENLACE_NO_ENCONTRADO, HttpStatus.NOT_FOUND),
                Map.entry(CodigoError.HORARIO_NO_DISPONIBLE, HttpStatus.CONFLICT),
                Map.entry(CodigoError.ESTADO_INVALIDO, HttpStatus.CONFLICT),
                Map.entry(CodigoError.CITA_NO_HA_INICIADO, HttpStatus.CONFLICT),
                Map.entry(CodigoError.CONFLICTO, HttpStatus.CONFLICT),
                Map.entry(CodigoError.ENLACE_VENCIDO, HttpStatus.GONE),
                Map.entry(CodigoError.ENLACE_AGOTADO, HttpStatus.GONE),
                Map.entry(CodigoError.ENLACE_DESACTIVADO, HttpStatus.GONE),
                Map.entry(CodigoError.ERROR_INTERNO, HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(esperado).containsOnlyKeys(CodigoError.values());
        esperado.forEach((codigo, estado) ->
                assertThat(ManejadorErrores.estadoHttp(codigo)).as(codigo.name()).isEqualTo(estado));
    }
}
