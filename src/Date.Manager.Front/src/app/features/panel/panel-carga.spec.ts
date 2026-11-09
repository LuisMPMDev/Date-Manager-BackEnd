import { ApiError } from '../../core/api-error';
import { Inicio } from '../../core/inicio';
import { api, configurar, crear, esperar, INICIO, kpi, repo } from './panel.pruebas';

const AVISO_403 = 'No tienes permiso para ver esta información.';

describe('PanelComponent: cargas', () => {
  beforeEach(async () => {
    await configurar({ sinAgenda: true });
  });

  it('descarta una respuesta que llega tarde y deja la más reciente', async () => {
    const { fixture, el } = await crear();
    expect(kpi(el, 'Citas hoy').valor).toBe('4');

    const retenidas: ((i: Inicio) => void)[] = [];
    api().respuesta = () => new Promise<Inicio>(resolver => retenidas.push(resolver));
    const servicio = (await repo().servicios())[0];
    await repo().guardarServicio(servicio); // carga A
    fixture.detectChanges();
    await repo().guardarServicio(servicio); // carga B
    fixture.detectChanges();
    await esperar(fixture);
    expect(retenidas).toHaveLength(2);
    const [cargaA, cargaB] = retenidas;

    cargaB({ ...INICIO, citasHoy: { total: 7, atendidas: 0 } });
    await esperar(fixture);
    expect(kpi(el, 'Citas hoy').valor).toBe('7');

    cargaA({ ...INICIO, citasHoy: { total: 1, atendidas: 0 } }); // la respuesta vieja llega después
    await esperar(fixture);
    expect(kpi(el, 'Citas hoy').valor).toBe('7');
  });

  it('si falla la carga muestra «No se pudo cargar el panel.»', async () => {
    const { el } = await crear(() => Promise.reject(new ApiError(0, 'ERROR_RED', 'sin conexión')));
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('No se pudo cargar el panel.');
    expect(el.querySelectorAll('.kpi')).toHaveLength(0);
  });

  it('se recupera cuando una carga posterior sale bien', async () => {
    const { fixture, el } = await crear(() => Promise.reject(new ApiError(500, 'ERROR_INTERNO', 'falló')));
    expect(el.querySelector('[role="alert"]')).not.toBeNull();
    api().respuesta = () => Promise.resolve(INICIO);
    await repo().guardarServicio((await repo().servicios())[0]);
    await esperar(fixture);
    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.querySelectorAll('.kpi')).toHaveLength(6);
  });

  it('un 403 muestra el aviso de «sin permiso» dentro del tablero y no rompe', async () => {
    const { el } = await crear(() => Promise.reject(new ApiError(403, 'SIN_PERMISO', 'No tienes permiso para esta acción.')));
    const aviso = el.querySelector('[role="alert"]');
    expect(aviso?.textContent?.trim()).toBe(AVISO_403);
    expect(el.textContent).not.toContain('No se pudo cargar el panel.');
    expect(el.querySelector('h2')).not.toBeNull();
    expect(el.querySelectorAll('.kpi')).toHaveLength(0);
  });
});
