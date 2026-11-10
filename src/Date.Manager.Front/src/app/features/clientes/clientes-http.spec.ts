import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Cita, Cliente } from '../../core/models';
import { SesionService } from '../../core/sesion';
import { MARTA, ROL_ADMIN } from '../../core/sesion.pruebas';
import { abrirSesion, configurarHttp, esperar, fallar, http, ruta, servir } from '../../testing/http-pantallas';
import { ClientesComponent } from './clientes';

const SIN_PERMISO = 'No tienes permiso para ver esta información.';

const LAURA: Cliente = { id: 'k1', nombre: 'Laura', apellido: 'Gómez', telefono: '300 111', correo: 'laura@correo.com' };
const PEDRO: Cliente = { id: 'k2', nombre: 'Pedro', apellido: 'Ruiz', telefono: '300 222', correo: 'pedro@correo.com' };
const CITAS: Cita[] = [
  { id: 'c1', servicioId: 's1', clienteId: 'k1', fecha: '2026-11-10', inicio: '10:00', fin: '10:30', estado: 'Confirmada', creadaEn: '2026-11-09T12:00:00Z' },
];

/** Abre la pantalla con las tres lecturas ya respondidas (o con el fallo que se pida en citas / clientes). */
async function crear(opciones: { citas?: () => void; clientes?: () => void } = {}) {
  configurarHttp();
  abrirSesion(MARTA, ROL_ADMIN);
  const fixture = TestBed.createComponent(ClientesComponent);
  fixture.detectChanges();
  (opciones.clientes ?? (() => servir('panel/clientes', [LAURA, PEDRO])))();
  (opciones.citas ?? (() => servir('panel/citas', CITAS)))();
  await esperar(fixture);
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

const filas = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLTableRowElement>('tbody tr[data-cliente]'));
const celdas = (f: Element) => Array.from(f.querySelectorAll('td')).map(c => c.textContent?.trim() ?? '');
const campo = (el: HTMLElement, nombre: string) => el.querySelector<HTMLInputElement>(`form [name="${nombre}"]`)!;
const guardar = (el: HTMLElement) =>
  Array.from(el.querySelectorAll<HTMLButtonElement>('form button')).find(b => b.textContent?.trim() === 'Guardar cliente')!;

async function editarLaura(fixture: { detectChanges(): void }, el: HTMLElement) {
  el.querySelector<HTMLButtonElement>('button[aria-label="Editar a Laura Gómez"]')!.click();
  fixture.detectChanges();
}

describe('ClientesComponent con la API', () => {
  afterEach(() => http().verify());

  describe('permisos auxiliares', () => {
    it('un 403 en GET /panel/citas deja la lista de clientes y avisa en el historial', async () => {
      const { el } = await crear({ citas: () => fallar('panel/citas', 403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción.') });

      expect(filas(el)).toHaveLength(2);
      expect(celdas(filas(el)[0])[0]).toBe('Laura');
      expect(el.querySelector('[data-aviso="citas"]')?.textContent?.trim()).toContain(SIN_PERMISO);
      expect(el.querySelector('[role="alert"]')).toBeNull();
      expect(celdas(filas(el)[0])[4]).toBe('—'); // sin permiso no se sabe cuántas citas tiene
    });

    it('con permiso para las citas no hay aviso y se cuentan las citas de cada cliente', async () => {
      const { el } = await crear();

      expect(el.querySelector('[data-aviso="citas"]')).toBeNull();
      expect(celdas(filas(el)[0])[4]).toBe('1');
      expect(celdas(filas(el)[1])[4]).toBe('0');
    });

    it('otro fallo de las citas avisa en el historial sin esconder los clientes', async () => {
      const { el } = await crear({ citas: () => fallar('panel/citas', 500, 'ERROR_INTERNO', 'Error del servidor.') });

      expect(filas(el)).toHaveLength(2);
      expect(el.querySelector('[data-aviso="citas"]')?.textContent?.trim()).toContain('No se pudo cargar el historial de citas.');
    });

    it('un 403 en la llamada principal (clientes) manda a /sin-acceso', async () => {
      await crear({ clientes: () => fallar('panel/clientes', 403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción.') });

      expect(ruta()).toBe('/sin-acceso');
    });
  });

  describe('errores al guardar', () => {
    async function guardarConError(status: number, codigo: string, mensaje: string, campos?: Record<string, string>) {
      const { fixture, el } = await crear();
      await editarLaura(fixture, el);
      guardar(el).click();
      fallar(`panel/clientes/${LAURA.id}`, status, codigo, mensaje, campos, 'PUT');
      await esperar(fixture);
      return { fixture, el };
    }

    it('un 400 con campos {correo} marca el campo correo y no repite el mensaje general', async () => {
      const { el } = await guardarConError(400, 'VALIDACION', 'Hay datos inválidos en la petición.', { correo: 'Escribe un correo válido.' });

      expect(el.querySelector('#error-correo')?.textContent?.trim()).toBe('Escribe un correo válido.');
      expect(campo(el, 'correo').getAttribute('aria-invalid')).toBe('true');
      expect(campo(el, 'correo').getAttribute('aria-describedby')).toBe('error-correo');
      expect(campo(el, 'nombre').getAttribute('aria-invalid')).toBeNull();
      expect(el.querySelector('[role="alert"]')).toBeNull();
    });

    it('un 400 con campos {cliente.correo} marca el mismo campo correo', async () => {
      const { el } = await guardarConError(400, 'VALIDACION', 'x', { 'cliente.correo': 'Escribe un correo válido.' });

      expect(el.querySelector('#error-correo')?.textContent?.trim()).toBe('Escribe un correo válido.');
      expect(campo(el, 'correo').getAttribute('aria-invalid')).toBe('true');
    });

    it('un 400 sin campos muestra el mensaje como error general', async () => {
      const { el } = await guardarConError(400, 'VALIDACION', 'Los datos del cliente no son válidos.');

      expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Los datos del cliente no son válidos.');
      expect(el.querySelector('#error-correo')).toBeNull();
    });

    it('los errores de campo se limpian al volver a guardar', async () => {
      const { fixture, el } = await guardarConError(400, 'VALIDACION', 'x', { correo: 'Escribe un correo válido.' });

      guardar(el).click();
      fixture.detectChanges();
      expect(el.querySelector('#error-correo')).toBeNull();
      servir(`panel/clientes/${LAURA.id}`, LAURA, 'PUT');
      await esperar(fixture);
      servir('panel/clientes', [LAURA, PEDRO]); // tras guardar, la pantalla recarga
      servir('panel/citas', CITAS);
      await esperar(fixture);
    });

    it('un 409 muestra el mensaje de la API y recarga clientes y citas', async () => {
      const { fixture, el } = await guardarConError(409, 'CONFLICTO', 'Ya existe otro cliente con ese correo.');

      expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Ya existe otro cliente con ese correo.');
      expect(guardar(el).disabled).toBe(false);
      servir('panel/clientes', [LAURA, PEDRO]); // la pantalla volvió a pedir los datos
      servir('panel/citas', CITAS);
      await esperar(fixture);
    });

    it('un error de red usa el mensaje de respaldo y deja el botón libre', async () => {
      const { fixture, el } = await crear();
      await editarLaura(fixture, el);
      guardar(el).click();
      http().expectOne(r => r.method === 'PUT').error(new ProgressEvent('error'));
      await esperar(fixture);

      expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toContain('No se pudo contactar al servidor');
      expect(guardar(el).disabled).toBe(false);
    });
  });

  describe('peticiones en curso', () => {
    it('un doble clic en guardar envía una sola petición y el botón queda deshabilitado', async () => {
      const { fixture, el } = await crear();
      await editarLaura(fixture, el);
      const boton = guardar(el);
      boton.click();
      boton.click();
      fixture.detectChanges();

      expect(http().match(r => r.method === 'PUT')).toHaveLength(1);
      expect(boton.disabled).toBe(true);
    });

    it('un 401 al guardar lleva a /login con aviso, una sola vez, y el botón no queda en «guardando»', async () => {
      const { fixture, el } = await crear();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
      await editarLaura(fixture, el);
      const boton = guardar(el);
      boton.click();
      fallar(`panel/clientes/${LAURA.id}`, 401, 'NO_AUTENTICADO', 'Inicia sesión para continuar.', undefined, 'PUT');
      await esperar(fixture);

      expect(navegar.mock.calls.filter(([url]) => url === '/login')).toHaveLength(1);
      expect(ruta()).toBe('/login');
      expect(TestBed.inject(SesionService).aviso()).toBe('Tu sesión venció. Inicia sesión de nuevo.');
      expect(boton.disabled).toBe(false);
    });
  });
});
