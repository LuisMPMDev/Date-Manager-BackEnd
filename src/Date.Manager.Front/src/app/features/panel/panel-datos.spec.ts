import { Alerta } from '../../core/metricas';
import { Cita, Servicio } from '../../core/models';
import { limitarAlertas, MAX_ALERTAS, ocupacionSemana, serieCitasPorDia } from './panel-datos';

let contador = 0;
function cita(parcial: Partial<Cita> & { fecha: string; servicioId: string }): Cita {
  contador++;
  return {
    id: `c${contador}`, clienteId: 'cli', inicio: '09:00', fin: '10:00', estado: 'Confirmada',
    creadaEn: '2026-01-01T00:00:00.000Z', ...parcial,
  };
}

function servicio(id: string, nombre: string, ventanas: Servicio['ventanas'], activo = true): Servicio {
  return { id, nombre, duracionMin: 60, activo, valor: 1000, ventanas };
}

describe('serieCitasPorDia', () => {
  it('cubre los 30 días hasta hoy, con etiquetas cortas y sin contar canceladas', () => {
    const citas = [
      cita({ servicioId: 's', fecha: '2026-11-09' }),
      cita({ servicioId: 's', fecha: '2026-11-09' }),
      cita({ servicioId: 's', fecha: '2026-11-09', estado: 'Cancelada' }),
      cita({ servicioId: 's', fecha: '2026-10-11' }),
      cita({ servicioId: 's', fecha: '2026-10-10' }), // fuera
      cita({ servicioId: 's', fecha: '2026-11-10' }), // futura: fuera
    ];
    const serie = serieCitasPorDia(citas, '2026-11-09');
    expect(serie.etiquetas).toHaveLength(30);
    expect(serie.valores).toHaveLength(30);
    expect(serie.etiquetas[0]).toBe('11 oct');
    expect(serie.etiquetas[29]).toBe('9 nov');
    expect(serie.valores[0]).toBe(1);
    expect(serie.valores[29]).toBe(2);
    expect(serie.valores.reduce((a, b) => a + b, 0)).toBe(3);
  });

  it('el resumen cuenta el total y el día con más citas', () => {
    const citas = [
      cita({ servicioId: 's', fecha: '2026-11-05' }),
      cita({ servicioId: 's', fecha: '2026-11-05' }),
      cita({ servicioId: 's', fecha: '2026-11-06' }),
    ];
    const { resumen } = serieCitasPorDia(citas, '2026-11-09');
    expect(resumen).toContain('Citas por día, últimos 30 días');
    expect(resumen).toContain('3 citas');
    expect(resumen).toContain('5 nov (2)');
  });

  it('sin citas, el resumen lo dice', () => {
    expect(serieCitasPorDia([], '2026-11-09').resumen).toContain('sin citas');
  });
});

describe('ocupacionSemana', () => {
  // Lunes a viernes de 08:00 a 12:00 (240 min por día).
  const ventanas = [1, 2, 3, 4, 5].map(dia => ({ id: `v${dia}`, dia: dia as 1, inicio: '08:00', fin: '12:00' }));
  const a = servicio('a', 'Servicio A', ventanas);
  const b = servicio('b', 'Servicio B', ventanas);
  const inactivo = servicio('c', 'Servicio C', ventanas, false);

  it('calcula el porcentaje por servicio activo de lunes a domingo de la semana de hoy', () => {
    const citas = [
      // 120 min de 1200 = 10 %
      cita({ servicioId: 'a', fecha: '2026-11-09', inicio: '08:00', fin: '09:00' }),
      cita({ servicioId: 'a', fecha: '2026-11-13', inicio: '08:00', fin: '09:00' }),
      // fuera de la semana: no cuenta
      cita({ servicioId: 'a', fecha: '2026-11-16', inicio: '08:00', fin: '12:00' }),
      cita({ servicioId: 'a', fecha: '2026-11-06', inicio: '08:00', fin: '12:00' }),
      // cancelada: no cuenta
      cita({ servicioId: 'a', fecha: '2026-11-10', inicio: '08:00', fin: '12:00', estado: 'Cancelada' }),
    ];
    const semana = ocupacionSemana([a, b, inactivo], citas, '2026-11-11'); // miércoles
    expect(semana.etiquetas).toEqual(['Servicio A', 'Servicio B']);
    expect(semana.valores).toEqual([10, 0]);
    expect(semana.resumen).toBe('Ocupación por servicio esta semana: Servicio A 10%, Servicio B 0%.');
  });

  it('un domingo sigue contando la semana que empezó el lunes anterior', () => {
    const citas = [cita({ servicioId: 'a', fecha: '2026-11-09', inicio: '08:00', fin: '12:00' })]; // 240 de 1200 = 20 %
    expect(ocupacionSemana([a], citas, '2026-11-15').valores).toEqual([20]);
    expect(ocupacionSemana([a], citas, '2026-11-16').valores).toEqual([0]);
  });

  it('sin servicios activos el resumen lo dice', () => {
    const semana = ocupacionSemana([inactivo], [], '2026-11-09');
    expect(semana.etiquetas).toEqual([]);
    expect(semana.resumen).toBe('Ocupación por servicio esta semana: no hay servicios activos.');
  });
});

describe('limitarAlertas', () => {
  const alerta = (n: number): Alerta => ({ tipo: 'cita_nueva', titulo: 'Cita nueva', detalle: `d${n}`, ref: `r${n}` });

  it('muestra hasta 6, en el orden recibido, e indica cuántas quedan fuera', () => {
    expect(MAX_ALERTAS).toBe(6);
    const todas = Array.from({ length: 9 }, (_, i) => alerta(i));
    const { visibles, extra } = limitarAlertas(todas);
    expect(visibles.map(x => x.ref)).toEqual(['r0', 'r1', 'r2', 'r3', 'r4', 'r5']);
    expect(extra).toBe(3);
  });

  it('con 6 o menos no hay excedente', () => {
    expect(limitarAlertas([alerta(1)])).toEqual({ visibles: [alerta(1)], extra: 0 });
    expect(limitarAlertas([])).toEqual({ visibles: [], extra: 0 });
  });
});
