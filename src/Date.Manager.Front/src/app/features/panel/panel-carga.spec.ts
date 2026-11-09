import { ApiError } from '../../core/api-error';
import { Inicio } from '../../core/inicio';
import { INICIO } from '../../core/inicio.pruebas';
import { api, configurar, crear, esperar, kpi, repo } from './panel.pruebas';

const AVISO_403 = 'No tienes permiso para ver esta información.';
const AVISO_RECARGA = 'No se pudo actualizar el panel.';

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
  describe('una recarga posterior', () => {
    /** Fuerza una recarga (sube `cambios`) y deja que termine. */
    const recargar = async (fixture: Parameters<typeof esperar>[0]) => {
      await repo().guardarServicio((await repo().servicios())[0]);
      await esperar(fixture);
    };

    it('si falla, conserva las cifras anteriores y avisa sin bloquear', async () => {
      const { fixture, el } = await crear();
      api().respuesta = () => Promise.reject(new ApiError(0, 'ERROR_RED', 'sin conexión'));
      await recargar(fixture);

      expect(kpi(el, 'Citas hoy').valor).toBe('4');
      expect(el.querySelectorAll('.kpi')).toHaveLength(6);
      expect(el.querySelector('[role="alert"]')).toBeNull();
      expect(el.querySelector('[role="status"]')?.textContent?.trim()).toBe(AVISO_RECARGA);
    });

    it('si responde 403, vacía el tablero y muestra el aviso de «sin permiso»', async () => {
      const { fixture, el } = await crear();
      api().respuesta = () => Promise.reject(new ApiError(403, 'SIN_PERMISO', 'No tienes permiso para esta acción.'));
      await recargar(fixture);

      expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe(AVISO_403);
      expect(el.querySelectorAll('.kpi')).toHaveLength(0);
      expect(el.querySelector('[role="status"]')).toBeNull();
    });

    it('la siguiente recarga correcta actualiza las cifras y quita el aviso', async () => {
      const { fixture, el } = await crear();
      api().respuesta = () => Promise.reject(new ApiError(500, 'ERROR_INTERNO', 'falló'));
      await recargar(fixture);
      expect(el.querySelector('[role="status"]')).not.toBeNull();

      api().respuesta = () => Promise.resolve({ ...INICIO, citasHoy: { total: 9, atendidas: 2 } });
      await recargar(fixture);
      expect(kpi(el, 'Citas hoy')).toEqual({ valor: '9', detalle: '2 ya atendidas' });
      expect(el.querySelector('[role="status"]')).toBeNull();
      expect(el.querySelector('[role="alert"]')).toBeNull();
    });

    it('tras un 403 la siguiente recarga correcta restaura el tablero', async () => {
      const { fixture, el } = await crear();
      api().respuesta = () => Promise.reject(new ApiError(403, 'SIN_PERMISO', 'No tienes permiso para esta acción.'));
      await recargar(fixture);
      api().respuesta = () => Promise.resolve(INICIO);
      await recargar(fixture);
      expect(el.querySelectorAll('.kpi')).toHaveLength(6);
      expect(el.querySelector('[role="alert"]')).toBeNull();
    });
  });
});
