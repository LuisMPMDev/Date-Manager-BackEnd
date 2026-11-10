import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgendaRepo } from '../../core/agenda-repo';
import { DemoAgendaRepo } from '../../testing/demo-agenda-repo';
import { RELOJ } from '../../core/reloj';
import { AgendaComponent } from './agenda';

const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00

describe('AgendaComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [AgendaComponent],
      providers: [
        { provide: RELOJ, useValue: () => AHORA },
        { provide: AgendaRepo, useClass: DemoAgendaRepo },
      ],
    }).compileComponents();
  });

  async function esperar(fixture: ComponentFixture<unknown>) {
    await new Promise(r => setTimeout(r));
    fixture.detectChanges();
  }

  async function crear() {
    const fixture = TestBed.createComponent(AgendaComponent);
    fixture.detectChanges();
    await esperar(fixture);
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const boton = (el: HTMLElement, texto: string) =>
    Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(
      b => b.textContent?.trim() === texto || b.getAttribute('aria-label') === texto,
    )!;
  const columnas = (el: HTMLElement) => el.querySelectorAll('[data-testid="columna"]').length;
  const dia = (el: HTMLElement) => el.querySelector('[data-testid="dia"]')!.textContent?.trim();

  it('abre en el día de hoy con una columna por servicio activo', async () => {
    const { el } = await crear();
    expect(el.querySelector('h2')!.textContent).toContain('Agenda');
    expect(dia(el)).toBe('Lunes 9 de noviembre');
    expect(columnas(el)).toBe(3);
    expect((el.querySelector('input[type="date"]') as HTMLInputElement).value).toBe('2026-11-09');
  });

  it('las flechas cambian de día', async () => {
    const { fixture, el } = await crear();
    boton(el, 'Día siguiente').click();
    await esperar(fixture);
    expect(dia(el)).toBe('Martes 10 de noviembre');
    expect((el.querySelector('input[type="date"]') as HTMLInputElement).value).toBe('2026-11-10');

    boton(el, 'Día anterior').click();
    boton(el, 'Día anterior').click();
    await esperar(fixture);
    expect(dia(el)).toBe('Domingo 8 de noviembre');
    expect(el.textContent).toContain('Ningún servicio atiende este día.');
  });

  it('elegir una fecha en el selector cambia el día', async () => {
    const { fixture, el } = await crear();
    const campo = el.querySelector<HTMLInputElement>('input[type="date"]')!;
    campo.value = '2026-11-14';
    campo.dispatchEvent(new Event('change'));
    await esperar(fixture);
    expect(dia(el)).toBe('Sábado 14 de noviembre');
  });

  it('«Hoy» vuelve al día actual', async () => {
    const { fixture, el } = await crear();
    boton(el, 'Día siguiente').click();
    await esperar(fixture);
    boton(el, 'Hoy').click();
    await esperar(fixture);
    expect(dia(el)).toBe('Lunes 9 de noviembre');
  });
});
