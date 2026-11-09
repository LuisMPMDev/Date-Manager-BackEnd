import { Inicio } from '../../core/inicio';
import { moneda } from '../../shared/formato';
import { construirKpis, serieCitasPorDia, serieOcupacion } from './panel-presentacion';
import { INICIO, INICIO_VACIO } from '../../core/inicio.pruebas';

const kpiDe = (inicio: Inicio, id: string) => construirKpis(inicio).find(k => k.id === id)!;

describe('construirKpis', () => {
  it('arma los seis indicadores en orden', () => {
    expect(construirKpis(INICIO).map(k => k.id)).toEqual(['citas-hoy', 'proxima', 'ocupacion', 'nuevas', 'canceladas', 'ingresos']);
  });

  it('citas de hoy: concuerda el singular y el plural de «atendida»', () => {
    expect(kpiDe({ ...INICIO, citasHoy: { total: 2, atendidas: 1 } }, 'citas-hoy').detalle).toBe('1 ya atendida');
    expect(kpiDe({ ...INICIO, citasHoy: { total: 2, atendidas: 2 } }, 'citas-hoy').detalle).toBe('2 ya atendidas');
    expect(kpiDe({ ...INICIO, citasHoy: { total: 0, atendidas: 0 } }, 'citas-hoy').detalle).toBe('0 ya atendidas');
  });

  it('próxima cita: «en N min» si es de hoy, la fecha si es de otro día, guion si no hay', () => {
    expect(kpiDe(INICIO, 'proxima')).toMatchObject({ valor: '09:30', detalle: 'en 90 min · Corte de cabello' });
    expect(kpiDe({ ...INICIO, proxima: INICIO.proximas[1] }, 'proxima'))
      .toMatchObject({ valor: '11:00', detalle: 'Martes 10 de noviembre · Barba' });
    expect(kpiDe({ ...INICIO, proxima: undefined }, 'proxima')).toMatchObject({ valor: '—', detalle: 'Sin citas próximas', tono: 'neutro' });
  });

  it('la ocupación se compara con el mismo día de la semana pasada, según la fecha del tablero', () => {
    expect(kpiDe(INICIO, 'ocupacion')).toMatchObject({ valor: '62%', detalle: '▲ 24% vs. lunes pasado', tono: 'ok' });
    expect(kpiDe({ ...INICIO, fecha: '2026-11-11' }, 'ocupacion').detalle).toBe('▲ 24% vs. miércoles pasado');
  });

  it('la variación se redondea al entero y lleva flecha según su signo', () => {
    const con = (variacion: number) => ({ ...INICIO, ingresos: { actual: 1, anterior: 1, variacion } });
    expect(kpiDe(con(0.126), 'ingresos').detalle).toBe('▲ 13% vs. 30 días antes');
    expect(kpiDe(con(-0.126), 'ingresos').detalle).toBe('▼ 13% vs. 30 días antes');
    expect(kpiDe(con(0.001), 'ingresos')).toMatchObject({ detalle: 'Sin cambios vs. 30 días antes', tono: 'neutro' });
  });

  it('el tono: subir ingresos es bueno, subir canceladas es malo', () => {
    expect(kpiDe(INICIO, 'ingresos').tono).toBe('ok');
    expect(kpiDe({ ...INICIO, ingresos: { actual: 1, anterior: 2, variacion: -0.5 } }, 'ingresos').tono).toBe('mal');
    expect(kpiDe({ ...INICIO, citasCanceladas: { actual: 6, anterior: 3, variacion: 1 } }, 'canceladas').tono).toBe('mal');
    expect(kpiDe(INICIO, 'canceladas').tono).toBe('ok');
  });

  it('una variación ausente se muestra como «—»', () => {
    const sinVariacion = construirKpis(INICIO_VACIO);
    expect(sinVariacion.filter(k => ['ocupacion', 'nuevas', 'canceladas', 'ingresos'].includes(k.id)).map(k => k.detalle))
      .toEqual(['—', '—', '—', '—']);
    expect(sinVariacion.every(k => k.tono === 'neutro')).toBe(true);
  });

  it('una variación nula (null en el JSON) también se muestra como «—»', () => {
    const conNulo = { ...INICIO, ingresos: { actual: 5, anterior: 0, variacion: null } } as unknown as Inicio;
    expect(kpiDe(conNulo, 'ingresos')).toMatchObject({ detalle: '—', tono: 'neutro' });
  });

  it('los ingresos llevan formato de moneda', () => {
    expect(kpiDe(INICIO, 'ingresos').valor).toBe(moneda(1250000));
  });
});

describe('serieCitasPorDia', () => {
  it('etiqueta cada día como «9 nov» y resume total y día más fuerte', () => {
    const serie = serieCitasPorDia([
      { fecha: '2026-11-07', total: 1 }, { fecha: '2026-11-08', total: 4 }, { fecha: '2026-11-09', total: 0 },
    ]);
    expect(serie.etiquetas).toEqual(['7 nov', '8 nov', '9 nov']);
    expect(serie.valores).toEqual([1, 4, 0]);
    expect(serie.resumen).toBe('Citas por día, últimos 30 días: 5 citas; el día con más citas fue 8 nov (4).');
  });

  it('singular con una sola cita y texto propio sin citas', () => {
    expect(serieCitasPorDia([{ fecha: '2026-01-02', total: 1 }]).resumen)
      .toBe('Citas por día, últimos 30 días: 1 cita; el día con más citas fue 2 ene (1).');
    expect(serieCitasPorDia([{ fecha: '2026-01-02', total: 0 }]).resumen).toBe('Citas por día, últimos 30 días: sin citas.');
    expect(serieCitasPorDia([]).resumen).toBe('Citas por día, últimos 30 días: sin citas.');
  });
});

describe('serieOcupacion', () => {
  it('pasa la fracción a porcentaje entero y la describe en texto', () => {
    const serie = serieOcupacion([
      { servicioId: 'a', nombre: 'Corte', ocupacion: 0.5 }, { servicioId: 'b', nombre: 'Barba', ocupacion: 0.256 },
    ]);
    expect(serie.etiquetas).toEqual(['Corte', 'Barba']);
    expect(serie.valores).toEqual([50, 26]);
    expect(serie.resumen).toBe('Ocupación por servicio esta semana: Corte 50%, Barba 26%.');
  });

  it('sin servicios lo dice', () => {
    expect(serieOcupacion([]).resumen).toBe('Ocupación por servicio esta semana: no hay servicios activos.');
  });
});
