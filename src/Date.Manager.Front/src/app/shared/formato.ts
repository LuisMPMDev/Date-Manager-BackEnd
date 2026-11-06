import { isoDia } from '../core/horarios';

export const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

const PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

/** Fecha local de `d` como 'YYYY-MM-DD'. */
export function aFechaISO(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/** Suma (o resta) días de calendario a 'YYYY-MM-DD'. */
export function sumarDias(fecha: string, dias: number): string {
  const [y, m, d] = fecha.split('-').map(Number);
  return aFechaISO(new Date(y, m - 1, d + dias));
}

/** '2026-11-10' -> 'Martes 10 de noviembre' (tablas propias: no depende del idioma del entorno). */
export function fechaLarga(fecha: string): string {
  const [, m, d] = fecha.split('-').map(Number);
  return `${DIAS[isoDia(fecha) - 1]} ${d} de ${MESES[m - 1]}`;
}

export function moneda(valor: number): string {
  return PESOS.format(valor);
}
