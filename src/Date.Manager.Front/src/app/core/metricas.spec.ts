import { Cita, Servicio } from './models';
import { alertas, citasPorDia, ingresosEstimados, ocupacion, ocupacionServicio, variacion } from './metricas';

const LUNES = '2026-11-09';
const AHORA = new Date(2026, 10, 9, 12, 0);

function servicio(parcial: Partial<Servicio> = {}): Servicio {
  return {
    id: 's1', nombre: 'Corte', duracionMin: 60, activo: true, valor: 10000,
    ventanas: [{ id: 'v1', dia: 1, inicio: '09:00', fin: '13:00' }], // 240 min
    ...parcial,
  };
}

function cita(parcial: Partial<Cita> = {}): Cita {
  return {
    id: 'c1', servicioId: 's1', clienteId: 'cl1', fecha: LUNES, inicio: '09:00', fin: '10:00',
    estado: 'Confirmada', creadaEn: '2026-11-01T10:00:00.000Z', ...parcial,
  };
}

describe('ocupacion', () => {
  it('60 min de cita sobre 240 min de ventana es 0.25', () => {
    expect(ocupacion([servicio()], [cita()], LUNES)).toBe(0.25);
  });

  it('una cita cancelada no cuenta', () => {
    expect(ocupacion([servicio()], [cita({ estado: 'Cancelada' })], LUNES)).toBe(0);
  });

  it('una cita atendida cuenta', () => {
    expect(ocupacion([servicio()], [cita({ estado: 'Atendida' })], LUNES)).toBe(0.25);
  });

  it('sin ventanas es 0 (no NaN)', () => {
    expect(ocupacion([servicio({ ventanas: [] })], [cita()], LUNES)).toBe(0);
  });

  it('ignora servicios inactivos', () => {
    expect(ocupacion([servicio({ activo: false })], [cita()], LUNES)).toBe(0);
  });

  it('un día sin ventanas ese día de la semana es 0', () => {
    expect(ocupacion([servicio()], [cita({ fecha: '2026-11-10' })], '2026-11-10')).toBe(0);
  });
});

describe('ocupacionServicio', () => {
  it('suma varios días del rango inclusive', () => {
    const citas = [
      cita({ id: 'a', fecha: '2026-11-09' }),
      cita({ id: 'b', fecha: '2026-11-16', inicio: '09:00', fin: '11:00' }),
    ];
    // dos lunes: 480 min disponibles, 60 + 120 ocupados
    expect(ocupacionServicio(servicio(), citas, '2026-11-09', '2026-11-16')).toBe(180 / 480);
  });

  it('solo cuenta citas de ese servicio', () => {
    expect(ocupacionServicio(servicio(), [cita({ servicioId: 'otro' })], LUNES, LUNES)).toBe(0);
  });

  it('es 0 si el servicio está inactivo', () => {
    expect(ocupacionServicio(servicio({ activo: false }), [cita()], LUNES, LUNES)).toBe(0);
  });
});

describe('ingresosEstimados', () => {
  it('suma los valores de las citas no canceladas del rango', () => {
    const citas = [cita({ id: 'a' }), cita({ id: 'b', fecha: '2026-11-10' }), cita({ id: 'c', estado: 'Cancelada' })];
    expect(ingresosEstimados([servicio()], citas, '2026-11-09', '2026-11-10')).toBe(20000);
  });

  it('incluye ambos extremos y excluye lo de fuera', () => {
    const citas = [
      cita({ id: 'a', fecha: '2026-11-08' }),
      cita({ id: 'b', fecha: '2026-11-09' }),
      cita({ id: 'c', fecha: '2026-11-10' }),
      cita({ id: 'd', fecha: '2026-11-11' }),
    ];
    expect(ingresosEstimados([servicio()], citas, '2026-11-09', '2026-11-10')).toBe(20000);
  });
});

describe('citasPorDia', () => {
  it('devuelve ceros para días vacíos y cuenta las no canceladas', () => {
    const citas = [cita({ id: 'a' }), cita({ id: 'b' }), cita({ id: 'c', estado: 'Cancelada' }), cita({ id: 'd', fecha: '2026-11-11' })];
    expect(citasPorDia(citas, '2026-11-09', '2026-11-11')).toEqual([
      { fecha: '2026-11-09', total: 2 },
      { fecha: '2026-11-10', total: 0 },
      { fecha: '2026-11-11', total: 1 },
    ]);
  });

  it('cruza el fin de mes', () => {
    expect(citasPorDia([], '2026-11-29', '2026-12-01').map(d => d.fecha)).toEqual(['2026-11-29', '2026-11-30', '2026-12-01']);
  });
});

describe('alertas', () => {
  it('un servicio activo sin ventanas genera una alerta', () => {
    const r = alertas([servicio({ id: 'x', ventanas: [] })], [], AHORA);
    expect(r.length).toBe(1);
    expect(r[0].tipo).toBe('servicio_sin_horario');
    expect(r[0].ref).toBe('x');
  });

  it('un servicio inactivo sin ventanas no genera alerta', () => {
    expect(alertas([servicio({ activo: false, ventanas: [] })], [], AHORA)).toEqual([]);
  });

  it('una cita creada hace 2 h es cita_nueva', () => {
    const creadaEn = new Date(AHORA.getTime() - 2 * 3600 * 1000).toISOString();
    const r = alertas([servicio()], [cita({ id: 'n', creadaEn })], AHORA);
    expect(r.length).toBe(1);
    expect(r[0].tipo).toBe('cita_nueva');
    expect(r[0].ref).toBe('n');
    expect(r[0].titulo).toBe('Cita nueva');
  });

  it('el detalle escribe la fecha en español, no en ISO', () => {
    const creadaEn = new Date(AHORA.getTime() - 2 * 3600 * 1000).toISOString();
    const r = alertas([servicio()], [cita({ fecha: '2026-11-08', inicio: '09:00', creadaEn })], AHORA);
    expect(r[0].detalle).toBe('Corte, Domingo 8 de noviembre a las 09:00');
  });

  it('una cita creada hace 30 h no genera alerta', () => {
    const creadaEn = new Date(AHORA.getTime() - 30 * 3600 * 1000).toISOString();
    expect(alertas([servicio()], [cita({ creadaEn })], AHORA)).toEqual([]);
  });

  it('una cita cancelada hace 3 h es cita_cancelada', () => {
    const canceladaEn = new Date(AHORA.getTime() - 3 * 3600 * 1000).toISOString();
    const r = alertas([servicio()], [cita({ id: 'k', estado: 'Cancelada', canceladaEn })], AHORA);
    expect(r.length).toBe(1);
    expect(r[0].tipo).toBe('cita_cancelada');
    expect(r[0].ref).toBe('k');
  });

  it('una cita cancelada hace 30 h no genera alerta', () => {
    const canceladaEn = new Date(AHORA.getTime() - 30 * 3600 * 1000).toISOString();
    expect(alertas([servicio()], [cita({ estado: 'Cancelada', canceladaEn })], AHORA)).toEqual([]);
  });
});

describe('variacion', () => {
  it('es null si el previo es 0', () => {
    expect(variacion(5, 0)).toBeNull();
  });

  it('12 contra 10 es 0.2', () => {
    expect(variacion(12, 10)!).toBeCloseTo(0.2, 10);
  });

  it('una baja es negativa', () => {
    expect(variacion(5, 10)!).toBeCloseTo(-0.5, 10);
  });
});
