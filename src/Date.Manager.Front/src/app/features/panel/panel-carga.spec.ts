import { Injectable } from '@angular/core';
import { Cita } from '../../core/models';
import { DemoAgendaRepo } from '../../core/demo-agenda-repo';
import { configurar, crear, esperar, kpi, repo } from './panel.pruebas';

@Injectable()
class RepoLento extends DemoAgendaRepo {
  lento = false;
  readonly retenidas: (() => void)[] = [];

  /** Con `lento`, la respuesta (tomada en el momento de la llamada) se entrega cuando la prueba lo decida. */
  override async citas(): Promise<Cita[]> {
    const instantanea = await super.citas();
    if (!this.lento) return instantanea;
    return new Promise<Cita[]>(resolver => this.retenidas.push(() => resolver(instantanea)));
  }
}

@Injectable()
class RepoQueFalla extends DemoAgendaRepo {
  fallar = true;

  override async citas(): Promise<Cita[]> {
    if (this.fallar) throw new Error('sin conexión');
    return super.citas();
  }
}

describe('PanelComponent: cargas', () => {
  it('descarta una respuesta que llega tarde y deja la más reciente', async () => {
    await configurar({ repo: RepoLento, sinAgenda: true });
    const { fixture, el } = await crear();
    const lento = repo() as RepoLento;
    const [a, b] = (await lento.citas()).filter(c => c.fecha === '2026-11-09' && c.estado === 'Confirmada');
    expect(kpi(el, 'Citas hoy').valor).toBe('2');

    lento.lento = true;
    await lento.cancelarCita(a.id); // carga A: ve 1 cita hoy
    fixture.detectChanges();
    await lento.cancelarCita(b.id); // carga B: ve 0 citas hoy
    fixture.detectChanges();
    await esperar(fixture);
    expect(lento.retenidas.length).toBeGreaterThanOrEqual(2);
    const [cargaA, cargaB] = lento.retenidas.slice(-2);

    cargaB();
    await esperar(fixture);
    expect(kpi(el, 'Citas hoy').valor).toBe('0');

    cargaA(); // la respuesta vieja llega después
    await esperar(fixture);
    expect(kpi(el, 'Citas hoy').valor).toBe('0');
  });

  it('si falla la carga muestra «No se pudo cargar el panel.»', async () => {
    await configurar({ repo: RepoQueFalla, sinAgenda: true });
    const { el } = await crear();
    const alerta = el.querySelector('[role="alert"]');
    expect(alerta?.textContent?.trim()).toBe('No se pudo cargar el panel.');
    expect(el.querySelectorAll('.kpi')).toHaveLength(0);
  });

  it('se recupera cuando una carga posterior sale bien', async () => {
    await configurar({ repo: RepoQueFalla, sinAgenda: true });
    const { fixture, el } = await crear();
    expect(el.querySelector('[role="alert"]')).not.toBeNull();
    (repo() as RepoQueFalla).fallar = false;
    await repo().guardarServicio((await repo().servicios())[0]);
    await esperar(fixture);
    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.querySelectorAll('.kpi')).toHaveLength(6);
  });
});
