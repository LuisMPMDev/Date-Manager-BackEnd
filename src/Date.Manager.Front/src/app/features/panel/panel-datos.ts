import { isoDia } from '../../core/horarios';
import { Alerta, citasPorDia, ocupacionServicio } from '../../core/metricas';
import { Cita, Servicio } from '../../core/models';
import { sumarDias } from '../../shared/formato';

export const MAX_ALERTAS = 6;
const DIAS_SERIE = 30;
const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** Datos de una gráfica de barras con su descripción en texto. */
export interface SerieGrafica {
  etiquetas: string[];
  valores: number[];
  resumen: string;
}

/** '2026-11-09' -> '9 nov' */
function etiquetaCorta(fecha: string): string {
  const [, m, d] = fecha.split('-').map(Number);
  return `${d} ${MESES_CORTOS[m - 1]}`;
}

/** Citas no canceladas por día, de los últimos 30 días hasta hoy. */
export function serieCitasPorDia(citas: Cita[], hoy: string): SerieGrafica {
  const dias = citasPorDia(citas, sumarDias(hoy, -(DIAS_SERIE - 1)), hoy);
  const etiquetas = dias.map(d => etiquetaCorta(d.fecha));
  const valores = dias.map(d => d.total);
  const total = valores.reduce((suma, v) => suma + v, 0);

  let resumen = 'Citas por día, últimos 30 días: sin citas.';
  if (total > 0) {
    const mayor = valores.indexOf(Math.max(...valores));
    resumen = `Citas por día, últimos 30 días: ${total} ${total === 1 ? 'cita' : 'citas'}; el día con más citas fue ${etiquetas[mayor]} (${valores[mayor]}).`;
  }
  return { etiquetas, valores, resumen };
}

/** Ocupación (0 a 100) de cada servicio activo en la semana de lunes a domingo que contiene `hoy`. */
export function ocupacionSemana(servicios: Servicio[], citas: Cita[], hoy: string): SerieGrafica {
  const lunes = sumarDias(hoy, -(isoDia(hoy) - 1));
  const domingo = sumarDias(lunes, 6);
  const activos = servicios.filter(s => s.activo);
  const valores = activos.map(s => Math.round(ocupacionServicio(s, citas, lunes, domingo) * 100));
  const etiquetas = activos.map(s => s.nombre);

  const detalle = etiquetas.length
    ? etiquetas.map((nombre, i) => `${nombre} ${valores[i]}%`).join(', ')
    : 'no hay servicios activos';
  return { etiquetas, valores, resumen: `Ocupación por servicio esta semana: ${detalle}.` };
}

/** Las primeras alertas, en el orden recibido, y cuántas quedaron fuera. */
export function limitarAlertas(todas: Alerta[]): { visibles: Alerta[]; extra: number } {
  return { visibles: todas.slice(0, MAX_ALERTAS), extra: Math.max(0, todas.length - MAX_ALERTAS) };
}
