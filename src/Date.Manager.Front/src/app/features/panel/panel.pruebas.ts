import { Component, Injectable, input, Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { AuthApi } from '../../core/auth-api';
import { RELOJ } from '../../core/errores';
import { Inicio } from '../../core/inicio';
import { InicioApi } from '../../core/inicio-api';
import { SesionService } from '../../core/sesion';
import { AuthApiDemo, rolDe } from '../../core/sesion.pruebas';
import { AgendaDiaComponent } from '../../shared/agenda-dia';
import { sumarDias } from '../../shared/formato';
import { FABRICA_GRAFICA } from '../../shared/grafica';
import { DemoAgendaRepo } from '../../testing/demo-agenda-repo';
import { sembrar } from '../../testing/seed';
import { PanelComponent } from './panel';

/** Ayudas compartidas por las pruebas del panel (sin pruebas propias). */
export const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00

export interface GraficaDoble { canvas: HTMLCanvasElement; config: any; data: any; update: () => void; destroy: () => void }

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

/** InicioApi de pruebas: responde lo que diga `respuesta` y anota la fecha de cada llamada. */
@Injectable()
export class InicioApiFalsa {
  readonly llamadas: (string | undefined)[] = [];
  respuesta: () => Promise<Inicio> = () => Promise.resolve(INICIO);

  inicio(fecha?: string): Promise<Inicio> {
    this.llamadas.push(fecha);
    return this.respuesta();
  }
}

/** Sustituto de la agenda del día cuando una prueba hace fallar el repositorio. */
@Component({ selector: 'app-agenda-dia', template: '' })
export class AgendaDiaFalsa {
  readonly fecha = input('');
}

export async function configurar(opciones: { repo?: Type<AgendaRepo>; sinAgenda?: boolean } = {}) {
  localStorage.clear();
  sessionStorage.clear();
  const graficas: GraficaDoble[] = [];
  const fabrica = (canvas: HTMLCanvasElement, config: any) => {
    const g = { canvas, config, data: config.data, update: () => undefined, destroy: () => undefined };
    graficas.push(g);
    return g;
  };
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [PanelComponent],
    providers: [
      provideRouter([]),
      { provide: RELOJ, useValue: () => new Date(AHORA) }, // una instancia nueva por lectura, como el reloj real
      { provide: AgendaRepo, useClass: opciones.repo ?? DemoAgendaRepo },
      { provide: AuthApi, useClass: AuthApiDemo },
      { provide: InicioApi, useClass: InicioApiFalsa },
      { provide: FABRICA_GRAFICA, useValue: fabrica },
    ],
  });
  if (opciones.sinAgenda) {
    TestBed.overrideComponent(PanelComponent, { remove: { imports: [AgendaDiaComponent] }, add: { imports: [AgendaDiaFalsa] } });
  }
  await TestBed.compileComponents();
  return graficas;
}

export async function esperar(fixture: ComponentFixture<unknown>) {
  for (let i = 0; i < 3; i++) {
    await new Promise(r => setTimeout(r));
    fixture.detectChanges();
  }
}

export const api = () => TestBed.inject(InicioApi) as unknown as InicioApiFalsa;
export const repo = () => TestBed.inject(AgendaRepo);

/** `inicio` es lo que responderá la API en la primera carga (por defecto, `INICIO`). */
export async function crear(inicio?: Inicio | (() => Promise<Inicio>)) {
  const datos = sembrar(AHORA);
  const marta = datos.usuarios.find(u => u.usuario === 'marta')!;
  TestBed.inject(SesionService).iniciar(marta, rolDe(marta, datos.roles));
  TestBed.inject(AgendaRepo); // siembra con el reloj de las 08:00
  if (inicio) api().respuesta = typeof inicio === 'function' ? inicio : () => Promise.resolve(inicio);
  const fixture = TestBed.createComponent(PanelComponent);
  fixture.detectChanges();
  await esperar(fixture);
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

export function kpi(el: HTMLElement, etiqueta: string) {
  const tarjeta = Array.from(el.querySelectorAll('.kpi')).find(k => k.querySelector('.kpi-etiqueta')?.textContent?.trim() === etiqueta)!;
  return {
    valor: tarjeta?.querySelector('.kpi-valor')?.textContent?.trim(),
    detalle: tarjeta?.querySelector('.kpi-detalle')?.textContent?.trim(),
  };
}

export const botonesAlerta = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLButtonElement>('.alertas button'));
export const textosAlerta = (el: HTMLElement) => botonesAlerta(el).map(b => b.textContent?.replace(/\s+/g, ' ').trim() ?? '');
export const filasProximas = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLTableRowElement>('.proximas tbody tr[data-cita]'));
export const celdas = (fila: Element) => Array.from(fila.querySelectorAll('td')).map(c => c.textContent?.replace(/\s+/g, ' ').trim() ?? '');
