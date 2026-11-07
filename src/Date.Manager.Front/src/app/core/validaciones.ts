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
  return mensajeDeVentanasCruzadas(s);
}

/** Dos ventanas del mismo día no pueden cruzarse (que una termine justo cuando empieza la otra sí vale). */
function mensajeDeVentanasCruzadas(s: Servicio): string | null {
  for (let dia = 1; dia <= 7; dia++) {
    const delDia = s.ventanas.filter(v => v.dia === dia).sort((a, b) => aMinutos(a.inicio) - aMinutos(b.inicio));
    for (let i = 1; i < delDia.length; i++) {
      const antes = delDia[i - 1];
      const despues = delDia[i];
      if (aMinutos(despues.inicio) < aMinutos(antes.fin)) {
        return (
          `${DIAS[dia - 1]}: las ventanas ${antes.inicio}–${antes.fin} y ${despues.inicio}–${despues.fin} se cruzan. ` +
          'Ajusta las horas para que una termine donde empieza la otra o antes.'
        );
      }
    }
  }
  return null;
}

const CORREO = /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/;

/** Una sola regla para el correo en toda la app: lo que se acepta al reservar se puede guardar en Clientes. */
export function esCorreoValido(correo: string): boolean {
  return CORREO.test(correo.trim());
}
