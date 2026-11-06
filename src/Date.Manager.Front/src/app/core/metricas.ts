import { aMinutos, isoDia } from './horarios';
import { Cita, Servicio } from './models';

export type Alerta = {
  tipo: 'cita_nueva' | 'cita_cancelada' | 'servicio_sin_horario';
  titulo: string;
  detalle: string;
  ref: string;
};

const MS_24H = 24 * 60 * 60 * 1000;

function partes(fecha: string): [number, number, number] {
  const [y, m, d] = fecha.split('-').map(Number);
  return [y, m, d];
}

function aFecha(y: number, m: number, d: number): string {
  return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

/** Fechas 'YYYY-MM-DD' de `desde` a `hasta`, ambas incluidas. */
function fechasEntre(desde: string, hasta: string): string[] {
  const [y, m, d] = partes(desde);
  const fin = partes(hasta);
  const finMs = Date.UTC(fin[0], fin[1] - 1, fin[2]);
  const resultado: string[] = [];
  for (let t = Date.UTC(y, m - 1, d); t <= finMs; t += 24 * 60 * 60 * 1000) {
    const f = new Date(t);
    resultado.push(aFecha(f.getUTCFullYear(), f.getUTCMonth() + 1, f.getUTCDate()));
  }
  return resultado;
}

function minutosCita(c: Cita): number {
  return aMinutos(c.fin) - aMinutos(c.inicio);
}

function minutosDisponibles(servicio: Servicio, fechas: string[]): number {
  if (!servicio.activo) return 0;
  let total = 0;
  for (const fecha of fechas) {
    const dia = isoDia(fecha);
    for (const v of servicio.ventanas) {
      if (v.dia === dia) total += aMinutos(v.fin) - aMinutos(v.inicio);
    }
  }
  return total;
}

function ocupacionDe(servicios: Servicio[], citas: Cita[], fechas: string[]): number {
  const conjunto = new Set(fechas);
  let disponibles = 0;
  let ocupados = 0;
  for (const s of servicios) {
    const disp = minutosDisponibles(s, fechas);
    if (disp === 0) continue;
    disponibles += disp;
    for (const c of citas) {
      if (c.servicioId === s.id && c.estado !== 'Cancelada' && conjunto.has(c.fecha)) ocupados += minutosCita(c);
    }
  }
  return disponibles === 0 ? 0 : ocupados / disponibles;
}

/** Fracción 0..1 de minutos ocupados sobre minutos de ventanas de servicios activos ese día. */
export function ocupacion(servicios: Servicio[], citas: Cita[], fecha: string): number {
  return ocupacionDe(servicios, citas, [fecha]);
}

export function ocupacionServicio(servicio: Servicio, citas: Cita[], desde: string, hasta: string): number {
  return ocupacionDe([servicio], citas, fechasEntre(desde, hasta));
}

export function ingresosEstimados(servicios: Servicio[], citas: Cita[], desde: string, hasta: string): number {
  const valores = new Map(servicios.map(s => [s.id, s.valor]));
  return citas
    .filter(c => c.estado !== 'Cancelada' && c.fecha >= desde && c.fecha <= hasta)
    .reduce((suma, c) => suma + (valores.get(c.servicioId) ?? 0), 0);
}

export function citasPorDia(citas: Cita[], desde: string, hasta: string): { fecha: string; total: number }[] {
  return fechasEntre(desde, hasta).map(fecha => ({
    fecha,
    total: citas.filter(c => c.fecha === fecha && c.estado !== 'Cancelada').length,
  }));
}

export function alertas(servicios: Servicio[], citas: Cita[], ahora: Date): Alerta[] {
  const resultado: Alerta[] = [];
  const nombres = new Map(servicios.map(s => [s.id, s.nombre]));
  const reciente = (iso: string | undefined): boolean => {
    if (!iso) return false;
    const edad = ahora.getTime() - new Date(iso).getTime();
    return edad >= 0 && edad < MS_24H;
  };

  for (const c of citas) {
    const nombre = nombres.get(c.servicioId) ?? 'Servicio';
    const detalle = `${nombre}, ${c.fecha} a las ${c.inicio}`;
    if (c.estado === 'Cancelada') {
      if (reciente(c.canceladaEn)) resultado.push({ tipo: 'cita_cancelada', titulo: 'Cita cancelada', detalle, ref: c.id });
    } else if (reciente(c.creadaEn)) {
      resultado.push({ tipo: 'cita_nueva', titulo: 'Cita nueva', detalle, ref: c.id });
    }
  }

  for (const s of servicios) {
    if (s.activo && s.ventanas.length === 0) {
      resultado.push({
        tipo: 'servicio_sin_horario',
        titulo: 'Servicio sin horario',
        detalle: `${s.nombre} no tiene horarios de atención`,
        ref: s.id,
      });
    }
  }
  return resultado;
}

/** Variación relativa como fracción; null si el valor previo es 0. */
export function variacion(actual: number, previo: number): number | null {
  return previo === 0 ? null : (actual - previo) / previo;
}
