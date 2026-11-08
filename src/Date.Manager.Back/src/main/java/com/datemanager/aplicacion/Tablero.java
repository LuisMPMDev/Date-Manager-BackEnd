package com.datemanager.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.datemanager.dominio.Cita;
import com.datemanager.dominio.Metricas.Alerta;
import com.datemanager.dominio.Metricas.CitasDia;
import com.datemanager.dominio.Metricas.OcupacionServicio;

/**
 * Datos del inicio del panel para un día. Las comparaciones (30 días, mismo día de la semana pasada) traen el valor
 * actual, el anterior y la variación relativa, que está vacía cuando el anterior es 0.
 */
public record Tablero(
        LocalDate fecha,
        CitasHoy citasHoy,
        ProximaCita proxima,
        Comparacion<Double> ocupacionHoy,
        Comparacion<Long> citasNuevas,
        Comparacion<Long> citasCanceladas,
        Comparacion<BigDecimal> ingresos,
        List<Alerta> alertas,
        int alertasExtra,
        List<CitasDia> citasPorDia,
        List<OcupacionServicio> ocupacionSemana,
        List<ProximaCita> proximas) {

    public record CitasHoy(int total, int atendidas) {
    }

    public record Comparacion<T extends Number>(T actual, T anterior, OptionalDouble variacion) {
    }

    /** Cita por venir con los nombres que el tablero muestra; {@code minutosHasta} solo si es hoy. */
    public record ProximaCita(Cita cita, String servicioNombre, String clienteNombre, String clienteApellido,
            boolean esHoy, OptionalInt minutosHasta) {
    }
}
