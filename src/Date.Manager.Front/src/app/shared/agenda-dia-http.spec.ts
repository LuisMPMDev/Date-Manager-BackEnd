import { TestBed } from '@angular/core/testing';
import { RELOJ } from '../core/errores';
import { Cita, Cliente, Servicio } from '../core/models';
import { MARTA, ROL_ADMIN } from '../core/sesion.pruebas';
import { abrirSesion, configurarHttp, esperar, fallar, http, ruta, servir } from '../testing/http-pantallas';
import { AgendaDiaComponent } from './agenda-dia';

const SIN_PERMISO = 'No tienes permiso para ver esta información.';
const AHORA = new Date(2026, 10, 9, 8, 0);

const CORTE: Servicio = {
  id: 's-corte', nombre: 'Corte de cabello', duracionMin: 30, activo: true, valor: 30000,
  ventanas: [{ id: 'v1', dia: 1, inicio: '09:00', fin: '12:00' }],
};
const CITA: Cita = {
  id: 'c-1', servicioId: 's-corte', clienteId: 'k-1', fecha: '2026-11-09', inicio: '09:30', fin: '10:00',
  estado: 'Confirmada', creadaEn: '2026-11-08T12:00:00Z',
};
const LAURA: Cliente = { id: 'k-1', nombre: 'Laura', apellido: 'Gómez', telefono: '1', correo: 'l@c.com' };

async function crear(lecturas: { servicios?: () => void; citas?: () => void; clientes?: () => void }) {
  configurarHttp([{ provide: RELOJ, useValue: () => new Date(AHORA) }]);
  abrirSesion(MARTA, ROL_ADMIN);
  const fixture = TestBed.createComponent(AgendaDiaComponent);
  fixture.componentRef.setInput('fecha', '2026-11-09');
  fixture.detectChanges();
  (lecturas.servicios ?? (() => servir('panel/servicios', [CORTE])))();
  (lecturas.citas ?? (() => servir('panel/citas', [CITA])))();
  (lecturas.clientes ?? (() => servir('panel/clientes', [LAURA])))();
  await esperar(fixture);
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

describe('AgendaDiaComponent con la API', () => {
  afterEach(() => http().verify());

  it('un 403 en los clientes deja la agenda sin nombres y avisa', async () => {
    const { el } = await crear({ clientes: () => fallar('panel/clientes', 403, 'SIN_PERMISO', 'x') });

    expect(el.querySelectorAll('[data-testid="columna"]')).toHaveLength(1);
    expect(el.textContent).toContain('09:30 · Cliente');
    expect(el.querySelector('[data-aviso="clientes"]')?.textContent?.trim()).toContain(SIN_PERMISO);
  });

  it('otro fallo de los clientes también deja la agenda y avisa con un texto propio', async () => {
    const { el } = await crear({ clientes: () => fallar('panel/clientes', 500, 'ERROR_INTERNO', 'x') });

    expect(el.querySelectorAll('[data-testid="columna"]')).toHaveLength(1);
    expect(el.querySelector('[data-aviso="clientes"]')?.textContent?.trim()).toContain('No se pudieron cargar los nombres de los clientes.');
  });

  it('un 403 en los servicios o las citas (la llamada principal) manda a /sin-acceso', async () => {
    await crear({ citas: () => fallar('panel/citas', 403, 'SIN_PERMISO', 'x') });

    expect(ruta()).toBe('/sin-acceso');
  });

  it('otro fallo de la llamada principal muestra un error en vez de quedarse en blanco', async () => {
    const { el } = await crear({ servicios: () => fallar('panel/servicios', 500, 'ERROR_INTERNO', 'x') });

    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('No se pudo cargar la agenda.');
  });
});
