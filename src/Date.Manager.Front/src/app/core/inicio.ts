/** Respuesta de GET /api/panel/inicio. Las fracciones (ocupación, variación) van de 0 a 1. Las claves nulas se omiten. */

export interface CitasHoy { total: number; atendidas: number }

/** Un indicador comparado con su periodo anterior; `variacion` falta cuando el anterior es 0. */
export interface Comparacion { actual: number; anterior: number; variacion?: number }

export interface Alerta {
  tipo: 'cita_nueva' | 'cita_cancelada' | 'servicio_sin_horario';
  titulo: string;
  detalle: string;
  ref: string;
}

export interface CitasDia { fecha: string; total: number }

export interface OcupacionServicio { servicioId: string; nombre: string; ocupacion: number }

export interface ProximaCita {
  id: string;
  servicioId: string;
  servicioNombre: string;
  clienteId: string;
  clienteNombre: string;
  clienteApellido: string;
  clienteTelefono: string;
  fecha: string;
  inicio: string;
  fin: string;
  esHoy: boolean;
  minutosHasta?: number;
}

export interface Inicio {
  fecha: string;
  citasHoy: CitasHoy;
  proxima?: ProximaCita;
  ocupacionHoy: Comparacion;
  citasNuevas: Comparacion;
  citasCanceladas: Comparacion;
  ingresos: Comparacion;
  alertas: Alerta[];
  alertasExtra: number;
  citasPorDia: CitasDia[];
  ocupacionSemana: OcupacionServicio[];
  proximas: ProximaCita[];
}
