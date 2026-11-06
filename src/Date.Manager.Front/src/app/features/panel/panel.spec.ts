import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Servicio } from '../../core/models';
import { alertas, citasPorDia } from '../../core/metricas';
import { aFechaISO, sumarDias } from '../../shared/formato';
import {
  AHORA, botonesAlerta, celdas, configurar, crear, DIA_MS, esperar, filasProximas, fijarReloj, GraficaDoble, kpi, repo,
  textosAlerta,
} from './panel.pruebas';
import { proximasCitas } from './panel-kpis';

const SIN_ALERTAS = 'Todo en orden: no hay alertas.';

const sinHorario = (n: number): Servicio => ({
  id: `extra-${n}`, nombre: `Extra ${n}`, duracionMin: 30, activo: true, valor: 1000, ventanas: [],
});

describe('PanelComponent', () => {
  let graficas: GraficaDoble[];
  beforeEach(async () => {
    graficas = await configurar();
  });

  it('saluda a quien inició sesión', async () => {
    const { el } = await crear();
    expect(el.querySelector('h2')!.textContent?.trim()).toBe('Hola, Marta. Así va el día');
  });

  it('muestra seis indicadores con sus etiquetas', async () => {
    const { el } = await crear();
    const etiquetas = Array.from(el.querySelectorAll('.kpi .kpi-etiqueta')).map(e => e.textContent?.trim());
    expect(etiquetas).toEqual([
      'Citas hoy', 'Próxima cita', 'Ocupación de hoy', 'Citas nuevas (30 días)', 'Canceladas (30 días)', 'Ingresos estimados',
    ]);
  });

  it('los indicadores salen de los datos de ejemplo', async () => {
    const { el } = await crear();
    const citas = await repo().citas();
    const hoy = citas.filter(c => c.fecha === '2026-11-09' && c.estado !== 'Cancelada');
    expect(kpi(el, 'Citas hoy')).toEqual({ valor: String(hoy.length), detalle: '0 ya atendidas' });

    const proxima = proximasCitas(citas, AHORA, 1)[0];
    expect(kpi(el, 'Próxima cita').valor).toBe(proxima.inicio);
    expect(kpi(el, 'Ocupación de hoy').valor).toMatch(/^\d+%$/);
    expect(kpi(el, 'Ingresos estimados').valor).toMatch(/\d/);
  });

  describe('alertas', () => {
    it('lista las alertas con su título y detalle', async () => {
      const { el } = await crear();
      const esperadas = alertas(await repo().servicios(), await repo().citas(), AHORA);
      expect(esperadas.length).toBeGreaterThan(0);
      expect(botonesAlerta(el)).toHaveLength(esperadas.length);
      const textos = textosAlerta(el);
      esperadas.forEach((a, i) => {
        expect(textos[i]).toContain(a.titulo);
        expect(textos[i]).toContain(a.detalle);
      });
      expect(el.textContent).not.toContain(SIN_ALERTAS);
    });

    it('la alerta «servicio sin horario» aparece al quitar todas las ventanas de un servicio activo y desaparece al restituirlas', async () => {
      const { fixture, el } = await crear();
      const servicio = (await repo().servicios())[0];
      expect(textosAlerta(el).some(t => t.includes('Servicio sin horario'))).toBe(false);

      await repo().guardarServicio({ ...servicio, ventanas: [] });
      await esperar(fixture);
      const alerta = textosAlerta(el).find(t => t.includes('Servicio sin horario'));
      expect(alerta).toContain(servicio.nombre);

      await repo().guardarServicio(servicio);
      await esperar(fixture);
      expect(textosAlerta(el).some(t => t.includes('Servicio sin horario'))).toBe(false);
    });

    it('cuando no hay alertas muestra «Todo en orden: no hay alertas.»', async () => {
      const { el } = await crear(new Date(AHORA.getTime() + 5 * DIA_MS)); // ya pasó el día de las citas nuevas y canceladas
      expect(botonesAlerta(el)).toHaveLength(0);
      expect(el.querySelector('.alertas')!.textContent).toContain(SIN_ALERTAS);
    });

    it('muestra a lo sumo 6 y avisa de cuántas más hay', async () => {
      const { fixture, el } = await crear();
      const base = alertas(await repo().servicios(), await repo().citas(), AHORA).length;
      for (let i = 1; i <= 7; i++) await repo().guardarServicio(sinHorario(i));
      await esperar(fixture);
      expect(botonesAlerta(el)).toHaveLength(6);
      expect(el.querySelector('.alertas .mas')!.textContent?.trim()).toBe(`y ${base + 7 - 6} más.`);
    });

    it('al hacer clic, las de citas llevan a Citas y las de servicios a Servicios', async () => {
      const { el } = await crear();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      botonesAlerta(el).find(b => b.textContent!.includes('Cita nueva'))!.click();
      expect(navegar).toHaveBeenLastCalledWith('/citas');
      botonesAlerta(el).find(b => b.textContent!.includes('Cita cancelada'))!.click();
      expect(navegar).toHaveBeenLastCalledWith('/citas');
    });

    it('la de servicio sin horario lleva a Servicios', async () => {
      const { fixture, el } = await crear();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      await repo().guardarServicio(sinHorario(1));
      await esperar(fixture);
      botonesAlerta(el).find(b => b.textContent!.includes('Servicio sin horario'))!.click();
      expect(navegar).toHaveBeenLastCalledWith('/servicios');
    });
  });

  describe('próximas citas', () => {
    it('lista las próximas 5 confirmadas con hora, cliente, servicio, teléfono y estado', async () => {
      const { el } = await crear();
      const encabezados = Array.from(el.querySelectorAll('.proximas thead th')).map(t => t.textContent?.trim());
      expect(encabezados).toEqual(['Hora', 'Cliente', 'Servicio', 'Teléfono', 'Estado', 'Acciones']);

      const citas = await repo().citas();
      const esperadas = proximasCitas(citas, AHORA, 5);
      expect(esperadas).toHaveLength(5);
      expect(filasProximas(el).map(f => f.dataset['cita'])).toEqual(esperadas.map(c => c.id));

      const primera = esperadas[0];
      const cliente = (await repo().clientes()).find(c => c.id === primera.clienteId)!;
      const servicio = (await repo().servicios()).find(s => s.id === primera.servicioId)!;
      const [hora, nombre, nombreServicio, telefono, estado] = celdas(filasProximas(el)[0]);
      expect(hora).toContain(primera.inicio);
      expect(nombre).toBe(`${cliente.nombre} ${cliente.apellido}`);
      expect(nombreServicio).toBe(servicio.nombre);
      expect(telefono).toBe(cliente.telefono);
      expect(estado).toBe('Confirmada');
    });

    it('muestra la fecha junto a la hora cuando la cita no es de hoy', async () => {
      const { el } = await crear();
      const citas = await repo().citas();
      const esperadas = proximasCitas(citas, AHORA, 5);
      const otroDia = esperadas.findIndex(c => c.fecha !== '2026-11-09');
      expect(otroDia).toBeGreaterThanOrEqual(0);
      expect(celdas(filasProximas(el)[otroDia])[0]).toMatch(/(Lunes|Martes|Miércoles|Jueves|Viernes|Sábado|Domingo) \d{1,2} de [a-zé]+/);
      const hoy = esperadas.findIndex(c => c.fecha === '2026-11-09');
      if (hoy >= 0) expect(celdas(filasProximas(el)[hoy])[0]).not.toMatch(/ de /);
    });

    it('«Abrir» lleva a Citas', async () => {
      const { el } = await crear();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      const abrir = filasProximas(el)[0].querySelector<HTMLButtonElement>('button')!;
      expect(abrir.textContent?.trim()).toBe('Abrir');
      abrir.click();
      expect(navegar).toHaveBeenCalledWith('/citas');
    });

    it('sin citas por delante dice «No hay citas próximas.» y «Sin citas próximas»', async () => {
      const { el } = await crear(new Date(AHORA.getTime() + 40 * DIA_MS));
      expect(filasProximas(el)).toHaveLength(0);
      expect(el.querySelector('.proximas')!.textContent).toContain('No hay citas próximas.');
      expect(kpi(el, 'Próxima cita')).toEqual({ valor: '—', detalle: 'Sin citas próximas' });
    });
  });

  describe('agenda y gráficas', () => {
    it('muestra la agenda de hoy', async () => {
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
      const serie = citasPorDia(await repo().citas(), sumarDias('2026-11-09', -29), '2026-11-09');
      expect(dias.config.data.labels).toHaveLength(30);
      expect(dias.config.data.datasets[0].data).toEqual(serie.map(d => d.total));

      const activos = (await repo().servicios()).filter(s => s.activo);
      expect(semana.config.data.labels).toEqual(activos.map(s => s.nombre));
      expect(semana.config.data.datasets[0].data.every((v: number) => v >= 0 && v <= 100)).toBe(true);
      expect(el.querySelectorAll('app-grafica')).toHaveLength(2);
      expect(el.textContent).toContain('Citas por día, últimos 30 días');
      expect(el.textContent).toContain('Ocupación por servicio esta semana');
    });
  });

  it('se recalcula cuando cambian los datos', async () => {
    const { fixture, el } = await crear();
    const antes = Number(kpi(el, 'Citas hoy').valor);
    const hoy = (await repo().citas()).find(c => c.fecha === '2026-11-09' && c.estado === 'Confirmada')!;
    await repo().cancelarCita(hoy.id);
    await esperar(fixture);
    expect(kpi(el, 'Citas hoy').valor).toBe(String(antes - 1));
    expect(Number(kpi(el, 'Canceladas (30 días)').valor)).toBeGreaterThan(0);
  });

  it('usa el día de hoy del reloj en cada carga', async () => {
    const { fixture, el } = await crear();
    const manana = new Date(AHORA.getTime() + DIA_MS);
    expect(aFechaISO(manana)).toBe('2026-11-10');
    fijarReloj(manana);
    await repo().guardarServicio((await repo().servicios())[0]);
    await esperar(fixture);
    expect(fixture.debugElement.query(x => x.name === 'app-agenda-dia').componentInstance.fecha()).toBe('2026-11-10');
    expect(el.querySelector('h2')).not.toBeNull();
  });
});
