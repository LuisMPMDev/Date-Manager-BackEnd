import { Cita, Cliente, EstadoCita, Servicio } from '../../core/models';
import { sumarDias } from '../../shared/formato';

export const DIAS_ATRAS = 7;
export const ESTADOS: EstadoCita[] = ['Confirmada', 'Atendida', 'Cancelada'];

/** Cadenas vacías = «Todos» / sin fecha. */
export interface FiltrosCitas {
  fecha: string;
  servicioId: string;
  estado: string;
  anteriores: boolean;
}

export const SIN_FILTROS: FiltrosCitas = { fecha: '', servicioId: '', estado: '', anteriores: false };

export interface FilaCita { cita: Cita; servicio: Servicio | undefined; cliente: Cliente | undefined }

/**
 * - Con una fecha elegida solo se ven las citas de ese día (aunque sea anterior).
 * - Sin fecha, se ven las de los últimos 7 días en adelante, o todas con «Mostrar anteriores».
 * Orden: fecha y hora ascendentes.
 */
export function filtrarCitas(citas: Cita[], f: FiltrosCitas, hoy: string): Cita[] {
  const desde = sumarDias(hoy, -DIAS_ATRAS);
  return citas
    .filter(c => {
      if (f.fecha) return c.fecha === f.fecha;
      return f.anteriores || c.fecha >= desde;
    })
    .filter(c => !f.servicioId || c.servicioId === f.servicioId)
    .filter(c => !f.estado || c.estado === f.estado)
    .sort((a, b) => a.fecha.localeCompare(b.fecha) || a.inicio.localeCompare(b.inicio));
}

export function nombreCompleto(c: Cliente | undefined): string {
  return c ? `${c.nombre} ${c.apellido}` : 'Cliente';
}
