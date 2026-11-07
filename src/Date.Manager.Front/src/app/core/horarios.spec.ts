import { Cita, DiaSemana, Servicio } from './models';
import { aHHmm, aMinutos, horariosLibres, isoDia, MAX_DIAS_ANTICIPACION, PASO_MIN, sumarMinutos } from './horarios';

const AHORA = new Date(2026, 10, 8, 10, 0); // domingo 8 nov 2026
const LUNES = '2026-11-09';

function servicio(parcial: Partial<Servicio> = {}): Servicio {
  return {
    id: 's1',
    nombre: 'Corte',
    duracionMin: 60,
    activo: true,
    valor: 10000,
    ventanas: [{ id: 'v1', dia: 1, inicio: '09:00', fin: '12:00' }],
    ...parcial,
  };
}

function cita(parcial: Partial<Cita> = {}): Cita {
  return {
    id: 'c1',
    servicioId: 's1',
    clienteId: 'cl1',
    fecha: LUNES,
    inicio: '10:00',
    fin: '11:00',
    estado: 'Confirmada',
    creadaEn: '2026-11-01T10:00:00.000Z',
    ...parcial,
  };
}

function todosLosDias(): Servicio['ventanas'] {
  return ([1, 2, 3, 4, 5, 6, 7] as DiaSemana[]).map(dia => ({ id: `v${dia}`, dia, inicio: '09:00', fin: '12:00' }));
}

describe('horarios', () => {
  it('expone las constantes', () => {
    expect(PASO_MIN).toBe(30);
    expect(MAX_DIAS_ANTICIPACION).toBe(30);
  });

  it('convierte entre HH:mm y minutos', () => {
    expect(aMinutos('09:30')).toBe(570);
    expect(aHHmm(570)).toBe('09:30');
    expect(sumarMinutos('09:30', 45)).toBe('10:15');
  });

  it('ofrece inicios cada 30 min mientras la cita quepa', () => {
    const r = horariosLibres({ servicio: servicio(), fecha: LUNES, citas: [], ahora: AHORA });
    expect(r).toEqual(['09:00', '09:30', '10:00', '10:30', '11:00']);
  });

  it('una cita contigua no bloquea', () => {
    const r = horariosLibres({ servicio: servicio(), fecha: LUNES, citas: [cita()], ahora: AHORA });
    expect(r).toEqual(['09:00', '11:00']);
  });

  it('ignora citas canceladas y de otros servicios', () => {
    const citas = [cita({ estado: 'Cancelada' }), cita({ id: 'c2', servicioId: 's2' })];
    const r = horariosLibres({ servicio: servicio(), fecha: LUNES, citas, ahora: AHORA });
    expect(r).toEqual(['09:00', '09:30', '10:00', '10:30', '11:00']);
  });

  it('ventana más corta que la duración no ofrece nada', () => {
    const s = servicio({ duracionMin: 90, ventanas: [{ id: 'v1', dia: 1, inicio: '09:00', fin: '10:00' }] });
    expect(horariosLibres({ servicio: s, fecha: LUNES, citas: [], ahora: AHORA })).toEqual([]);
  });

  it('usa ISO para el domingo', () => {
    expect(isoDia('2026-11-08')).toBe(7);
    expect(isoDia('2026-11-09')).toBe(1);
    const s = servicio({ ventanas: [{ id: 'v7', dia: 7, inicio: '09:00', fin: '12:00' }] });
    const ahoraAntes = new Date(2026, 10, 1, 10, 0);
    expect(horariosLibres({ servicio: s, fecha: '2026-11-08', citas: [], ahora: ahoraAntes }).length).toBeGreaterThan(0);
    expect(horariosLibres({ servicio: s, fecha: LUNES, citas: [], ahora: ahoraAntes })).toEqual([]);
  });

  it('hoy no ofrece horas pasadas', () => {
    const ahora = new Date(2026, 10, 9, 10, 15); // lunes 10:15
    const r = horariosLibres({ servicio: servicio(), fecha: LUNES, citas: [], ahora });
    for (const h of ['09:00', '09:30', '10:00']) expect(r).not.toContain(h);
    expect(r).toContain('10:30');
  });

  it('límite de anticipación', () => {
    const s = servicio({ ventanas: todosLosDias() });
    expect(horariosLibres({ servicio: s, fecha: '2026-12-08', citas: [], ahora: AHORA }).length).toBeGreaterThan(0);
    expect(horariosLibres({ servicio: s, fecha: '2026-12-09', citas: [], ahora: AHORA })).toEqual([]);
    expect(horariosLibres({ servicio: s, fecha: '2026-11-07', citas: [], ahora: AHORA })).toEqual([]);
  });

  it('servicio inactivo', () => {
    const r = horariosLibres({ servicio: servicio({ activo: false }), fecha: LUNES, citas: [], ahora: AHORA });
    expect(r).toEqual([]);
  });

  it('servicio inactivo: al mover una cita (excluirCitaId) sí calcula los horarios libres', () => {
    const inactivo = servicio({ activo: false });
    const citas = [cita({ id: 'c1', inicio: '10:00', fin: '11:00' })];
    const r = horariosLibres({ servicio: inactivo, fecha: LUNES, citas, ahora: AHORA, excluirCitaId: 'c1' });
    expect(r).toEqual(['09:00', '09:30', '10:00', '10:30', '11:00']);
    expect(horariosLibres({ servicio: inactivo, fecha: LUNES, citas, ahora: AHORA })).toEqual([]);
  });

  it('excluirCitaId libera el horario propio', () => {
    const citas = [cita({ id: 'c1', inicio: '10:00', fin: '11:00' })];
    expect(horariosLibres({ servicio: servicio(), fecha: LUNES, citas, ahora: AHORA })).not.toContain('10:00');
    expect(horariosLibres({ servicio: servicio(), fecha: LUNES, citas, ahora: AHORA, excluirCitaId: 'c1' })).toContain('10:00');
  });

  it('ventanas solapadas no repiten horas', () => {
    const s = servicio({
      ventanas: [
        { id: 'v1', dia: 1, inicio: '09:00', fin: '12:00' },
        { id: 'v2', dia: 1, inicio: '10:00', fin: '13:00' },
      ],
    });
    const r = horariosLibres({ servicio: s, fecha: LUNES, citas: [], ahora: AHORA });
    expect(new Set(r).size).toBe(r.length);
    expect(r).toEqual([...r].sort());
  });
});
