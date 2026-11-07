import { Cita, DiaSemana, Servicio } from './models';

export const PASO_MIN = 30;
export const MAX_DIAS_ANTICIPACION = 30;

const MS_DIA = 24 * 60 * 60 * 1000;

/** '09:30' -> 570 */
export function aMinutos(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + m;
}

/** 570 -> '09:30' */
export function aHHmm(min: number): string {
  const h = Math.floor(min / 60);
  const m = min % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

function partesFecha(fecha: string): [number, number, number] {
  const [y, m, d] = fecha.split('-').map(Number);
  return [y, m, d];
}

/** 'YYYY-MM-DD' -> 1..7 (lunes = 1, domingo = 7) */
export function isoDia(fecha: string): DiaSemana {
  const [y, m, d] = partesFecha(fecha);
  const js = new Date(y, m - 1, d).getDay(); // 0 = domingo
  return (js === 0 ? 7 : js) as DiaSemana;
}

export function sumarMinutos(hhmm: string, min: number): string {
  return aHHmm(aMinutos(hhmm) + min);
}

/** Dias de calendario entre `desde` (fecha local) y `fecha` ('YYYY-MM-DD'); negativo si es pasada. */
function diasDesde(desde: Date, fecha: string): number {
  const [y, m, d] = partesFecha(fecha);
  const objetivo = Date.UTC(y, m - 1, d);
  const base = Date.UTC(desde.getFullYear(), desde.getMonth(), desde.getDate());
  return Math.round((objetivo - base) / MS_DIA);
}

export function horariosLibres(p: {
  servicio: Servicio;
  fecha: string;
  citas: Cita[];
  ahora: Date;
  excluirCitaId?: string;
}): string[] {
  const { servicio, fecha, citas, ahora, excluirCitaId } = p;
  // Un servicio inactivo no se puede reservar; pero sus citas ya existentes sí se pueden mover (excluirCitaId).
  if (!servicio.activo && excluirCitaId === undefined) return [];

  const dias = diasDesde(ahora, fecha);
  if (dias < 0 || dias > MAX_DIAS_ANTICIPACION) return [];

  const minutosAhora = ahora.getHours() * 60 + ahora.getMinutes();
  const ocupadas = citas.filter(
    c =>
      c.servicioId === servicio.id &&
      c.fecha === fecha &&
      c.estado !== 'Cancelada' &&
      c.id !== excluirCitaId,
  );

  const dia = isoDia(fecha);
  const inicios = new Set<number>();
  for (const v of servicio.ventanas) {
    if (v.dia !== dia) continue;
    const finVentana = aMinutos(v.fin);
    for (let ini = aMinutos(v.inicio); ini + servicio.duracionMin <= finVentana; ini += PASO_MIN) {
      const fin = ini + servicio.duracionMin;
      if (dias === 0 && ini < minutosAhora) continue;
      const choca = ocupadas.some(c => ini < aMinutos(c.fin) && fin > aMinutos(c.inicio));
      if (!choca) inicios.add(ini);
    }
  }
  return [...inicios].sort((a, b) => a - b).map(aHHmm);
}
