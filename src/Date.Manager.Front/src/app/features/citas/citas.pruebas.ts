import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgendaRepo } from '../../core/agenda-repo';
import { AuthApi } from '../../core/auth-api';
import { DemoAgendaRepo } from '../../testing/demo-agenda-repo';
import { RELOJ } from '../../core/errores';
import { Cita, Permiso } from '../../core/models';
import { sembrar } from '../../testing/seed';
import { SesionService } from '../../core/sesion';
import { AuthApiDemo, rolDe } from '../../core/sesion.pruebas';
import { CitasComponent } from './citas';

/** Ayudas compartidas por las pruebas de la pantalla de citas (sin pruebas propias). */
export const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00
export const HOY = '2026-11-09';
export const TARDE = new Date(2026, 10, 9, 23, 0); // ya pasaron todas las citas de hoy

let reloj = AHORA;

export async function configurar(): Promise<void> {
  localStorage.clear();
  sessionStorage.clear();
  reloj = AHORA;
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [CitasComponent],
    providers: [
      { provide: RELOJ, useValue: () => reloj },
      { provide: AgendaRepo, useClass: DemoAgendaRepo },
      { provide: AuthApi, useClass: AuthApiDemo },
    ],
  }).compileComponents();
}

export async function esperar(fixture: ComponentFixture<unknown>) {
  for (let i = 0; i < 2; i++) {
    await new Promise(r => setTimeout(r));
    fixture.detectChanges();
  }
}

/**
 * Sin `permisos`, la sesión es del administrador (todos los permisos).
 * `ahora` mueve el reloj después de sembrar los datos de ejemplo (que son relativos al lunes 9, 08:00).
 */
export async function crear(permisos?: Permiso[], ahora?: Date) {
  const datos = sembrar(AHORA);
  const marta = datos.usuarios.find(u => u.usuario === 'marta')!;
  const roles = permisos ? [{ ...datos.roles[0], permisos }] : datos.roles;
  TestBed.inject(SesionService).iniciar(marta, rolDe(marta, roles));
  TestBed.inject(AgendaRepo); // siembra con el reloj de las 08:00
  if (ahora) reloj = ahora;
  const fixture = TestBed.createComponent(CitasComponent);
  fixture.detectChanges();
  await esperar(fixture);
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

export const repo = () => TestBed.inject(AgendaRepo);
export const filas = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLTableRowElement>('tbody tr[data-cita]'));
export const celdas = (fila: HTMLElement) => Array.from(fila.querySelectorAll('td')).map(c => c.textContent?.trim() ?? '');
export const fila = (el: HTMLElement, id: string) => el.querySelector<HTMLTableRowElement>(`tr[data-cita="${id}"]`)!;
export const ids = (el: HTMLElement) => filas(el).map(f => f.dataset['cita']);
export const botones = (el: HTMLElement | Element) => Array.from(el.querySelectorAll<HTMLButtonElement>('button'));
export const boton = (el: HTMLElement | Element, texto: string) => botones(el).find(b => b.textContent?.trim() === texto)!;
export const textosBotones = (el: HTMLElement | Element) => botones(el).map(b => b.textContent?.trim());
export const dialogo = (el: HTMLElement) => el.querySelector<HTMLElement>('[role="dialog"]');
export const alerta = (el: HTMLElement) => el.querySelector('[role="alert"]')?.textContent?.trim() ?? '';

export function elegir(el: HTMLElement, selector: string, valor: string) {
  const campo = el.querySelector<HTMLInputElement | HTMLSelectElement>(selector)!;
  campo.value = valor;
  campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
}

export const porFechaYHora = (a: Cita, b: Cita) => a.fecha.localeCompare(b.fecha) || a.inicio.localeCompare(b.inicio);
export const desde = (citas: Cita[], fecha: string) => citas.filter(c => c.fecha >= fecha).sort(porFechaYHora);

export async function primeraConfirmada(el: HTMLElement): Promise<Cita> {
  const citas = await repo().citas();
  const id = ids(el).find(i => citas.find(c => c.id === i)!.estado === 'Confirmada')!;
  return citas.find(c => c.id === id)!;
}