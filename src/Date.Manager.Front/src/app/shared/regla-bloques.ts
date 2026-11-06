import { aHHmm, aMinutos, PASO_MIN } from '../core/horarios';
import { BloqueRegla, HuecoRegla } from './regla-tiempo';

export interface ReglaDia { desdeMin: number; hastaMin: number; bloques: BloqueRegla[]; huecos: HuecoRegla[] }

export type Tramo = [desde: number, hasta: number];

/** Une tramos que se tocan o se solapan; devuelve la lista ordenada. */
export function unir(tramos: Tramo[]): Tramo[] {
  const unidos: Tramo[] = [];
  for (const [d, h] of [...tramos].sort((a, b) => a[0] - b[0])) {
    const ultimo = unidos[unidos.length - 1];
    if (ultimo && d <= ultimo[1]) ultimo[1] = Math.max(ultimo[1], h);
    else unidos.push([d, h]);
  }
  return unidos;
}

/** Lo que queda de `base` (ya unida) al quitarle `quitar` (ya unida). Solo tramos no vacíos. */
export function restar(base: Tramo[], quitar: Tramo[]): Tramo[] {
  const resto: Tramo[] = [];
  for (const [d, h] of base) {
    let cursor = d;
    for (const [qd, qh] of quitar) {
      if (qh <= cursor || qd >= h) continue;
      if (qd > cursor) resto.push([cursor, qd]);
      cursor = Math.max(cursor, qh);
    }
    if (cursor < h) resto.push([cursor, h]);
  }
  return resto;
}

/**
 * Convierte los datos de un día de un servicio en lo que dibuja la regla de tiempo.
 * - El rango cubre las ventanas, redondeado hacia afuera a horas completas.
 * - Cada horario libre es un hueco clicable de un paso (PASO_MIN).
 * - Lo de cada ventana que no es la celda (PASO_MIN) de un inicio ofrecido va «ocupado»: todo minuto no reservable queda rayado.
 * - Lo que queda fuera de toda ventana dentro del rango va «fuera».
 * - El horario elegido es un bloque tan alto como la duración del servicio.
 * Devuelve null si ese día el servicio no tiene ventanas.
 */
export function construirRegla(p: {
  ventanas: { inicio: string; fin: string }[];
  duracionMin: number;
  libres: string[];
  elegido: string | null;
}): ReglaDia | null {
  const ventanas = unir(p.ventanas.map(v => [aMinutos(v.inicio), aMinutos(v.fin)] as Tramo));
  if (!ventanas.length) return null;

  const desdeMin = Math.floor(ventanas[0][0] / 60) * 60;
  const hastaMin = Math.ceil(ventanas[ventanas.length - 1][1] / 60) * 60;

  const iniciosMin = p.libres.map(aMinutos);
  // Cada inicio ofrecido es una celda de un paso: el resto de la ventana no se puede reservar.
  const cubierto = unir(iniciosMin.map(i => [i, i + PASO_MIN] as Tramo));

  const bloques: BloqueRegla[] = [
    ...restar([[desdeMin, hastaMin]], ventanas).map(([d, h]): BloqueRegla => ({ desdeMin: d, hastaMin: h, tipo: 'fuera', etiqueta: 'Fuera de horario' })),
    ...restar(ventanas, cubierto).map(([d, h]): BloqueRegla => ({ desdeMin: d, hastaMin: h, tipo: 'ocupado', etiqueta: 'No disponible' })),
  ];
  if (p.elegido) {
    const inicio = aMinutos(p.elegido);
    bloques.push({
      desdeMin: inicio,
      hastaMin: inicio + p.duracionMin,
      tipo: 'elegido',
      etiqueta: `${p.elegido} – ${aHHmm(inicio + p.duracionMin)}`,
    });
  }
  bloques.sort((a, b) => a.desdeMin - b.desdeMin);

  const huecos = iniciosMin.map((i): HuecoRegla => ({ desdeMin: i, hastaMin: i + PASO_MIN, etiqueta: aHHmm(i) }));
  return { desdeMin, hastaMin, bloques, huecos };
}
