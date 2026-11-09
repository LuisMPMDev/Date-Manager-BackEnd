import { isoDia } from '../../core/horarios';
import { CitasDia, Comparacion, Inicio, OcupacionServicio } from '../../core/inicio';
import { DIAS, fechaLarga, moneda } from '../../shared/formato';

/**
 * Presentación del tablero: la API manda las cifras ya calculadas y aquí solo se les da forma de texto
 * (porcentajes, moneda, flechas, descripciones de las gráficas).
 */

export type Tono = 'ok' | 'mal' | 'neutro';

export interface Kpi {
  id: 'citas-hoy' | 'proxima' | 'ocupacion' | 'nuevas' | 'canceladas' | 'ingresos';
  etiqueta: string;
  valor: string;
  detalle: string;
  tono: Tono;
}

/** Datos de una gráfica de barras con su descripción en texto. */
export interface SerieGrafica {
  etiquetas: string[];
  valores: number[];
  resumen: string;
}

const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/**
 * Texto de comparación con ▲/▼. `subeEsBueno` decide el tono: más ingresos es bueno, más cancelaciones es malo.
 * Sin variación (el valor anterior era 0, no hay con qué comparar) solo queda «—».
 */
function comparar(c: Comparacion, contra: string, subeEsBueno: boolean): Pick<Kpi, 'detalle' | 'tono'> {
  if (c.variacion === undefined) return { detalle: '—', tono: 'neutro' };
  const pct = Math.round(Math.abs(c.variacion) * 100);
  if (pct === 0) return { detalle: `Sin cambios vs. ${contra}`, tono: 'neutro' };
  const sube = c.variacion > 0;
  return { detalle: `${sube ? '▲' : '▼'} ${pct}% vs. ${contra}`, tono: sube === subeEsBueno ? 'ok' : 'mal' };
}

function kpiProxima(inicio: Inicio): Kpi {
  const base = { id: 'proxima', etiqueta: 'Próxima cita' } as const;
  const { proxima } = inicio;
  if (!proxima) return { ...base, valor: '—', detalle: 'Sin citas próximas', tono: 'neutro' };

  const cuando = proxima.esHoy && proxima.minutosHasta !== undefined
    ? `en ${proxima.minutosHasta} min`
    : fechaLarga(proxima.fecha);
  return { ...base, valor: proxima.inicio, detalle: `${cuando} · ${proxima.servicioNombre}`, tono: 'neutro' };
}

export function construirKpis(inicio: Inicio): Kpi[] {
  const { citasHoy } = inicio;
  const diaPasado = `${DIAS[isoDia(inicio.fecha) - 1].toLowerCase()} pasado`;

  return [
    {
      id: 'citas-hoy', etiqueta: 'Citas hoy', valor: String(citasHoy.total), tono: 'neutro',
      detalle: `${citasHoy.atendidas} ya ${citasHoy.atendidas === 1 ? 'atendida' : 'atendidas'}`,
    },
    kpiProxima(inicio),
    {
      id: 'ocupacion', etiqueta: 'Ocupación de hoy', valor: `${Math.round(inicio.ocupacionHoy.actual * 100)}%`,
      ...comparar(inicio.ocupacionHoy, diaPasado, true),
    },
    {
      id: 'nuevas', etiqueta: 'Citas nuevas (30 días)', valor: String(inicio.citasNuevas.actual),
      ...comparar(inicio.citasNuevas, '30 días antes', true),
    },
    {
      id: 'canceladas', etiqueta: 'Canceladas (30 días)', valor: String(inicio.citasCanceladas.actual),
      ...comparar(inicio.citasCanceladas, '30 días antes', false),
    },
    {
      id: 'ingresos', etiqueta: 'Ingresos estimados', valor: moneda(inicio.ingresos.actual),
      ...comparar(inicio.ingresos, '30 días antes', true),
    },
  ];
}

/** '2026-11-09' -> '9 nov' */
function etiquetaCorta(fecha: string): string {
  const [, m, d] = fecha.split('-').map(Number);
  return `${d} ${MESES_CORTOS[m - 1]}`;
}

/** Citas no canceladas por día, de los últimos 30 días hasta hoy. */
export function serieCitasPorDia(dias: CitasDia[]): SerieGrafica {
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

/** Ocupación (0 a 100) de cada servicio activo en la semana de lunes a domingo que contiene hoy. */
export function serieOcupacion(servicios: OcupacionServicio[]): SerieGrafica {
  const etiquetas = servicios.map(s => s.nombre);
  const valores = servicios.map(s => Math.round(s.ocupacion * 100));

  const detalle = etiquetas.length
    ? etiquetas.map((nombre, i) => `${nombre} ${valores[i]}%`).join(', ')
    : 'no hay servicios activos';
  return { etiquetas, valores, resumen: `Ocupación por servicio esta semana: ${detalle}.` };
}
