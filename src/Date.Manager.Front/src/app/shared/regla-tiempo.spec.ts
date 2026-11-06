import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BloqueRegla, HuecoRegla, PX_POR_MIN, ReglaTiempoComponent } from './regla-tiempo';

@Component({
  imports: [ReglaTiempoComponent],
  template: `<app-regla-tiempo [desdeMin]="540" [hastaMin]="780" [bloques]="bloques" [huecos]="huecos"
    [ahoraMin]="ahora()" (elegir)="elegido = $event" />`,
})
class Anfitrion {
  bloques: BloqueRegla[] = [
    { desdeMin: 540, hastaMin: 600, tipo: 'ocupado', etiqueta: 'Ocupado' },
    { desdeMin: 600, hastaMin: 630, tipo: 'elegido', etiqueta: '10:00' },
    { desdeMin: 660, hastaMin: 720, tipo: 'fuera' },
    { desdeMin: 720, hastaMin: 780, tipo: 'atendida' },
  ];
  huecos: HuecoRegla[] = [
    { desdeMin: 630, hastaMin: 660, etiqueta: '10:30' },
    { desdeMin: 600, hastaMin: 630, etiqueta: '10:00' },
  ];
  ahora = signal<number | null>(650);
  elegido: number | null = null;
}

describe('ReglaTiempoComponent', () => {
  function crear() {
    const fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  // jsdom no calcula el layout: se lee el alto que el componente fija en línea.
  const alto = (e: Element) => parseFloat((e as HTMLElement).style.height);

  it('un bloque de 60 min mide el doble que uno de 30 min (misma escala)', () => {
    const { el } = crear();
    const bloques = Array.from(el.querySelectorAll('[data-testid="bloque"]'));
    const de60 = bloques[0];
    const de30 = bloques[1];
    expect(alto(de30)).toBeCloseTo(30 * PX_POR_MIN, 1);
    expect(alto(de60)).toBeCloseTo(2 * alto(de30), 1);
  });

  it('los bloques ocupado y fuera llevan la clase rayado; los demás no', () => {
    const { el } = crear();
    const [ocupado, elegido, fuera, atendida] = Array.from(el.querySelectorAll('[data-testid="bloque"]'));
    expect(ocupado.classList.contains('rayado')).toBe(true);
    expect(fuera.classList.contains('rayado')).toBe(true);
    expect(elegido.classList.contains('rayado')).toBe(false);
    expect(atendida.classList.contains('rayado')).toBe(false);
  });

  it('los bloques se posicionan por minutos desde el inicio de la regla', () => {
    const { el } = crear();
    const elegido = el.querySelectorAll<HTMLElement>('[data-testid="bloque"]')[1];
    expect(parseFloat(elegido.style.top)).toBeCloseTo((600 - 540) * PX_POR_MIN, 1);
  });

  it('dibuja una marca por hora desde el inicio hasta el final del rango', () => {
    const { el } = crear();
    const marcas = Array.from(el.querySelectorAll('.marca')).map(m => m.textContent?.trim());
    expect(marcas).toEqual(['09:00', '10:00', '11:00', '12:00', '13:00']);
  });

  it('dibuja la línea de ahora solo si cae dentro del rango', () => {
    const { fixture, el } = crear();
    expect(el.querySelector('.ahora')).not.toBeNull();
    fixture.componentInstance.ahora.set(900);
    fixture.detectChanges();
    expect(el.querySelector('.ahora')).toBeNull();
  });

  it('los huecos son botones y emiten su hora de inicio al pulsarlos', () => {
    const { el, fixture } = crear();
    const huecos = Array.from(el.querySelectorAll<HTMLButtonElement>('button[data-testid="hueco"]'));
    expect(huecos.map(h => h.textContent?.trim())).toEqual(['10:30', '10:00']);
    huecos[0].click();
    expect(fixture.componentInstance.elegido).toBe(630);
  });

  it('el hueco cuyo inicio coincide con el bloque elegido queda marcado como pulsado', () => {
    const { el } = crear();
    const huecos = Array.from(el.querySelectorAll<HTMLButtonElement>('button[data-testid="hueco"]'));
    expect(huecos.map(h => h.getAttribute('aria-pressed'))).toEqual(['false', 'true']);
  });
});
