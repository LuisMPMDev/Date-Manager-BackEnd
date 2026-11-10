import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Rol } from '../../core/models';
import { SesionService } from '../../core/sesion';
import { ANDRES, MARTA, ROL_ADMIN, ROL_RECEPCION } from '../../core/sesion.pruebas';
import { abrirSesion, configurarHttp, esperar, fallar, http, ruta, servir } from '../../testing/http-pantallas';
import { UsuariosComponent } from './usuarios';

const SIN_PERMISO = 'No tienes permiso para ver esta información.';
const ROL_SOLO_ROLES: Rol = { id: 'r-roles', nombre: 'Gestor de roles', descripcion: 'Solo roles', permisos: ['gestionar_roles'] };

/** Abre la pantalla como `rol` y responde las dos lecturas (o el fallo que se pida). */
async function crear(opciones: { rol?: Rol; usuarios?: () => void; roles?: () => void } = {}) {
  configurarHttp();
  abrirSesion(MARTA, opciones.rol ?? ROL_ADMIN);
  const fixture = TestBed.createComponent(UsuariosComponent);
  fixture.detectChanges();
  (opciones.usuarios ?? (() => servir('panel/usuarios', [MARTA, ANDRES])))();
  (opciones.roles ?? (() => servir('panel/roles', [ROL_ADMIN, ROL_RECEPCION, ROL_SOLO_ROLES])))();
  await esperar(fixture);
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

const seccion = (el: HTMLElement, nombre: 'usuarios' | 'roles') =>
  el.querySelector<HTMLElement>(`section[data-seccion="${nombre}"]`);
const filas = (s: HTMLElement) => Array.from(s.querySelectorAll<HTMLTableRowElement>('tbody tr[data-fila]'));
const celdas = (f: Element) => Array.from(f.querySelectorAll('td')).map(c => c.textContent?.trim() ?? '');
const boton = (s: HTMLElement, texto: string) =>
  Array.from(s.querySelectorAll<HTMLButtonElement>('button')).find(b => b.textContent?.trim() === texto)!;

describe('UsuariosComponent con la API', () => {
  afterEach(() => http().verify());

  describe('permisos auxiliares', () => {
    it('con solo gestionar_roles, un 403 en GET /panel/usuarios muestra los roles y avisa de los usuarios', async () => {
      const { el } = await crear({
        rol: ROL_SOLO_ROLES,
        usuarios: () => fallar('panel/usuarios', 403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción.'),
      });

      const roles = seccion(el, 'roles')!;
      expect(filas(roles)).toHaveLength(3);
      expect(seccion(el, 'usuarios')).toBeNull();
      expect(roles.querySelector('[data-aviso="usuarios"]')?.textContent?.trim()).toContain(SIN_PERMISO);
      expect(el.querySelector('[role="alert"]')).toBeNull();
      expect(celdas(filas(roles)[0])[2]).toBe('—'); // sin saber los usuarios no se puede contar quién tiene cada rol
    });

    it('si se pueden leer los usuarios, la sección de roles cuenta quién tiene cada rol y no avisa nada', async () => {
      const { el } = await crear();

      const roles = seccion(el, 'roles')!;
      expect(roles.querySelector('[data-aviso="usuarios"]')).toBeNull();
      expect(celdas(filas(roles)[0])[2]).toBe('1');
      expect(filas(seccion(el, 'usuarios')!)).toHaveLength(2);
    });

    it('un 403 en los roles avisa en la sección de usuarios, que sigue funcionando', async () => {
      const { el } = await crear({
        roles: () => fallar('panel/roles', 403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción.'),
      });

      const usuarios = seccion(el, 'usuarios')!;
      expect(filas(usuarios)).toHaveLength(2);
      expect(usuarios.querySelector('[data-aviso="roles"]')?.textContent?.trim()).toContain(SIN_PERMISO);
    });

    it('un 403 en ambas llamadas manda a /sin-acceso', async () => {
      await crear({
        usuarios: () => fallar('panel/usuarios', 403, 'SIN_PERMISO', 'x'),
        roles: () => fallar('panel/roles', 403, 'SIN_PERMISO', 'x'),
      });

      expect(ruta()).toBe('/sin-acceso');
    });

    it('otro fallo de carga muestra el error general de la pantalla', async () => {
      const { el } = await crear({ roles: () => fallar('panel/roles', 500, 'ERROR_INTERNO', 'Error del servidor.') });

      expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('No se pudo cargar usuarios y roles.');
    });
  });

  describe('usuarios: errores al guardar', () => {
    async function guardarConError(status: number, codigo: string, mensaje: string, campos?: Record<string, string>) {
      const { fixture, el } = await crear();
      const s = seccion(el, 'usuarios')!;
      s.querySelector<HTMLButtonElement>('button[aria-label="Editar usuario Andrés Lora"]')!.click();
      fixture.detectChanges();
      boton(s, 'Guardar usuario').click();
      fallar(`panel/usuarios/${ANDRES.id}`, status, codigo, mensaje, campos, 'PUT');
      await esperar(fixture);
      return { fixture, el, s };
    }

    it('un 400 con campos {correo} marca el campo correo; con {cliente.correo} también', async () => {
      for (const clave of ['correo', 'cliente.correo']) {
        const { s } = await guardarConError(400, 'VALIDACION', 'x', { [clave]: 'Escribe un correo válido.' });
        expect(s.querySelector('#error-usuario-correo')?.textContent?.trim()).toBe('Escribe un correo válido.');
        expect(s.querySelector('[name="correo"]')?.getAttribute('aria-invalid')).toBe('true');
        expect(s.querySelector('[role="alert"]')).toBeNull();
      }
    });

    it('un 400 sin campos muestra el mensaje como error general', async () => {
      const { s } = await guardarConError(400, 'VALIDACION', 'La clave es demasiado corta.');

      expect(s.querySelector('[role="alert"]')?.textContent?.trim()).toBe('La clave es demasiado corta.');
      expect(s.querySelector('[aria-invalid="true"]')).toBeNull();
    });

    it('un 409 muestra su mensaje y recarga usuarios y roles', async () => {
      const { fixture, s } = await guardarConError(409, 'CONFLICTO', 'Ya existe otro usuario con ese nombre de usuario.');

      expect(s.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Ya existe otro usuario con ese nombre de usuario.');
      expect(boton(s, 'Guardar usuario').disabled).toBe(false);
      servir('panel/usuarios', [MARTA, ANDRES]);
      servir('panel/roles', [ROL_ADMIN, ROL_RECEPCION, ROL_SOLO_ROLES]);
      await esperar(fixture);
    });
  });

  describe('roles: errores al guardar y al eliminar', () => {
    async function editarRecepcion() {
      const { fixture, el } = await crear();
      const s = seccion(el, 'roles')!;
      s.querySelector<HTMLButtonElement>('button[aria-label="Editar rol Recepción"]')!.click();
      fixture.detectChanges();
      return { fixture, el, s };
    }

    it('un 400 con campos {nombre} marca el campo nombre del rol', async () => {
      const { fixture, s } = await editarRecepcion();
      boton(s, 'Guardar rol').click();
      fallar(`panel/roles/${ROL_RECEPCION.id}`, 400, 'VALIDACION', 'x', { nombre: 'El nombre es obligatorio.' }, 'PUT');
      await esperar(fixture);

      expect(s.querySelector('#error-rol-nombre')?.textContent?.trim()).toBe('El nombre es obligatorio.');
      expect(s.querySelector('[name="nombre"]')?.getAttribute('aria-invalid')).toBe('true');
    });

    it('un 400 sin campos muestra el mensaje como error general', async () => {
      const { fixture, s } = await editarRecepcion();
      boton(s, 'Guardar rol').click();
      fallar(`panel/roles/${ROL_RECEPCION.id}`, 400, 'VALIDACION', 'Los permisos no son válidos.', undefined, 'PUT');
      await esperar(fixture);

      expect(s.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Los permisos no son válidos.');
    });

    it('un 409 al eliminar (rol en uso) muestra el mensaje de la API y recarga', async () => {
      const { fixture, el } = await crear();
      const s = seccion(el, 'roles')!;
      // «Gestor de roles» no tiene usuarios en pantalla, así que la pantalla deja intentar el borrado
      s.querySelector<HTMLButtonElement>('button[aria-label="Eliminar rol Gestor de roles"]')!.click();
      fixture.detectChanges();
      boton(s, 'Sí, eliminar').click();
      fallar(`panel/roles/${ROL_SOLO_ROLES.id}`, 409, 'CONFLICTO', 'El rol tiene usuarios asignados.', undefined, 'DELETE');
      await esperar(fixture);

      expect(s.querySelector('[role="alert"]')?.textContent?.trim()).toBe('El rol tiene usuarios asignados.');
      servir('panel/usuarios', [MARTA, ANDRES]);
      servir('panel/roles', [ROL_ADMIN, ROL_RECEPCION, ROL_SOLO_ROLES]);
      await esperar(fixture);
    });

    it('el motivo local de un rol con usuarios sigue siendo el mismo texto', async () => {
      const { el } = await crear();
      const fila = filas(seccion(el, 'roles')!)[0];

      expect(fila.querySelector('.sr-only')?.textContent?.trim()).toBe('No se puede eliminar un rol que tiene usuarios asignados.');
    });
  });

  describe('peticiones en curso', () => {
    it('un doble clic en guardar usuario envía una sola petición, incluso mientras se refresca la sesión', async () => {
      const { fixture, el } = await crear();
      const s = seccion(el, 'usuarios')!;
      s.querySelector<HTMLButtonElement>('button[aria-label="Editar usuario Andrés Lora"]')!.click();
      fixture.detectChanges();
      const guardar = boton(s, 'Guardar usuario');
      guardar.click();
      guardar.click();
      fixture.detectChanges();
      expect(guardar.disabled).toBe(true);

      const puts = http().match(r => r.method === 'PUT');
      expect(puts).toHaveLength(1);
      puts[0].flush(ANDRES);
      await new Promise(r => setTimeout(r)); // la sesión se refresca (GET /auth/yo) y el botón sigue ocupado
      fixture.detectChanges();
      guardar.click();
      expect(http().match(r => r.method === 'PUT')).toHaveLength(0);

      http().expectOne(r => r.url.endsWith('/auth/yo')).flush({ usuario: MARTA, rol: ROL_ADMIN });
      servir('panel/usuarios', [MARTA, ANDRES]);
      servir('panel/roles', [ROL_ADMIN, ROL_RECEPCION, ROL_SOLO_ROLES]);
      await esperar(fixture);
    });

    it('un doble clic en guardar rol envía una sola petición, incluso mientras se refresca la sesión', async () => {
      const { fixture, el } = await crear();
      const s = seccion(el, 'roles')!;
      s.querySelector<HTMLButtonElement>('button[aria-label="Editar rol Recepción"]')!.click();
      fixture.detectChanges();
      const guardar = boton(s, 'Guardar rol');
      guardar.click();
      guardar.click();
      fixture.detectChanges();
      expect(guardar.disabled).toBe(true);

      const puts = http().match(r => r.method === 'PUT');
      expect(puts).toHaveLength(1);
      puts[0].flush(ROL_RECEPCION);
      await new Promise(r => setTimeout(r));
      fixture.detectChanges();
      guardar.click();
      expect(http().match(r => r.method === 'PUT')).toHaveLength(0);

      http().expectOne(r => r.url.endsWith('/auth/yo')).flush({ usuario: MARTA, rol: ROL_ADMIN });
      servir('panel/usuarios', [MARTA, ANDRES]);
      servir('panel/roles', [ROL_ADMIN, ROL_RECEPCION, ROL_SOLO_ROLES]);
      await esperar(fixture);
    });

    it('un 401 al guardar un usuario lleva a /login con aviso, una sola vez, y el botón no queda en «guardando»', async () => {
      const { fixture, el } = await crear();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
      const s = seccion(el, 'usuarios')!;
      s.querySelector<HTMLButtonElement>('button[aria-label="Editar usuario Andrés Lora"]')!.click();
      fixture.detectChanges();
      const guardar = boton(s, 'Guardar usuario');
      guardar.click();
      fallar(`panel/usuarios/${ANDRES.id}`, 401, 'NO_AUTENTICADO', 'Inicia sesión para continuar.', undefined, 'PUT');
      await esperar(fixture);

      expect(navegar.mock.calls.filter(([url]) => url === '/login')).toHaveLength(1);
      expect(ruta()).toBe('/login');
      expect(TestBed.inject(SesionService).aviso()).toBe('Tu sesión venció. Inicia sesión de nuevo.');
      expect(guardar.disabled).toBe(false);
    });

    it('un 401 al guardar un rol lleva a /login con aviso, una sola vez, y el botón no queda en «guardando»', async () => {
      const { fixture, el } = await crear();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
      const s = seccion(el, 'roles')!;
      s.querySelector<HTMLButtonElement>('button[aria-label="Editar rol Recepción"]')!.click();
      fixture.detectChanges();
      const guardar = boton(s, 'Guardar rol');
      guardar.click();
      fallar(`panel/roles/${ROL_RECEPCION.id}`, 401, 'NO_AUTENTICADO', 'Inicia sesión para continuar.', undefined, 'PUT');
      await esperar(fixture);

      expect(navegar.mock.calls.filter(([url]) => url === '/login')).toHaveLength(1);
      expect(ruta()).toBe('/login');
      expect(guardar.disabled).toBe(false);
    });
  });
});
