import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgendaRepo } from '../../core/agenda-repo';
import { AuthApi } from '../../core/auth-api';
import { DemoAgendaRepo } from '../../testing/demo-agenda-repo';
import { RELOJ } from '../../core/errores';
import { Permiso, Usuario } from '../../core/models';
import { SesionService } from '../../core/sesion';
import { AuthApiDemo, rolDe } from '../../core/sesion.pruebas';
import { UsuariosComponent } from './usuarios';

/** Ayudas compartidas por las pruebas de usuarios y roles (sin pruebas propias). */
export const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00

export async function configurar(repo: typeof DemoAgendaRepo = DemoAgendaRepo): Promise<void> {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [UsuariosComponent],
    providers: [
      { provide: RELOJ, useValue: () => AHORA },
      { provide: AgendaRepo, useClass: repo },
      { provide: AuthApi, useClass: AuthApiDemo },
    ],
  }).compileComponents();
}

export async function esperar(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise(r => setTimeout(r));
    fixture.detectChanges();
  }
}

export const repo = () => TestBed.inject(AgendaRepo);
export const sesion = () => TestBed.inject(SesionService);

/** Cambia los permisos de un rol directo en el repositorio (sin las reglas de la pantalla). */
export async function fijarPermisos(rolId: string, permisos: Permiso[]): Promise<void> {
  const rol = (await repo().roles()).find(r => r.id === rolId)!;
  await repo().guardarRol({ ...rol, permisos });
}

/**
 * Abre la pantalla con la sesión de `usuario` (por defecto marta, administradora).
 * `antes` prepara datos en el repositorio antes de iniciar la sesión.
 */
export async function crear(opciones: { usuario?: string; antes?: () => Promise<void> } = {}) {
  await opciones.antes?.();
  const [usuarios, roles] = await Promise.all([repo().usuarios(), repo().roles()]);
  const quien = usuarios.find(u => u.usuario === (opciones.usuario ?? 'marta'))!;
  sesion().iniciar(quien, rolDe(quien, roles));
  const fixture = TestBed.createComponent(UsuariosComponent);
  fixture.detectChanges();
  await esperar(fixture);
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

export type NombreSeccion = 'usuarios' | 'roles';
export const seccion = (el: HTMLElement, nombre: NombreSeccion) =>
  el.querySelector<HTMLElement>(`section[data-seccion="${nombre}"]`)!;
export const filasDe = (s: HTMLElement) => Array.from(s.querySelectorAll<HTMLTableRowElement>('tbody tr[data-fila]'));
export const fila = (s: HTMLElement, id: string) => s.querySelector<HTMLTableRowElement>(`tr[data-fila="${id}"]`)!;
export const celdas = (f: Element) => Array.from(f.querySelectorAll('td')).map(c => c.textContent?.trim() ?? '');
export const alerta = (s: HTMLElement) => s.querySelector('[role="alert"]')?.textContent?.trim() ?? '';
export const aviso = (s: HTMLElement) => s.querySelector('[role="status"]')?.textContent?.trim() ?? '';
export const boton = (s: HTMLElement, texto: string) =>
  Array.from(s.querySelectorAll<HTMLButtonElement>('button')).find(b => b.textContent?.trim() === texto)!;
export const botonDe = (s: HTMLElement, nombreAccesible: string) =>
  s.querySelector<HTMLButtonElement>(`button[aria-label="${nombreAccesible}"]`)!;
export const campo = <T extends HTMLElement = HTMLInputElement>(s: HTMLElement, nombre: string) =>
  s.querySelector<T>(`form [name="${nombre}"]`)!;

export function escribir(input: HTMLInputElement, valor: string): void {
  input.value = valor;
  input.dispatchEvent(new Event('input'));
}

export function elegir(select: HTMLSelectElement, valor: string): void {
  select.value = valor;
  select.dispatchEvent(new Event('change'));
}

export async function pulsar(fixture: ComponentFixture<unknown>, b: HTMLElement): Promise<void> {
  b.click();
  await esperar(fixture);
}

export async function marcar(fixture: ComponentFixture<unknown>, casilla: HTMLInputElement, valor: boolean): Promise<void> {
  casilla.checked = valor;
  casilla.dispatchEvent(new Event('change'));
  await esperar(fixture);
}

export const usuarioPorNombre = async (nombre: string): Promise<Usuario> =>
  (await repo().usuarios()).find(u => u.usuario === nombre)!;
