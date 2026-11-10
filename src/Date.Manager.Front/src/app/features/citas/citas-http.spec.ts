import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { RELOJ } from '../../core/reloj';
import { Cita, Cliente, Servicio } from '../../core/models';
import { SesionService } from '../../core/sesion';
import { MARTA, ROL_ADMIN } from '../../core/sesion.pruebas';
import { abrirSesion, configurarHttp, esperar, fallar, http, ruta, servir } from '../../testing/http-pantallas';
import { CitasComponent } from './citas';
import { MoverCitaComponent } from './mover-cita';

const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00

const SERVICIO: Servicio = {
  id: 's1', nombre: 'Consulta', duracionMin: 30, activo: true, valor: 40000,
  ventanas: [1, 2, 3, 4, 5, 6, 7].map(dia => ({ id: `v${dia}`, dia: dia as 1, inicio: '09:00', fin: '12:00' })),
};
const CLIENTE: Cliente = { id: 'k1', nombre: 'Laura', apellido: 'Gómez', telefono: '300 111', correo: 'laura@correo.com' };
const FUTURA: Cita = { id: 'c1', servicioId: 's1', clienteId: 'k1', fecha: '2026-11-10', inicio: '10:00', fin: '10:30', estado: 'Confirmada', creadaEn: '2026-11-09T12:00:00Z' };
const EMPEZADA: Cita = { id: 'c2', servicioId: 's1', clienteId: 'k1', fecha: '2026-11-09', inicio: '07:00', fin: '07:30', estado: 'Confirmada', creadaEn: '2026-11-08T12:00:00Z' };

function lecturas(citas: Cita[] = [FUTURA, EMPEZADA]): void {
  servir('panel/citas', citas);
  servir('panel/servicios', [SERVICIO]);
  servir('panel/clientes', [CLIENTE]);
}

async function crear(citas: () => void = () => lecturas()) {
  configurarHttp([{ provide: RELOJ, useValue: () => AHORA }]);
  abrirSesion(MARTA, ROL_ADMIN);
  const fixture = TestBed.createComponent(CitasComponent);
  fixture.detectChanges();
  citas();
  await esperar(fixture);
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

const fila = (el: HTMLElement, id: string) => el.querySelector<HTMLElement>(`tr[data-cita="${id}"]`)!;
const boton = (c: HTMLElement, texto: string) =>
  Array.from(c.querySelectorAll<HTMLButtonElement>('button')).find(b => b.textContent?.trim() === texto)!;
const alerta = (el: HTMLElement) => el.querySelector('[role="alert"]')?.textContent?.trim() ?? '';

describe('CitasComponent con la API', () => {
  afterEach(() => http().verify());

  it('un 403 en la llamada principal (citas) manda a /sin-acceso', async () => {
    await crear(() => {
      fallar('panel/citas', 403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción.');
      servir('panel/servicios', [SERVICIO]);
      servir('panel/clientes', [CLIENTE]);
    });

    expect(ruta()).toBe('/sin-acceso');
  });

  it('otro fallo de carga muestra un error en vez de dejar la pantalla vacía en silencio', async () => {
    const { el } = await crear(() => {
      fallar('panel/citas', 500, 'ERROR_INTERNO', 'Error del servidor.');
      servir('panel/servicios', [SERVICIO]);
      servir('panel/clientes', [CLIENTE]);
    });

    expect(alerta(el)).toBe('No se pudieron cargar las citas.');
  });

  describe('cancelar', () => {
    async function pedirCancelacion(fixture: { detectChanges(): void }, el: HTMLElement) {
      boton(fila(el, 'c1'), 'Cancelar cita').click();
      fixture.detectChanges();
      return boton(fila(el, 'c1'), 'Sí, cancelar');
    }

    it('un doble clic en «Sí, cancelar» envía una sola petición y el botón queda deshabilitado', async () => {
      const { fixture, el } = await crear();
      const si = await pedirCancelacion(fixture, el);
      si.click();
      si.click();
      fixture.detectChanges();

      expect(http().match(r => r.method === 'POST')).toHaveLength(1);
      expect(si.disabled).toBe(true);
    });

    it('un 409 ESTADO_INVALIDO muestra su mensaje y recarga las citas', async () => {
      const { fixture, el } = await crear();
      const si = await pedirCancelacion(fixture, el);
      si.click();
      fallar('panel/citas/c1/cancelar', 409, 'ESTADO_INVALIDO', 'La cita ya fue atendida.', undefined, 'POST');
      await esperar(fixture);

      expect(alerta(el)).toBe('La cita ya fue atendida.');
      lecturas([{ ...FUTURA, estado: 'Atendida' }, EMPEZADA]); // la pantalla volvió a pedir los datos
      await esperar(fixture);
      expect(fila(el, 'c1').textContent).toContain('Atendida');
    });

    it('un 401 lleva a /login con aviso, una sola vez, y los botones no quedan en «guardando»', async () => {
      const { fixture, el } = await crear();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
      const si = await pedirCancelacion(fixture, el);
      si.click();
      fallar('panel/citas/c1/cancelar', 401, 'NO_AUTENTICADO', 'Inicia sesión para continuar.', undefined, 'POST');
      await esperar(fixture);

      expect(navegar.mock.calls.filter(([url]) => url === '/login')).toHaveLength(1);
      expect(ruta()).toBe('/login');
      expect(TestBed.inject(SesionService).aviso()).toBe('Tu sesión venció. Inicia sesión de nuevo.');
      expect(el.querySelectorAll<HTMLButtonElement>('tbody button:disabled')).toHaveLength(0);
    });
  });

  describe('atender', () => {
    it('un doble clic en «Marcar como atendida» envía una sola petición', async () => {
      const { fixture, el } = await crear();
      const b = boton(fila(el, 'c2'), 'Marcar como atendida');
      b.click();
      b.click();
      fixture.detectChanges();

      expect(http().match(r => r.method === 'POST')).toHaveLength(1);
      expect(b.disabled).toBe(true);
    });

    it('un 409 CITA_NO_HA_INICIADO muestra su mensaje, recarga y libera el botón', async () => {
      const { fixture, el } = await crear();
      const b = boton(fila(el, 'c2'), 'Marcar como atendida');
      b.click();
      fallar('panel/citas/c2/atender', 409, 'CITA_NO_HA_INICIADO', 'La cita todavía no ha iniciado.', undefined, 'POST');
      await esperar(fixture);

      expect(alerta(el)).toBe('La cita todavía no ha iniciado.');
      expect(b.disabled).toBe(false);
      lecturas();
      await esperar(fixture);
    });
  });

  describe('mover', () => {
    const HORARIOS = 'panel/servicios/s1/horarios';

    async function abrirMover() {
      const { fixture, el } = await crear();
      boton(fila(el, 'c1'), 'Mover cita').click();
      fixture.detectChanges();
      servir(HORARIOS, ['10:00', '10:30', '11:00']);
      await esperar(fixture);
      const dialogo = el.querySelector<HTMLElement>('app-mover-cita')!;
      dialogo.querySelector<HTMLButtonElement>('[data-testid="hueco"]')!.click();
      fixture.detectChanges();
      return { fixture, el, dialogo, mover: boton(dialogo, 'Mover cita') };
    }

    it('un doble clic en «Mover cita» envía una sola petición y el botón queda deshabilitado', async () => {
      const { fixture, mover } = await abrirMover();
      mover.click();
      mover.click();
      fixture.detectChanges();

      expect(http().match(r => r.method === 'POST')).toHaveLength(1);
      expect(mover.disabled).toBe(true);
    });

    it('un 409 HORARIO_NO_DISPONIBLE muestra el mensaje de la API, recarga los horarios y la lista', async () => {
      const { fixture, dialogo, mover } = await abrirMover();
      mover.click();
      fallar('panel/citas/c1/mover', 409, 'HORARIO_NO_DISPONIBLE', 'Ese horario ya no está disponible.', undefined, 'POST');
      await esperar(fixture);

      expect(alerta(dialogo)).toBe('Ese horario ya no está disponible.');
      expect(mover.disabled).toBe(true); // sin hora elegida hasta que escoja otra
      servir(HORARIOS, ['10:30', '11:00']);
      lecturas();
      await esperar(fixture);
    });

    it('un error que no es de la API usa el mensaje genérico', async () => {
      const { fixture, dialogo, mover } = await abrirMover();
      mover.click();
      http().expectOne(r => r.method === 'POST').error(new ProgressEvent('error'));
      await esperar(fixture);

      expect(alerta(dialogo)).toContain('No se pudo contactar al servidor');
      servir(HORARIOS, ['10:00', '10:30', '11:00']);
      await esperar(fixture);
    });

    it('un 401 lleva a /login con aviso, una sola vez, y el botón no queda en «moviendo»', async () => {
      const { fixture, mover } = await abrirMover();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
      mover.click();
      fallar('panel/citas/c1/mover', 401, 'NO_AUTENTICADO', 'Inicia sesión para continuar.', undefined, 'POST');
      await esperar(fixture);
      servir(HORARIOS, ['10:00']); // tras el error la pantalla vuelve a pedir los horarios
      await esperar(fixture);

      expect(navegar.mock.calls.filter(([url]) => url === '/login')).toHaveLength(1);
      expect(ruta()).toBe('/login');
      const dialogo = fixture.debugElement.query(By.directive(MoverCitaComponent)).componentInstance as { moviendo(): boolean };
      expect(dialogo.moviendo()).toBe(false);
    });
  });
});
