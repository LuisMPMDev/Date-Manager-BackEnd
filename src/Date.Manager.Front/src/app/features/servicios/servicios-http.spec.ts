import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Servicio } from '../../core/models';
import { SesionService } from '../../core/sesion';
import { MARTA, ROL_ADMIN } from '../../core/sesion.pruebas';
import { abrirSesion, configurarHttp, esperar, fallar, http, ruta, servir } from '../../testing/http-pantallas';
import { ServiciosComponent } from './servicios';

const CONSULTA: Servicio = {
  id: 'srv-consulta', nombre: 'Consulta', duracionMin: 30, activo: true, valor: 40000,
  ventanas: [{ id: 'v1', dia: 1, inicio: '09:00', fin: '12:00' }],
};

async function crear(servicios: () => void = () => servir('panel/servicios', [CONSULTA])) {
  configurarHttp();
  abrirSesion(MARTA, ROL_ADMIN);
  const fixture = TestBed.createComponent(ServiciosComponent);
  fixture.detectChanges();
  servicios();
  await esperar(fixture);
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

const boton = (el: HTMLElement, texto: string) =>
  Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(b => b.textContent?.trim() === texto)!;
const porNombre = (el: HTMLElement, nombre: string) => el.querySelector<HTMLButtonElement>(`button[aria-label="${nombre}"]`)!;
const PUT = `panel/servicios/${CONSULTA.id}`;

async function editar(fixture: { detectChanges(): void }, el: HTMLElement) {
  porNombre(el, 'Editar Consulta').click();
  fixture.detectChanges();
}

describe('ServiciosComponent con la API', () => {
  afterEach(() => http().verify());

  it('un 403 en la llamada principal (servicios) manda a /sin-acceso', async () => {
    await crear(() => fallar('panel/servicios', 403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción.'));

    expect(ruta()).toBe('/sin-acceso');
  });

  it('otro fallo de carga muestra un error en vez de dejar la pantalla vacía en silencio', async () => {
    const { el } = await crear(() => fallar('panel/servicios', 500, 'ERROR_INTERNO', 'Error del servidor.'));

    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('No se pudieron cargar los servicios.');
  });

  describe('errores al guardar', () => {
    async function guardarConError(status: number, codigo: string, mensaje: string, campos?: Record<string, string>) {
      const { fixture, el } = await crear();
      await editar(fixture, el);
      boton(el, 'Guardar servicio').click();
      fallar(PUT, status, codigo, mensaje, campos, 'PUT');
      await esperar(fixture);
      return { fixture, el };
    }

    it('un 400 con campos marca nombre y duración junto a cada campo', async () => {
      const { el } = await guardarConError(400, 'VALIDACION', 'x', {
        nombre: 'El nombre es obligatorio.', duracionMin: 'La duración debe ser mayor que cero.',
      });

      expect(el.querySelector('#error-nombre')?.textContent?.trim()).toBe('El nombre es obligatorio.');
      expect(el.querySelector('#error-duracion')?.textContent?.trim()).toBe('La duración debe ser mayor que cero.');
      expect(el.querySelector('[name="nombre"]')?.getAttribute('aria-invalid')).toBe('true');
      expect(el.querySelector('[name="duracion"]')?.getAttribute('aria-describedby')).toBe('error-duracion');
      expect(el.querySelector('[name="valor"]')?.getAttribute('aria-invalid')).toBeNull();
      expect(el.querySelector('[role="alert"]')).toBeNull();
    });

    it('un campo que el formulario no muestra (una ventana) llega al error general', async () => {
      const { el } = await guardarConError(400, 'VALIDACION', 'x', { 'ventanas[0].fin': 'La hora final debe ser posterior al inicio.' });

      expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('La hora final debe ser posterior al inicio.');
    });

    it('un 400 sin campos muestra el mensaje como error general', async () => {
      const { el } = await guardarConError(400, 'VALIDACION', 'Las ventanas se cruzan.');

      expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Las ventanas se cruzan.');
    });

    it('un 409 muestra su mensaje y recarga los servicios', async () => {
      const { fixture, el } = await guardarConError(409, 'CONFLICTO', 'Hay citas confirmadas en esas ventanas.');

      expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Hay citas confirmadas en esas ventanas.');
      expect(boton(el, 'Guardar servicio').disabled).toBe(false);
      servir('panel/servicios', [CONSULTA]);
      await esperar(fixture);
    });
  });

  describe('peticiones en curso', () => {
    it('un doble clic en guardar envía una sola petición y el botón queda deshabilitado', async () => {
      const { fixture, el } = await crear();
      await editar(fixture, el);
      const guardar = boton(el, 'Guardar servicio');
      guardar.click();
      guardar.click();
      fixture.detectChanges();

      expect(http().match(r => r.method === 'PUT')).toHaveLength(1);
      expect(guardar.disabled).toBe(true);
    });

    it('un 401 al guardar lleva a /login con aviso, una sola vez, y el botón no queda en «guardando»', async () => {
      const { fixture, el } = await crear();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
      await editar(fixture, el);
      const guardar = boton(el, 'Guardar servicio');
      guardar.click();
      fallar(PUT, 401, 'NO_AUTENTICADO', 'Inicia sesión para continuar.', undefined, 'PUT');
      await esperar(fixture);

      expect(navegar.mock.calls.filter(([url]) => url === '/login')).toHaveLength(1);
      expect(ruta()).toBe('/login');
      expect(TestBed.inject(SesionService).aviso()).toBe('Tu sesión venció. Inicia sesión de nuevo.');
      expect(guardar.disabled).toBe(false);
    });
  });

  describe('activar y desactivar desde la tabla', () => {
    it('un doble clic en «Desactivar» envía una sola petición', async () => {
      const { fixture, el } = await crear();
      const b = porNombre(el, 'Desactivar Consulta');
      b.click();
      b.click();
      fixture.detectChanges();

      expect(http().match(r => r.method === 'PUT')).toHaveLength(1);
      expect(b.disabled).toBe(true);
    });

    it('si la API rechaza el cambio se ve su mensaje, la tabla se recarga y el botón se libera', async () => {
      const { fixture, el } = await crear();
      const b = porNombre(el, 'Desactivar Consulta');
      b.click();
      fallar(PUT, 409, 'CONFLICTO', 'El servicio tiene citas confirmadas.', undefined, 'PUT');
      await esperar(fixture);

      expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('El servicio tiene citas confirmadas.');
      expect(b.disabled).toBe(false);
      servir('panel/servicios', [CONSULTA]);
      await esperar(fixture);
    });
  });
});
