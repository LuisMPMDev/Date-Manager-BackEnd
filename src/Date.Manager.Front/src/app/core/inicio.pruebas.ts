import { Inicio } from './inicio';
import { sumarDias } from '../shared/formato';

/** Respuestas fijas de GET /panel/inicio para las pruebas (sin TestBed, las puede importar cualquier spec). */

/** Lo que responde la API a las 08:00 del lunes 9 de noviembre de 2026: cifras fijas y fáciles de reconocer. */
export const INICIO: Inicio = {
  fecha: '2026-11-09',
  citasHoy: { total: 4, atendidas: 1 },
  proxima: {
    id: 'c-1', servicioId: 's-corte', servicioNombre: 'Corte de cabello', clienteId: 'k-1', clienteNombre: 'Laura',
    clienteApellido: 'Gómez', clienteTelefono: '300 111 2233', fecha: '2026-11-09', inicio: '09:30', fin: '10:00',
    esHoy: true, minutosHasta: 90,
  },
  ocupacionHoy: { actual: 0.62, anterior: 0.5, variacion: 0.24 },
  citasNuevas: { actual: 12, anterior: 16, variacion: -0.25 },
  citasCanceladas: { actual: 3, anterior: 5, variacion: -0.4 },
  ingresos: { actual: 1250000, anterior: 1000000, variacion: 0.25 },
  alertas: [
    { tipo: 'cita_nueva', titulo: 'Cita nueva', detalle: 'Corte de cabello, Lunes 9 de noviembre a las 09:30', ref: 'c-1' },
    { tipo: 'cita_cancelada', titulo: 'Cita cancelada', detalle: 'Barba, Martes 10 de noviembre a las 11:00', ref: 'c-2' },
    { tipo: 'servicio_sin_horario', titulo: 'Servicio sin horario', detalle: 'Tinte no tiene horarios de atención', ref: 's-tinte' },
  ],
  alertasExtra: 2,
  // 30 días hasta hoy; total = día del mes módulo 4 (0 a 3), así cada barra es predecible
  citasPorDia: Array.from({ length: 30 }, (_, i) => {
    const fecha = sumarDias('2026-11-09', i - 29);
    return { fecha, total: Number(fecha.slice(8)) % 4 };
  }),
  ocupacionSemana: [
    { servicioId: 's-corte', nombre: 'Corte de cabello', ocupacion: 0.5 },
    { servicioId: 's-barba', nombre: 'Barba', ocupacion: 0.256 },
  ],
  proximas: [
    {
      id: 'c-1', servicioId: 's-corte', servicioNombre: 'Corte de cabello', clienteId: 'k-1', clienteNombre: 'Laura',
      clienteApellido: 'Gómez', clienteTelefono: '300 111 2233', fecha: '2026-11-09', inicio: '09:30', fin: '10:00',
      esHoy: true, minutosHasta: 90,
    },
    {
      id: 'c-3', servicioId: 's-barba', servicioNombre: 'Barba', clienteId: 'k-2', clienteNombre: 'Pedro',
      clienteApellido: 'Ruiz', clienteTelefono: '300 444 5566', fecha: '2026-11-10', inicio: '11:00', fin: '11:30',
      esHoy: false,
    },
  ],
};

/** Un día sin nada: todo en cero, sin variaciones y con las listas vacías. */
export const INICIO_VACIO: Inicio = {
  fecha: '2026-11-09',
  citasHoy: { total: 0, atendidas: 0 },
  ocupacionHoy: { actual: 0, anterior: 0 },
  citasNuevas: { actual: 0, anterior: 0 },
  citasCanceladas: { actual: 0, anterior: 0 },
  ingresos: { actual: 0, anterior: 0 },
  alertas: [],
  alertasExtra: 0,
  citasPorDia: [],
  ocupacionSemana: [],
  proximas: [],
};
