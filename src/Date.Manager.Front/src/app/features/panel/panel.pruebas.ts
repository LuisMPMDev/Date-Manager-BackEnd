import { Component, input, Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { DemoAgendaRepo } from '../../core/demo-agenda-repo';
import { RELOJ } from '../../core/errores';
import { sembrar } from '../../core/seed';
import { SesionService } from '../../core/sesion';
import { FABRICA_GRAFICA } from '../../shared/grafica';
import { AgendaDiaComponent } from '../../shared/agenda-dia';
import { PanelComponent } from './panel';

/** Ayudas compartidas por las pruebas del panel (sin pruebas propias). */
export const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00
export const DIA_MS = 24 * 60 * 60 * 1000;

export interface GraficaDoble { canvas: HTMLCanvasElement; config: any; data: any; update: () => void; destroy: () => void }

/** Sustituto de la agenda del día cuando una prueba hace fallar el repositorio. */
@Component({ selector: 'app-agenda-dia', template: '' })
export class AgendaDiaFalsa {
  readonly fecha = input('');
}

let reloj = AHORA;
export const fijarReloj = (fecha: Date) => (reloj = fecha);

export async function configurar(opciones: { repo?: Type<AgendaRepo>; sinAgenda?: boolean } = {}) {
  localStorage.clear();
  sessionStorage.clear();
  reloj = AHORA;
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
      { provide: RELOJ, useValue: () => reloj },
      { provide: AgendaRepo, useClass: opciones.repo ?? DemoAgendaRepo },
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

/** `ahora` mueve el reloj después de sembrar los datos de ejemplo (que son relativos al lunes 9, 08:00). */
export async function crear(ahora?: Date) {
  const datos = sembrar(AHORA);
  const marta = datos.usuarios.find(u => u.usuario === 'marta')!;
  TestBed.inject(SesionService).iniciar(marta, datos.roles);
  TestBed.inject(AgendaRepo); // siembra con el reloj de las 08:00
  if (ahora) reloj = ahora;
  const fixture = TestBed.createComponent(PanelComponent);
  fixture.detectChanges();
  await esperar(fixture);
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

export const repo = () => TestBed.inject(AgendaRepo);

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
