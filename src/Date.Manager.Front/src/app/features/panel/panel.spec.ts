import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Inicio } from '../../core/inicio';
import { INICIO, INICIO_VACIO } from '../../core/inicio.pruebas';
import { SesionService } from '../../core/sesion';
import { rolDe } from '../../core/sesion.pruebas';
import { moneda } from '../../shared/formato';
import { sembrar } from '../../testing/seed';
import {
  AHORA, api, botonesAlerta, celdas, configurar, crear, esperar, filasProximas, GraficaDoble, kpi,
  repo, textosAlerta,
} from './panel.pruebas';

const SIN_ALERTAS = 'Todo en orden: no hay alertas.';

describe('PanelComponent', () => {
  let graficas: GraficaDoble[];
  beforeEach(async () => {
    graficas = await configurar();
  });

  it('saluda a quien inició sesión', async () => {
    const { el } = await crear();
    expect(el.querySelector('h2')!.textContent?.trim()).toBe('Hola, Marta. Así va el día');
  });

  it('pide el tablero sin fecha: el servidor decide cuál es hoy', async () => {
    await crear();
    expect(api().llamadas).toEqual([undefined]);
  });

  it('muestra seis indicadores con sus etiquetas', async () => {
    const { el } = await crear();
    const etiquetas = Array.from(el.querySelectorAll('.kpi .kpi-etiqueta')).map(e => e.textContent?.trim());
    expect(etiquetas).toEqual([
      'Citas hoy', 'Próxima cita', 'Ocupación de hoy', 'Citas nuevas (30 días)', 'Canceladas (30 días)', 'Ingresos estimados',
    ]);
  });

  describe('indicadores', () => {
    it('citas de hoy y cuántas ya se atendieron', async () => {
      const { el } = await crear();
      expect(kpi(el, 'Citas hoy')).toEqual({ valor: '4', detalle: '1 ya atendida' });
    });

    it('próxima cita: hora, minutos que faltan y servicio', async () => {
      const { el } = await crear();
      expect(kpi(el, 'Próxima cita')).toEqual({ valor: '09:30', detalle: 'en 90 min · Corte de cabello' });
    });

    it('próxima cita de otro día: muestra la fecha en lugar de los minutos', async () => {
      const proxima = INICIO.proximas[1];
      const { el } = await crear({ ...INICIO, proxima });
      expect(kpi(el, 'Próxima cita')).toEqual({ valor: '11:00', detalle: 'Martes 10 de noviembre · Barba' });
    });

    it('ocupación en porcentaje y su variación contra el mismo día de la semana pasada', async () => {
      const { el } = await crear();
      expect(kpi(el, 'Ocupación de hoy')).toEqual({ valor: '62%', detalle: '▲ 24% vs. lunes pasado' });
    });

    it('citas nuevas y canceladas con su variación contra los 30 días anteriores', async () => {
      const { el } = await crear();
      expect(kpi(el, 'Citas nuevas (30 días)')).toEqual({ valor: '12', detalle: '▼ 25% vs. 30 días antes' });
      expect(kpi(el, 'Canceladas (30 días)')).toEqual({ valor: '3', detalle: '▼ 40% vs. 30 días antes' });
    });

    it('ingresos con formato de moneda y su variación', async () => {
      const { el } = await crear();
      expect(kpi(el, 'Ingresos estimados')).toEqual({ valor: moneda(1250000), detalle: '▲ 25% vs. 30 días antes' });
      expect(kpi(el, 'Ingresos estimados').valor).toMatch(/1\.250\.000/);
    });

    it('el tono depende de si subir es bueno: menos canceladas es bueno, menos citas nuevas es malo', async () => {
      const { el } = await crear();
      const detalle = (etiqueta: string) =>
        Array.from(el.querySelectorAll('.kpi')).find(k => k.querySelector('.kpi-etiqueta')?.textContent?.trim() === etiqueta)!
          .querySelector('.kpi-detalle')!;
      expect(detalle('Ocupación de hoy').classList.contains('ok')).toBe(true);
      expect(detalle('Citas nuevas (30 días)').classList.contains('mal')).toBe(true);
      expect(detalle('Canceladas (30 días)').classList.contains('ok')).toBe(true);
      expect(detalle('Citas hoy').classList.contains('ok') || detalle('Citas hoy').classList.contains('mal')).toBe(false);
    });

    it('una variación ausente se muestra como «—» y no rompe', async () => {
      const sinVariacion: Inicio = {
        ...INICIO,
        ocupacionHoy: { actual: 0.62, anterior: 0 },
        citasNuevas: { actual: 12, anterior: 0 },
        citasCanceladas: { actual: 3, anterior: 0 },
        ingresos: { actual: 1250000, anterior: 0 },
      };
      const { el } = await crear(sinVariacion);
      expect(kpi(el, 'Ocupación de hoy')).toEqual({ valor: '62%', detalle: '—' });
      expect(kpi(el, 'Citas nuevas (30 días)')).toEqual({ valor: '12', detalle: '—' });
      expect(kpi(el, 'Canceladas (30 días)')).toEqual({ valor: '3', detalle: '—' });
      expect(kpi(el, 'Ingresos estimados').detalle).toBe('—');
      expect(el.querySelector('[role="alert"]')).toBeNull();
    });

    it('una variación de 0 % dice «Sin cambios»', async () => {
      const { el } = await crear({ ...INICIO, citasNuevas: { actual: 12, anterior: 12, variacion: 0 } });
      expect(kpi(el, 'Citas nuevas (30 días)')).toEqual({ valor: '12', detalle: 'Sin cambios vs. 30 días antes' });
    });
  });

  describe('día sin citas', () => {
    it('renderiza todo en cero y con las listas vacías, sin error', async () => {
      const { el } = await crear(INICIO_VACIO);
      expect(el.querySelector('[role="alert"]')).toBeNull();
      expect(kpi(el, 'Citas hoy')).toEqual({ valor: '0', detalle: '0 ya atendidas' });
      expect(kpi(el, 'Próxima cita')).toEqual({ valor: '—', detalle: 'Sin citas próximas' });
      expect(kpi(el, 'Ocupación de hoy')).toEqual({ valor: '0%', detalle: '—' });
      expect(kpi(el, 'Ingresos estimados').valor).toBe(moneda(0));
      expect(el.querySelector('.alertas')!.textContent).toContain(SIN_ALERTAS);
      expect(filasProximas(el)).toHaveLength(0);
      expect(el.querySelector('.proximas')!.textContent).toContain('No hay citas próximas.');
      expect(el.textContent).toContain('Citas por día, últimos 30 días: sin citas.');
      expect(el.textContent).toContain('no hay servicios activos');
    });
  });

  describe('alertas', () => {
    it('lista las alertas con su título y detalle', async () => {
      const { el } = await crear();
      expect(botonesAlerta(el)).toHaveLength(3);
      const textos = textosAlerta(el);
      INICIO.alertas.forEach((a, i) => {
        expect(textos[i]).toContain(a.titulo);
        expect(textos[i]).toContain(a.detalle);
      });
      expect(el.textContent).not.toContain(SIN_ALERTAS);
    });

    it('avisa de cuántas más hay cuando la API dejó alertas fuera', async () => {
      const { el } = await crear();
      expect(el.querySelector('.alertas .mas')!.textContent?.trim()).toBe('y 2 más.');
    });

    it('sin alertas extra no muestra el aviso de «más»', async () => {
      const { el } = await crear({ ...INICIO, alertasExtra: 0 });
      expect(el.querySelector('.alertas .mas')).toBeNull();
    });

    it('cuando no hay alertas muestra «Todo en orden: no hay alertas.»', async () => {
      const { el } = await crear({ ...INICIO, alertas: [], alertasExtra: 0 });
      expect(botonesAlerta(el)).toHaveLength(0);
      expect(el.querySelector('.alertas')!.textContent).toContain(SIN_ALERTAS);
    });

    it('al hacer clic, las de citas llevan a Citas y la de servicio a Servicios', async () => {
      const { el } = await crear();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      botonesAlerta(el).find(b => b.textContent!.includes('Cita nueva'))!.click();
      expect(navegar).toHaveBeenLastCalledWith('/citas');
      botonesAlerta(el).find(b => b.textContent!.includes('Cita cancelada'))!.click();
      expect(navegar).toHaveBeenLastCalledWith('/citas');
      botonesAlerta(el).find(b => b.textContent!.includes('Servicio sin horario'))!.click();
      expect(navegar).toHaveBeenLastCalledWith('/servicios');
    });
  });

  describe('próximas citas', () => {
    it('lista las citas con hora, cliente, servicio y teléfono, sin columna de estado', async () => {
      const { el } = await crear();
      const encabezados = Array.from(el.querySelectorAll('.proximas thead th')).map(t => t.textContent?.trim());
      expect(encabezados).toEqual(['Hora', 'Cliente', 'Servicio', 'Teléfono', 'Acciones']);
      expect(filasProximas(el).map(f => f.dataset['cita'])).toEqual(['c-1', 'c-3']);

      const [hora, cliente, servicio, telefono] = celdas(filasProximas(el)[0]);
      expect(hora).toBe('09:30');
      expect(cliente).toBe('Laura Gómez');
      expect(servicio).toBe('Corte de cabello');
      expect(telefono).toBe('300 111 2233');
      expect(el.querySelector('.proximas .chip')).toBeNull();
    });

    it('muestra la fecha junto a la hora cuando la cita no es de hoy', async () => {
      const { el } = await crear();
      const [hoy, otroDia] = filasProximas(el);
      expect(otroDia.querySelector('strong')!.textContent?.trim()).toBe('11:00');
      expect(otroDia.querySelector('small.fecha')!.textContent?.trim()).toBe('Martes 10 de noviembre');
      expect(hoy.querySelector('strong')!.textContent?.trim()).toBe('09:30');
      expect(hoy.querySelector('small.fecha')).toBeNull();
    });

    it('«Abrir» lleva a Citas', async () => {
      const { el } = await crear();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      const abrir = filasProximas(el)[0].querySelector<HTMLButtonElement>('button')!;
      expect(abrir.textContent?.trim()).toBe('Abrir');
      expect(abrir.getAttribute('aria-label')).toBe('Abrir cita de Laura Gómez, Lunes 9 de noviembre 09:30');
      abrir.click();
      expect(navegar).toHaveBeenCalledWith('/citas');
    });

    it('sin el permiso «ver citas» no ofrece «Abrir», pero sigue listando las citas', async () => {
      const { fixture, el } = await crear();
      expect(filasProximas(el)[0].querySelector('button')).not.toBeNull();

      const datos = sembrar(AHORA);
      const marta = datos.usuarios.find(u => u.usuario === 'marta')!;
      TestBed.inject(SesionService).iniciar(marta, { ...rolDe(marta, datos.roles), permisos: ['ver_panel'] });
      await esperar(fixture);

      expect(filasProximas(el)).toHaveLength(2);
      expect(el.querySelectorAll('.proximas button')).toHaveLength(0);
    });

    it('sin citas por delante dice «No hay citas próximas.»', async () => {
      const { el } = await crear({ ...INICIO, proxima: undefined, proximas: [] });
      expect(filasProximas(el)).toHaveLength(0);
      expect(el.querySelector('.proximas')!.textContent).toContain('No hay citas próximas.');
      expect(kpi(el, 'Próxima cita')).toEqual({ valor: '—', detalle: 'Sin citas próximas' });
    });
  });

  describe('agenda y gráficas', () => {
    it('muestra la agenda del día que respondió la API', async () => {
      const { fixture, el } = await crear();
      const agenda = fixture.debugElement.query(x => x.name === 'app-agenda-dia');
      expect(agenda).not.toBeNull();
      expect(agenda.componentInstance.fecha()).toBe('2026-11-09');
      expect(el.querySelectorAll('app-agenda-dia [data-testid="columna"]').length).toBeGreaterThan(0);
    });

    it('dibuja las citas de los últimos 30 días y la ocupación por servicio de la semana', async () => {
      const { el } = await crear();
      expect(graficas.map(g => g.config.options.indexAxis)).toEqual(['x', 'y']);

      const [dias, semana] = graficas;
      expect(dias.config.data.labels).toHaveLength(30);
      expect(dias.config.data.labels[29]).toBe('9 nov');
      expect(dias.config.data.datasets[0].data).toEqual(INICIO.citasPorDia.map(d => d.total));

      expect(semana.config.data.labels).toEqual(['Corte de cabello', 'Barba']);
      expect(semana.config.data.datasets[0].data).toEqual([50, 26]);
      expect(el.querySelectorAll('app-grafica')).toHaveLength(2);
      expect(el.textContent).toContain('Citas por día, últimos 30 días');
      expect(el.textContent).toContain('Ocupación por servicio esta semana: Corte de cabello 50%, Barba 26%.');
    });
  });

  describe('recarga', () => {
    it('vuelve a pedir el tablero cuando cambian los datos del negocio', async () => {
      const { fixture, el } = await crear();
      expect(kpi(el, 'Citas hoy').valor).toBe('4');
      api().respuesta = () => Promise.resolve({ ...INICIO, citasHoy: { total: 3, atendidas: 1 } });
      await repo().guardarServicio((await repo().servicios())[0]);
      await esperar(fixture);
      expect(api().llamadas).toHaveLength(2);
      expect(kpi(el, 'Citas hoy').valor).toBe('3');
    });

    it('se refresca con el paso del tiempo, para que «en N min» no se quede viejo', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
      try {
        const { fixture, el } = await crear();
        expect(kpi(el, 'Próxima cita').detalle).toBe('en 90 min · Corte de cabello');
        api().respuesta = () => Promise.resolve({
          ...INICIO, proxima: { ...INICIO.proximas[0], minutosHasta: 89 },
        });
        vi.advanceTimersByTime(60_000);
        await esperar(fixture);
        expect(api().llamadas).toHaveLength(2);
        expect(kpi(el, 'Próxima cita').detalle).toBe('en 89 min · Corte de cabello');
        expect(api().llamadas.every(f => f === undefined)).toBe(true); // el tick tampoco manda fecha
      } finally {
        vi.useRealTimers();
      }
    });
  });
});
