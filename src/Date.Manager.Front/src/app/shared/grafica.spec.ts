import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FABRICA_GRAFICA, GraficaComponent, GraficaMotor } from './grafica';

type Doble = GraficaMotor & { canvas: HTMLCanvasElement; config: any; update: ReturnType<typeof vi.fn>; destroy: ReturnType<typeof vi.fn> };

function crearFabrica(retraso?: Promise<void>) {
  const creadas: Doble[] = [];
  const fabrica = vi.fn((canvas: HTMLCanvasElement, config: any) => {
    const grafica = { canvas, config, data: config.data, update: vi.fn(), destroy: vi.fn() } as Doble;
    creadas.push(grafica);
    return retraso ? retraso.then(() => grafica) : grafica;
  });
  return { fabrica, creadas };
}

@Component({
  imports: [GraficaComponent],
  template: `<app-grafica [tipo]="tipo()" [etiquetas]="etiquetas()" [valores]="valores()" [resumen]="resumen()" unidad="%" />`,
})
class Anfitrion {
  tipo = signal<'barras' | 'barras-h'>('barras');
  etiquetas = signal(['Lun', 'Mar']);
  valores = signal([3, 5]);
  resumen = signal('Citas por día: 8 en total.');
}

async function montar(retraso?: Promise<void>) {
  const { fabrica, creadas } = crearFabrica(retraso);
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [Anfitrion],
    providers: [{ provide: FABRICA_GRAFICA, useValue: fabrica }],
  }).compileComponents();
  const fixture = TestBed.createComponent(Anfitrion);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement, fabrica, creadas };
}

describe('GraficaComponent', () => {
  afterEach(() => {
    delete (window as any).matchMedia;
  });

  it('crea un gráfico de barras sobre el canvas con las etiquetas y los valores', async () => {
    const { el, fabrica, creadas } = await montar();
    expect(fabrica).toHaveBeenCalledTimes(1);
    const [g] = creadas;
    expect(g.canvas).toBe(el.querySelector('canvas'));
    expect(g.config.type).toBe('bar');
    expect(g.config.data.labels).toEqual(['Lun', 'Mar']);
    expect(g.config.data.datasets[0].data).toEqual([3, 5]);
    expect(g.config.options.indexAxis).toBe('x');
  });

  it('«barras-h» dibuja las barras en horizontal', async () => {
    const { fixture, fabrica, creadas } = await montar();
    fixture.componentInstance.tipo.set('barras-h');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    // cambiar el tipo obliga a crear de nuevo el gráfico: el primero se destruye
    expect(fabrica).toHaveBeenCalledTimes(2);
    expect(creadas[0].destroy).toHaveBeenCalledTimes(1);
    expect(creadas[1].config.options.indexAxis).toBe('y');
  });

  it('al cambiar los datos actualiza el mismo gráfico en vez de crear otro', async () => {
    const { fixture, fabrica, creadas } = await montar();
    fixture.componentInstance.etiquetas.set(['Lun', 'Mar', 'Mié']);
    fixture.componentInstance.valores.set([3, 5, 9]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fabrica).toHaveBeenCalledTimes(1);
    expect(creadas[0].data.labels).toEqual(['Lun', 'Mar', 'Mié']);
    expect(creadas[0].data.datasets[0].data).toEqual([3, 5, 9]);
    expect(creadas[0].update).toHaveBeenCalled();
    expect(creadas[0].destroy).not.toHaveBeenCalled();
  });

  it('destruye el gráfico al salir', async () => {
    const { fixture, creadas } = await montar();
    fixture.destroy();
    expect(creadas[0].destroy).toHaveBeenCalledTimes(1);
  });

  it('si se destruye antes de que el gráfico esté listo, lo destruye en cuanto llega', async () => {
    let liberar!: () => void;
    const espera = new Promise<void>(r => (liberar = r));
    const { fixture, creadas } = await montar(espera);
    fixture.destroy();
    liberar();
    await espera;
    await Promise.resolve();
    expect(creadas[0].destroy).toHaveBeenCalledTimes(1);
  });

  it('ofrece el resumen y los valores como texto, no solo el dibujo', async () => {
    const { el } = await montar();
    expect(el.querySelector('canvas')!.getAttribute('aria-hidden')).toBe('true');
    const alternativa = el.querySelector('.sr-only')!;
    expect(alternativa.textContent).toContain('Citas por día: 8 en total.');
    const filas = Array.from(alternativa.querySelectorAll('tbody tr')).map(f =>
      Array.from(f.children).map(c => c.textContent?.trim()),
    );
    expect(filas).toEqual([['Lun', '3%'], ['Mar', '5%']]);
  });

  it('actualiza el texto alternativo cuando cambian los datos', async () => {
    const { fixture, el } = await montar();
    fixture.componentInstance.resumen.set('Otro resumen.');
    fixture.componentInstance.valores.set([1, 2]);
    fixture.detectChanges();
    expect(el.querySelector('.sr-only')!.textContent).toContain('Otro resumen.');
    expect(el.querySelector('.sr-only tbody tr td')!.textContent?.trim()).toBe('1%');
  });

  it('con «reducir movimiento» desactiva la animación', async () => {
    (window as any).matchMedia = (q: string) => ({ matches: q.includes('reduce') });
    const { creadas } = await montar();
    expect(creadas[0].config.options.animation).toBe(false);
  });

  it('sin esa preferencia no toca la animación', async () => {
    (window as any).matchMedia = () => ({ matches: false });
    const { creadas } = await montar();
    expect(creadas[0].config.options.animation).toBeUndefined();
  });
});
