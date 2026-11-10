import { TestBed } from '@angular/core/testing';
import { RELOJ } from '../../core/reloj';
import { Cita, Rol, Servicio } from '../../core/models';
import { INICIO } from '../../core/inicio.pruebas';
import { MARTA } from '../../core/sesion.pruebas';
import { FABRICA_GRAFICA } from '../../shared/grafica';
import { abrirSesion, configurarHttp, esperar, fallar, http, servir } from '../../testing/http-pantallas';
import { PanelComponent } from './panel';

const SIN_PERMISO = 'No tienes permiso para ver esta información.';
const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00
const SOLO_PANEL: Rol = { id: 'r-panel', nombre: 'Solo panel', descripcion: 'Ve el panel', permisos: ['ver_panel'] };

const CORTE: Servicio = {
  id: 's-corte', nombre: 'Corte de cabello', duracionMin: 30, activo: true, valor: 30000,
  ventanas: [{ id: 'v1', dia: 1, inicio: '09:00', fin: '12:00' }],
};
const CITA: Cita = {
  id: 'c-1', servicioId: 's-corte', clienteId: 'k-1', fecha: '2026-11-09', inicio: '09:30', fin: '10:00',
  estado: 'Confirmada', creadaEn: '2026-11-08T12:00:00Z',
};

/** Panel de un rol con solo `ver_panel`: el tablero y la agenda se leen sin problema; los clientes no. */
async function crear(clientes: () => void) {
  configurarHttp([
    { provide: RELOJ, useValue: () => new Date(AHORA) },
    {
      provide: FABRICA_GRAFICA,
      useValue: (canvas: HTMLCanvasElement, config: { data: unknown }) =>
        ({ canvas, config, data: config.data, update: () => undefined, destroy: () => undefined }),
    },
  ]);
  abrirSesion(MARTA, SOLO_PANEL);
  const fixture = TestBed.createComponent(PanelComponent);
  fixture.detectChanges();
  servir('panel/inicio', INICIO);
  await esperar(fixture); // el tablero ya se ve y la agenda del día pide sus datos
  servir('panel/servicios', [CORTE]);
  servir('panel/citas', [CITA]);
  clientes();
  await esperar(fixture);
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

describe('PanelComponent con la API: permisos auxiliares', () => {
  afterEach(() => http().verify());

  it('un 403 en GET /panel/clientes muestra el tablero y la agenda sin nombres de cliente, más el aviso', async () => {
    const { el } = await crear(() => fallar('panel/clientes', 403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción.'));

    expect(el.querySelectorAll('.kpi')).toHaveLength(6);
    expect(el.querySelectorAll('.proximas tbody tr[data-cita]')).toHaveLength(2);
    const agenda = el.querySelector('app-agenda-dia')!;
    expect(agenda.querySelectorAll('[data-testid="columna"]')).toHaveLength(1);
    expect(agenda.textContent).toContain('09:30 · Cliente');
    expect(agenda.querySelector('[data-aviso="clientes"]')?.textContent?.trim()).toContain(SIN_PERMISO);
    expect(el.querySelector('.error[role="alert"]')).toBeNull(); // el panel no se cae: solo avisa en la agenda
  });

  it('con permiso para leer los clientes la agenda muestra el nombre y no hay aviso', async () => {
    const { el } = await crear(() =>
      servir('panel/clientes', [{ id: 'k-1', nombre: 'Laura', apellido: 'Gómez', telefono: '1', correo: 'l@c.com' }]));

    const agenda = el.querySelector('app-agenda-dia')!;
    expect(agenda.textContent).toContain('09:30 · Laura G.');
    expect(agenda.querySelector('[data-aviso="clientes"]')).toBeNull();
  });
});
