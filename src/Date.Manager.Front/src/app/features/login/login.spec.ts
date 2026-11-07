import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { DemoAgendaRepo } from '../../core/demo-agenda-repo';
import { RELOJ } from '../../core/errores';
import { SesionService } from '../../core/sesion';
import { LoginComponent } from './login';

const AHORA = new Date(2026, 10, 9, 8, 0);

describe('LoginComponent', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: RELOJ, useValue: () => AHORA },
        { provide: AgendaRepo, useClass: DemoAgendaRepo },
      ],
    }).compileComponents();
  });

  function crear() {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function escribir(el: HTMLElement, selector: string, valor: string) {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  it('el título y el botón de envío dicen «Iniciar sesión»', () => {
    const { el } = crear();
    expect(el.querySelector('h1')!.textContent?.trim()).toBe('Iniciar sesión');
    expect(el.querySelector('form button[type="submit"]')!.textContent?.trim()).toBe('Iniciar sesión');
    expect(el.textContent).not.toContain('Entrar');
  });

  it('credenciales incorrectas muestran «Usuario o clave incorrectos.»', async () => {
    const { fixture, el } = crear();
    escribir(el, 'input[name="usuario"]', 'marta');
    escribir(el, 'input[name="clave"]', 'mala');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Usuario o clave incorrectos.');
    expect(TestBed.inject(SesionService).usuario()).toBeNull();
  });

  it('entrar con una tarjeta de cuenta de ejemplo inicia sesión y navega a la primera ruta del rol', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, el } = crear();
    const tarjetas = Array.from(el.querySelectorAll<HTMLButtonElement>('button.tarjeta'));
    expect(tarjetas.some(b => b.textContent?.includes('Marta · Administrador'))).toBe(true);
    const andres = tarjetas.find(b => b.textContent?.includes('Andrés · Recepción'))!;
    andres.click();
    await fixture.whenStable();
    expect(TestBed.inject(SesionService).usuario()?.usuario).toBe('andres');
    expect(navegar).toHaveBeenCalledWith('/agenda');
  });

  it('el formulario con credenciales correctas también inicia sesión', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, el } = crear();
    escribir(el, 'input[name="usuario"]', 'marta');
    escribir(el, 'input[name="clave"]', 'demo123');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(navegar).toHaveBeenCalledWith('/panel');
  });
});
