import { Cita, Servicio } from '../../core/models';
import { moneda } from '../../shared/formato';
import { calcularKpis, Kpi, proximasCitas } from './panel-kpis';

const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00
const DIA_MS = 24 * 60 * 60 * 1000;

// Lunes de 08:00 a 12:00 (240 min) y 50 000 por cita.
const SERVICIO_A: Servicio = {
  id: 'a', nombre: 'Servicio A', duracionMin: 60, activo: true, valor: 50000,
  ventanas: [{ id: 'a-1', dia: 1, inicio: '08:00', fin: '12:00' }],
};
const SERVICIOS = [SERVICIO_A];

let contador = 0;
function cita(parcial: Partial<Cita> & { fecha: string; inicio: string }): Cita {
  contador++;
  return {
    id: `c${contador}`, servicioId: 'a', clienteId: 'cli', fin: '23:59', estado: 'Confirmada',
    creadaEn: new Date(AHORA.getTime() - 100 * DIA_MS).toISOString(), ...parcial,
  };
}
const haceDias = (n: number) => new Date(AHORA.getTime() - n * DIA_MS).toISOString();
const kpi = (kpis: Kpi[], id: string) => kpis.find(k => k.id === id)!;

describe('calcularKpis', () => {
  it('devuelve los seis indicadores con sus etiquetas, en orden', () => {
    const kpis = calcularKpis({ servicios: SERVICIOS, citas: [], ahora: AHORA });
    expect(kpis.map(k => k.etiqueta)).toEqual([
      'Citas hoy', 'Próxima cita', 'Ocupación de hoy', 'Citas nuevas (30 días)', 'Canceladas (30 días)', 'Ingresos estimados',
    ]);
  });

  describe('Citas hoy', () => {
    it('cuenta las citas de hoy que no están canceladas y cuántas ya se atendieron', () => {
      const citas = [
        cita({ fecha: '2026-11-09', inicio: '09:00' }),
        cita({ fecha: '2026-11-09', inicio: '10:00', estado: 'Atendida' }),
        cita({ fecha: '2026-11-09', inicio: '11:00', estado: 'Cancelada' }),
        cita({ fecha: '2026-11-10', inicio: '09:00' }),
      ];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'citas-hoy');
      expect(k.valor).toBe('2');
      expect(k.detalle).toBe('1 ya atendida');
    });

    it('usa el plural cuando no es exactamente una', () => {
      const citas = [
        cita({ fecha: '2026-11-09', inicio: '09:00', estado: 'Atendida' }),
        cita({ fecha: '2026-11-09', inicio: '10:00', estado: 'Atendida' }),
      ];
      expect(kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'citas-hoy').detalle).toBe('2 ya atendidas');
      expect(kpi(calcularKpis({ servicios: SERVICIOS, citas: [], ahora: AHORA }), 'citas-hoy').detalle).toBe('0 ya atendidas');
    });
  });

  describe('Próxima cita', () => {
    it('muestra la hora de la primera cita confirmada que aún no empieza y en cuántos minutos', () => {
      const citas = [
        cita({ fecha: '2026-11-09', inicio: '07:00' }), // ya empezó
        cita({ fecha: '2026-11-09', inicio: '08:30', estado: 'Atendida' }),
        cita({ fecha: '2026-11-09', inicio: '09:30' }),
        cita({ fecha: '2026-11-09', inicio: '10:00' }),
      ];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'proxima');
      expect(k.valor).toBe('09:30');
      expect(k.detalle).toBe('en 90 min · Servicio A');
    });

    it('una cita que empieza justo ahora cuenta como próxima', () => {
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas: [cita({ fecha: '2026-11-09', inicio: '08:00' })], ahora: AHORA }), 'proxima');
      expect(k.valor).toBe('08:00');
      expect(k.detalle).toBe('en 0 min · Servicio A');
    });

    it('si es de otro día muestra el día en lugar de los minutos', () => {
      const citas = [cita({ fecha: '2026-11-11', inicio: '14:00' }), cita({ fecha: '2026-11-10', inicio: '09:00' })];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'proxima');
      expect(k.valor).toBe('09:00');
      expect(k.detalle).toBe('Martes 10 de noviembre · Servicio A');
    });

    it('sin citas confirmadas por delante dice «Sin citas próximas»', () => {
      const citas = [cita({ fecha: '2026-11-10', inicio: '09:00', estado: 'Cancelada' }), cita({ fecha: '2026-11-08', inicio: '09:00' })];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'proxima');
      expect(k.valor).toBe('—');
      expect(k.detalle).toBe('Sin citas próximas');
    });
  });

  describe('Ocupación de hoy', () => {
    it('es el porcentaje de minutos ocupados y compara con el mismo día de la semana pasada', () => {
      const citas = [
        cita({ fecha: '2026-11-09', inicio: '09:00', fin: '10:00' }), // 60 de 240 = 25 %
        cita({ fecha: '2026-11-02', inicio: '09:00', fin: '09:30' }), // 30 de 240 = 12,5 %
      ];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'ocupacion');
      expect(k.valor).toBe('25%');
      expect(k.detalle).toBe('▲ 100% vs. lunes pasado');
      expect(k.tono).toBe('ok');
    });

    it('si baja, usa ▼ y tono malo', () => {
      const citas = [
        cita({ fecha: '2026-11-09', inicio: '09:00', fin: '10:00' }),
        cita({ fecha: '2026-11-02', inicio: '09:00', fin: '11:00' }),
      ];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'ocupacion');
      expect(k.detalle).toBe('▼ 50% vs. lunes pasado');
      expect(k.tono).toBe('mal');
    });

    it('sin ocupación la semana pasada dice «Sin dato previo»', () => {
      const citas = [cita({ fecha: '2026-11-09', inicio: '09:00', fin: '10:00' })];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'ocupacion');
      expect(k.detalle).toBe('Sin dato previo');
      expect(k.tono).toBe('neutro');
    });

    it('sin variación dice que no hubo cambios', () => {
      const citas = [
        cita({ fecha: '2026-11-09', inicio: '09:00', fin: '10:00' }),
        cita({ fecha: '2026-11-02', inicio: '09:00', fin: '10:00' }),
      ];
      expect(kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'ocupacion').detalle).toBe('Sin cambios vs. lunes pasado');
    });
  });

  describe('Citas nuevas (30 días)', () => {
    it('cuenta las creadas en los últimos 30 días y compara con los 30 anteriores', () => {
      const citas = [
        cita({ fecha: '2026-11-20', inicio: '09:00', creadaEn: haceDias(1) }),
        cita({ fecha: '2026-11-20', inicio: '10:00', creadaEn: haceDias(29) }),
        cita({ fecha: '2026-11-20', inicio: '11:00', creadaEn: haceDias(15), estado: 'Cancelada' }), // también fue una cita nueva
        cita({ fecha: '2026-11-20', inicio: '12:00', creadaEn: haceDias(31) }),
        cita({ fecha: '2026-11-20', inicio: '13:00', creadaEn: haceDias(59) }),
      ];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'nuevas');
      expect(k.valor).toBe('3');
      expect(k.detalle).toBe('▲ 50% vs. 30 días antes');
      expect(k.tono).toBe('ok');
    });

    it('si hay menos que antes, ▼ y tono malo; sin periodo previo, «Sin dato previo»', () => {
      const menos = [
        cita({ fecha: '2026-11-20', inicio: '09:00', creadaEn: haceDias(2) }),
        cita({ fecha: '2026-11-20', inicio: '10:00', creadaEn: haceDias(40) }),
        cita({ fecha: '2026-11-20', inicio: '11:00', creadaEn: haceDias(41) }),
      ];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas: menos, ahora: AHORA }), 'nuevas');
      expect(k.detalle).toBe('▼ 50% vs. 30 días antes');
      expect(k.tono).toBe('mal');

      const sin = [cita({ fecha: '2026-11-20', inicio: '09:00', creadaEn: haceDias(2) })];
      expect(kpi(calcularKpis({ servicios: SERVICIOS, citas: sin, ahora: AHORA }), 'nuevas').detalle).toBe('Sin dato previo');
    });
  });

  describe('Canceladas (30 días)', () => {
    it('cuenta las canceladas en los últimos 30 días; que haya más que antes es malo', () => {
      const citas = [
        cita({ fecha: '2026-11-20', inicio: '09:00', estado: 'Cancelada', canceladaEn: haceDias(1) }),
        cita({ fecha: '2026-11-20', inicio: '10:00', estado: 'Cancelada', canceladaEn: haceDias(10) }),
        cita({ fecha: '2026-11-20', inicio: '11:00', estado: 'Cancelada', canceladaEn: haceDias(45) }),
        cita({ fecha: '2026-11-20', inicio: '12:00', estado: 'Confirmada' }),
      ];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'canceladas');
      expect(k.valor).toBe('2');
      expect(k.detalle).toBe('▲ 100% vs. 30 días antes');
      expect(k.tono).toBe('mal');
    });

    it('que haya menos que antes es bueno', () => {
      const citas = [
        cita({ fecha: '2026-11-20', inicio: '09:00', estado: 'Cancelada', canceladaEn: haceDias(1) }),
        cita({ fecha: '2026-11-20', inicio: '10:00', estado: 'Cancelada', canceladaEn: haceDias(40) }),
        cita({ fecha: '2026-11-20', inicio: '11:00', estado: 'Cancelada', canceladaEn: haceDias(50) }),
      ];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'canceladas');
      expect(k.detalle).toBe('▼ 50% vs. 30 días antes');
      expect(k.tono).toBe('ok');
    });
  });

  describe('Ingresos estimados', () => {
    it('suma el valor de las citas no canceladas de los últimos 30 días hasta hoy y compara con los 30 anteriores', () => {
      const citas = [
        cita({ fecha: '2026-11-09', inicio: '09:00' }), // hoy: entra
        cita({ fecha: '2026-10-11', inicio: '09:00' }), // hoy - 29: entra
        cita({ fecha: '2026-10-10', inicio: '09:00' }), // hoy - 30: periodo previo
        cita({ fecha: '2026-09-11', inicio: '09:00' }), // hoy - 59: periodo previo
        cita({ fecha: '2026-09-10', inicio: '09:00' }), // fuera
        cita({ fecha: '2026-10-20', inicio: '09:00', estado: 'Cancelada' }), // no suma
        cita({ fecha: '2026-11-10', inicio: '09:00' }), // futura: no suma
      ];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'ingresos');
      expect(k.valor).toBe(moneda(100000));
      expect(k.detalle).toBe('Sin cambios vs. 30 días antes');
    });

    it('muestra la variación frente al periodo anterior', () => {
      const citas = [
        cita({ fecha: '2026-11-09', inicio: '09:00' }),
        cita({ fecha: '2026-11-08', inicio: '09:00' }),
        cita({ fecha: '2026-11-07', inicio: '09:00' }),
        cita({ fecha: '2026-10-01', inicio: '09:00' }),
        cita({ fecha: '2026-10-02', inicio: '09:00' }),
      ];
      const k = kpi(calcularKpis({ servicios: SERVICIOS, citas, ahora: AHORA }), 'ingresos');
      expect(k.valor).toBe(moneda(150000));
      expect(k.detalle).toBe('▲ 50% vs. 30 días antes');
      expect(k.tono).toBe('ok');
    });
  });
});

describe('proximasCitas', () => {
  it('devuelve las próximas citas confirmadas desde ahora, ordenadas, hasta el límite', () => {
    const citas = [
      cita({ fecha: '2026-11-12', inicio: '09:00' }),
      cita({ fecha: '2026-11-09', inicio: '07:30' }), // ya empezó
      cita({ fecha: '2026-11-09', inicio: '09:00', estado: 'Atendida' }),
      cita({ fecha: '2026-11-09', inicio: '10:00' }),
      cita({ fecha: '2026-11-10', inicio: '09:00', estado: 'Cancelada' }),
      cita({ fecha: '2026-11-09', inicio: '08:00' }),
      cita({ fecha: '2026-11-13', inicio: '09:00' }),
      cita({ fecha: '2026-11-14', inicio: '09:00' }),
      cita({ fecha: '2026-11-15', inicio: '09:00' }),
    ];
    const proximas = proximasCitas(citas, AHORA, 5);
    expect(proximas.map(c => `${c.fecha} ${c.inicio}`)).toEqual([
      '2026-11-09 08:00', '2026-11-09 10:00', '2026-11-12 09:00', '2026-11-13 09:00', '2026-11-14 09:00',
    ]);
  });
});
