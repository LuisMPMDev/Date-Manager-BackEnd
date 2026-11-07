import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AgendaRepo } from '../core/agenda-repo';
import { DemoAgendaRepo } from '../core/demo-agenda-repo';
import { RELOJ } from '../core/errores';
import { sembrar } from '../core/seed';
import { SesionService } from '../core/sesion';
import { PanelShell } from './panel-shell';

const AHORA = new Date(2026, 10, 9, 8, 0);

describe('PanelShell', () => {
  const datos = sembrar(AHORA);

  let fixture: ComponentFixture<PanelShell>;

  function crear(usuario: string) {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [PanelShell],
      providers: [
        provideRouter([]),
        { provide: RELOJ, useValue: () => AHORA },
        { provide: AgendaRepo, useClass: DemoAgendaRepo },
      ],
    });
    TestBed.inject(SesionService).iniciar(datos.usuarios.find(u => u.usuario === usuario)!, datos.roles);
    fixture = TestBed.createComponent(PanelShell);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const enlaces = (el: HTMLElement) => Array.from(el.querySelectorAll('nav a')).map(a => a.textContent?.trim());

  it('el administrador ve todo el menú', () => {
    expect(enlaces(crear('marta'))).toEqual(['Panel', 'Agenda', 'Citas', 'Servicios', 'Clientes', 'Usuarios y roles']);
  });

  describe('«Reiniciar demo»', () => {
    const boton = (el: HTMLElement, texto: string) =>
      Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(b => b.textContent?.trim() === texto);
    const esperar = async () => {
      await new Promise(r => setTimeout(r));
      fixture.detectChanges();
    };
    const valorConsulta = async () =>
      (await TestBed.inject(AgendaRepo).servicios()).find(s => s.id === 'srv-consulta')!.valor;

    async function conCambios() {
      const el = crear('marta');
      const repo = TestBed.inject(AgendaRepo);
      const consulta = (await repo.servicios()).find(s => s.id === 'srv-consulta')!;
      await repo.guardarServicio({ ...consulta, valor: 1 });
      vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      return el;
    }

    it('pide confirmación antes de borrar y no toca los datos', async () => {
      const el = await conCambios();
      boton(el, 'Reiniciar demo')!.click();
      await esperar();
      expect(el.textContent).toContain('¿Volver a los datos de ejemplo?');
      expect(boton(el, 'Reiniciar demo')).toBeUndefined();
      expect(await valorConsulta()).toBe(1);
    });

    it('«No, volver» cancela y deja los cambios', async () => {
      const el = await conCambios();
      boton(el, 'Reiniciar demo')!.click();
      await esperar();
      boton(el, 'No, volver')!.click();
      await esperar();
      expect(el.textContent).not.toContain('¿Volver a los datos de ejemplo?');
      expect(boton(el, 'Reiniciar demo')).toBeDefined();
      expect(await valorConsulta()).toBe(1);
    });

    it('«Sí, reiniciar» restaura los datos de ejemplo y conserva la sesión', async () => {
      const el = await conCambios();
      boton(el, 'Reiniciar demo')!.click();
      await esperar();
      boton(el, 'Sí, reiniciar')!.click();
      await esperar();
      await esperar();
      expect(await valorConsulta()).toBe(40000);
      expect(TestBed.inject(SesionService).usuario()?.usuario).toBe('marta');
      expect(el.textContent).not.toContain('¿Volver a los datos de ejemplo?');
      expect(boton(el, 'Reiniciar demo')).toBeDefined();
    });
  });

  it('recepción solo ve lo que su rol permite', () => {
    const el = crear('andres');
    expect(enlaces(el)).toEqual(['Agenda', 'Citas', 'Clientes']);
    expect(el.textContent).toContain('Recepción');
  });
});
