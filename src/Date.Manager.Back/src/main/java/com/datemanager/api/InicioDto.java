package com.datemanager.api;

import java.util.List;
import java.util.UUID;

import com.datemanager.aplicacion.Tablero;
import com.datemanager.dominio.Cita;
import com.datemanager.dominio.Metricas.Alerta;
import com.datemanager.dominio.Metricas.CitasDia;
import com.datemanager.dominio.Metricas.OcupacionServicio;

/**
 * Inicio del panel. Fechas {@code YYYY-MM-DD}, horas {@code HH:mm}; las fracciones (ocupación, variación) van de 0 a
 * 1 ({@code 0.2} = 20 %) y los importes son números JSON.
 * <p>
 * Las claves sin valor se <b>omiten</b> (la aplicación usa {@code non_null}): {@code proxima} si no hay citas por
 * venir, {@code variacion} cuando el valor anterior es 0 (no hay con qué comparar) y {@code minutosHasta} cuando la
 * cita no es de hoy. Las listas vacías sí se publican como {@code []}.
 */
public record InicioDto(
        String fecha,
        CitasHoyDto citasHoy,
        ProximaCitaDto proxima,
        ComparacionDto ocupacionHoy,
        ComparacionDto citasNuevas,
        ComparacionDto citasCanceladas,
        ComparacionDto ingresos,
        List<AlertaDto> alertas,
        int alertasExtra,
        List<CitasDiaDto> citasPorDia,
        List<OcupacionServicioDto> ocupacionSemana,
        List<ProximaCitaDto> proximas) {

    public record CitasHoyDto(int total, int atendidas) {
    }

    public record ComparacionDto(Number actual, Number anterior, Double variacion) {

        static ComparacionDto de(Tablero.Comparacion<?> c) {
            return new ComparacionDto(c.actual(), c.anterior(),
                    c.variacion().isPresent() ? c.variacion().getAsDouble() : null);
        }
    }

    public record AlertaDto(String tipo, String titulo, String detalle, UUID ref) {

        static AlertaDto de(Alerta a) {
            return new AlertaDto(a.tipo().codigo(), a.titulo(), a.detalle(), a.ref());
        }
    }

    public record CitasDiaDto(String fecha, int total) {

        static CitasDiaDto de(CitasDia d) {
            return new CitasDiaDto(d.fecha().format(FormatoFechas.FECHA), d.total());
        }
    }

    public record OcupacionServicioDto(UUID servicioId, String nombre, double ocupacion) {

        static OcupacionServicioDto de(OcupacionServicio o) {
            return new OcupacionServicioDto(o.servicioId(), o.nombre(), o.ocupacion());
        }
    }

    public record ProximaCitaDto(UUID id, UUID servicioId, String servicioNombre, UUID clienteId, String clienteNombre,
            String clienteApellido, String fecha, String inicio, String fin, boolean esHoy, Integer minutosHasta) {

        static ProximaCitaDto de(Tablero.ProximaCita p) {
            Cita c = p.cita();
            return new ProximaCitaDto(c.id(), c.servicioId(), p.servicioNombre(), c.clienteId(), p.clienteNombre(),
                    p.clienteApellido(), c.fecha().format(FormatoFechas.FECHA), c.inicio().format(FormatoFechas.HORA),
                    c.fin().format(FormatoFechas.HORA), p.esHoy(),
                    p.minutosHasta().isPresent() ? p.minutosHasta().getAsInt() : null);
        }
    }

    public static InicioDto de(Tablero t) {
        return new InicioDto(t.fecha().format(FormatoFechas.FECHA),
                new CitasHoyDto(t.citasHoy().total(), t.citasHoy().atendidas()),
                t.proxima() == null ? null : ProximaCitaDto.de(t.proxima()),
                ComparacionDto.de(t.ocupacionHoy()), ComparacionDto.de(t.citasNuevas()),
                ComparacionDto.de(t.citasCanceladas()), ComparacionDto.de(t.ingresos()),
                t.alertas().stream().map(AlertaDto::de).toList(), t.alertasExtra(),
                t.citasPorDia().stream().map(CitasDiaDto::de).toList(),
                t.ocupacionSemana().stream().map(OcupacionServicioDto::de).toList(),
                t.proximas().stream().map(ProximaCitaDto::de).toList());
    }
}
