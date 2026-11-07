import { DIAS } from './fechas';
import { aMinutos } from './horarios';
import { Servicio } from './models';

const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

/** Mensaje de error en español si el servicio no se puede guardar; null si es válido. */
export function validarServicio(s: Servicio): string | null {
  if (!s.nombre.trim()) return 'Escribe el nombre del servicio.';
  if (!Number.isInteger(s.duracionMin) || s.duracionMin <= 0) {
    return 'La duración debe ser un número entero de minutos mayor que cero.';
  }
  if (!Number.isFinite(s.valor) || s.valor < 0) return 'El valor no puede ser negativo.';
  for (const v of s.ventanas) {
    const dia = DIAS[v.dia - 1] ?? 'Día';
    if (!HHMM.test(v.inicio) || !HHMM.test(v.fin)) return `${dia}: escribe la hora de inicio y de fin de la ventana.`;
    if (aMinutos(v.inicio) >= aMinutos(v.fin)) return `${dia}: la hora de fin debe ser posterior a la de inicio.`;
  }
  return null;
}
