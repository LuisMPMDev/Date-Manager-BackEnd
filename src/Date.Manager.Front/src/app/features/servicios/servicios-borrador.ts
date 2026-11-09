import { nuevoId } from '../../core/ids';
import { DiaSemana, Servicio, VentanaDisponibilidad } from '../../core/models';
import { DIAS } from '../../shared/formato';

/** Lo que se edita en el formulario: números como texto, tal cual los escribe la persona. */
export interface BorradorServicio {
  id: string;
  nombre: string;
  duracion: string;
  valor: string;
  activo: boolean;
  ventanas: VentanaDisponibilidad[];
}

export const OPCIONES_DIA: { valor: DiaSemana; nombre: string }[] = DIAS.map((nombre, i) => ({
  valor: (i + 1) as DiaSemana,
  nombre,
}));

export function borradorVacio(): BorradorServicio {
  return { id: nuevoId(), nombre: '', duracion: '60', valor: '0', activo: true, ventanas: [] };
}

export function borradorDe(s: Servicio): BorradorServicio {
  return {
    id: s.id,
    nombre: s.nombre,
    duracion: String(s.duracionMin),
    valor: String(s.valor),
    activo: s.activo,
    ventanas: s.ventanas.map(v => ({ ...v })),
  };
}

/** Un texto vacío cuenta como número inválido (no como cero). */
function numero(texto: string): number {
  return texto.trim() === '' ? NaN : Number(texto);
}

export function servicioDe(b: BorradorServicio): Servicio {
  return {
    id: b.id,
    nombre: b.nombre.trim(),
    duracionMin: numero(b.duracion),
    valor: numero(b.valor),
    activo: b.activo,
    ventanas: b.ventanas.map(v => ({ ...v })),
  };
}

export function ventanaNueva(): VentanaDisponibilidad {
  return { id: nuevoId(), dia: 1, inicio: '09:00', fin: '12:00' };
}

/** «Lun 09:00–13:00, Lun 15:00–18:00, Mar …», ordenado por día y hora. */
export function resumenVentanas(ventanas: VentanaDisponibilidad[]): string {
  return [...ventanas]
    .sort((a, b) => a.dia - b.dia || a.inicio.localeCompare(b.inicio))
    .map(v => `${DIAS[v.dia - 1].slice(0, 3)} ${v.inicio}–${v.fin}`)
    .join(', ');
}
