import { aMinutos, isoDia } from '../core/horarios';
import { Cita, Cliente, Servicio } from '../core/models';
import { aFechaISO } from './formato';
import { restar, Tramo, unir } from './regla-bloques';
import { BloqueRegla } from './regla-tiempo';

export interface ColumnaAgenda { servicioId: string; nombre: string; inactivo: boolean; bloques: BloqueRegla[] }
export interface AgendaDelDia { desdeMin: number; hastaMin: number; columnas: ColumnaAgenda[] }

/** «Camila Rojas» -> «Camila R.» */
function nombreCorto(cliente: Cliente | undefined): string {
  if (!cliente) return 'Cliente';
  const inicial = cliente.apellido.trim().charAt(0);
  return inicial ? `${cliente.nombre} ${inicial}.` : cliente.nombre;
}

/**
 * Lo que dibuja la agenda de un día: una columna por servicio activo con el mismo rango de horas.
 * - Un servicio inactivo que ese día todavía tiene citas conserva su columna, marcada «inactivo»:
 *   todo va rayado (no ofrece horarios libres) y se ven sus citas para poder gestionarlas.
 * - El rango cubre todas las ventanas de ese día (y las citas dibujadas), redondeado a horas completas.
 * - Lo que queda fuera de las ventanas del servicio va «fuera» (rayado).
 * - Citas confirmadas son «cita»; atendidas o ya terminadas, «atendida»; canceladas no se dibujan.
 * Devuelve null si ningún servicio activo atiende ese día y no hay citas que mostrar.
 */
export function construirAgendaDia(p: {
  fecha: string;
  servicios: Servicio[];
  citas: Cita[];
  clientes: Cliente[];
  ahora: Date;
}): AgendaDelDia | null {
  const dia = isoDia(p.fecha);
  const vivasDelDia = p.citas.filter(c => c.fecha === p.fecha && c.estado !== 'Cancelada');
  const conCitas = new Set(vivasDelDia.map(c => c.servicioId));
  const mostrados = p.servicios.filter(s => s.activo || conCitas.has(s.id));
  const ventanasDe = (s: Servicio): Tramo[] =>
    s.activo ? unir(s.ventanas.filter(v => v.dia === dia).map(v => [aMinutos(v.inicio), aMinutos(v.fin)] as Tramo)) : [];
  const ventanas = new Map(mostrados.map(s => [s.id, ventanasDe(s)]));

  const citasDelDia = vivasDelDia.filter(c => ventanas.has(c.servicioId));
  if (![...ventanas.values()].some(v => v.length) && !citasDelDia.length) return null;

  const extremos = [
    ...[...ventanas.values()].flat().flat(),
    ...citasDelDia.flatMap(c => [aMinutos(c.inicio), aMinutos(c.fin)]),
  ];

  const desdeMin = Math.floor(Math.min(...extremos) / 60) * 60;
  const hastaMin = Math.ceil(Math.max(...extremos) / 60) * 60;

  const hoy = aFechaISO(p.ahora);
  const minutosAhora = p.ahora.getHours() * 60 + p.ahora.getMinutes();
  const yaPaso = (c: Cita) => p.fecha < hoy || (p.fecha === hoy && aMinutos(c.fin) <= minutosAhora);

  const columnas = mostrados.map((s): ColumnaAgenda => {
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
    return { servicioId: s.id, nombre: s.nombre, inactivo: !s.activo, bloques: [...fuera, ...citas] };
  });
  return { desdeMin, hastaMin, columnas };
}
