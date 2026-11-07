// Los formateadores de fecha son lógica pura de `core`; se reexportan aquí para las pantallas.
export { aFechaISO, DIAS, fechaLarga, sumarDias } from '../core/fechas';

const PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

export function moneda(valor: number): string {
  return PESOS.format(valor);
}
