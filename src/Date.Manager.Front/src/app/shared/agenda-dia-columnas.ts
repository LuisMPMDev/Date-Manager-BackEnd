import { aMinutos, isoDia } from '../core/horarios';
import { Cita, Cliente, Servicio } from '../core/models';
import { aFechaISO } from './formato';
import { restar, Tramo, unir } from './regla-bloques';
import { BloqueRegla } from './regla-tiempo';

export interface ColumnaAgenda { servicioId: string; nombre: string; bloques: BloqueRegla[] }
export interface AgendaDelDia { desdeMin: number; hastaMin: number; columnas: ColumnaAgenda[] }

/** «Camila Rojas» -> «Camila R.» */
function nombreCorto(cliente: Cliente | undefined): string {
  if (!cliente) return 'Cliente';
  const inicial = cliente.apellido.trim().charAt(0);
  return inicial ? `${cliente.nombre} ${inicial}.` : cliente.nombre;
}

/**
 * Lo que dibuja la agenda de un día: una columna por servicio activo con el mismo rango de horas.
 * - El rango cubre todas las ventanas de ese día (y las citas dibujadas), redondeado a horas completas.
 * - Lo que queda fuera de las ventanas del servicio va «fuera» (rayado).
 * - Citas confirmadas son «cita»; atendidas o ya terminadas, «atendida»; canceladas no se dibujan.
 * Devuelve null si ningún servicio activo atiende ese día.
 */
export function construirAgendaDia(p: {
  fecha: string;
  servicios: Servicio[];
  citas: Cita[];
  clientes: Cliente[];
  ahora: Date;
}): AgendaDelDia | null {
  const dia = isoDia(p.fecha);
  const activos = p.servicios.filter(s => s.activo);
  const ventanasDe = (s: Servicio): Tramo[] =>
    unir(s.ventanas.filter(v => v.dia === dia).map(v => [aMinutos(v.inicio), aMinutos(v.fin)] as Tramo));
  const ventanas = new Map(activos.map(s => [s.id, ventanasDe(s)]));

  const citasDelDia = p.citas.filter(
    c => c.fecha === p.fecha && c.estado !== 'Cancelada' && ventanas.has(c.servicioId),
  );
  if (![...ventanas.values()].some(v => v.length)) return null;

  const extremos = [
    ...[...ventanas.values()].flat().flat(),
    ...citasDelDia.flatMap(c => [aMinutos(c.inicio), aMinutos(c.fin)]),
  ];

  const desdeMin = Math.floor(Math.min(...extremos) / 60) * 60;
  const hastaMin = Math.ceil(Math.max(...extremos) / 60) * 60;

  const hoy = aFechaISO(p.ahora);
  const minutosAhora = p.ahora.getHours() * 60 + p.ahora.getMinutes();
  const yaPaso = (c: Cita) => p.fecha < hoy || (p.fecha === hoy && aMinutos(c.fin) <= minutosAhora);

  const columnas = activos.map((s): ColumnaAgenda => {
    const fuera = restar([[desdeMin, hastaMin]], ventanas.get(s.id)!).map(
      ([d, h]): BloqueRegla => ({ desdeMin: d, hastaMin: h, tipo: 'fuera' }),
    );
    const citas = citasDelDia
      .filter(c => c.servicioId === s.id)
      .map((c): BloqueRegla => ({
        desdeMin: aMinutos(c.inicio),
        hastaMin: aMinutos(c.fin),
        tipo: c.estado === 'Atendida' || yaPaso(c) ? 'atendida' : 'cita',
        etiqueta: `${c.inicio} · ${nombreCorto(p.clientes.find(x => x.id === c.clienteId))}`,
      }));
    return { servicioId: s.id, nombre: s.nombre, bloques: [...fuera, ...citas] };
  });
  return { desdeMin, hastaMin, columnas };
}
