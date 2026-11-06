import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AgendaRepo } from '../core/agenda-repo';
import { DemoAgendaRepo } from '../core/demo-agenda-repo';
import { RELOJ } from '../core/errores';
import { sembrar } from '../core/seed';
import { SesionService } from '../core/sesion';
import { PanelShell } from './panel-shell';

const AHORA = new Date(2026, 10, 9, 8, 0);

describe('PanelShell', () => {
  const datos = sembrar(AHORA);

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
    const fixture = TestBed.createComponent(PanelShell);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const enlaces = (el: HTMLElement) => Array.from(el.querySelectorAll('nav a')).map(a => a.textContent?.trim());

  it('el administrador ve todo el menú', () => {
    expect(enlaces(crear('marta'))).toEqual(['Panel', 'Agenda', 'Citas', 'Servicios', 'Clientes', 'Usuarios y roles']);
  });

  it('recepción solo ve lo que su rol permite', () => {
    const el = crear('andres');
    expect(enlaces(el)).toEqual(['Agenda', 'Citas', 'Clientes']);
    expect(el.textContent).toContain('Recepción');
  });
});
