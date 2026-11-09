import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Rol, Usuario } from '../core/models';
import { ANDRES, MARTA, ROL_ADMIN, ROL_RECEPCION } from '../core/sesion.pruebas';
import { SesionService } from '../core/sesion';
import { PanelShell } from './panel-shell';

describe('PanelShell', () => {
  function crear(usuario: Usuario, rol: Rol) {
    sessionStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [PanelShell],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(SesionService).iniciar(usuario, rol);
    const fixture = TestBed.createComponent(PanelShell);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const enlaces = (el: HTMLElement) => Array.from(el.querySelectorAll('nav a')).map(a => a.textContent?.trim());
  const botones = (el: HTMLElement) => Array.from(el.querySelectorAll('button')).map(b => b.textContent?.trim());

  it('el administrador ve todo el menú, con Enlaces', () => {
    const { el } = crear(MARTA, ROL_ADMIN);
    expect(enlaces(el)).toEqual(['Panel', 'Agenda', 'Citas', 'Servicios', 'Clientes', 'Usuarios y roles', 'Enlaces']);
  });

  it('recepción solo ve lo que su rol permite', () => {
    const { el } = crear(ANDRES, ROL_RECEPCION);
    expect(enlaces(el)).toEqual(['Agenda', 'Citas', 'Clientes']);
    expect(el.textContent).toContain('Recepción');
  });

  it('un rol sin gestionar_enlaces no ve Enlaces', () => {
    const { el } = crear(MARTA, { ...ROL_ADMIN, permisos: ROL_ADMIN.permisos.filter(p => p !== 'gestionar_enlaces') });
    expect(enlaces(el)).not.toContain('Enlaces');
  });

  it('ya no existe «Reiniciar demo» ni su confirmación: solo queda «Cerrar sesión»', () => {
    const { el } = crear(MARTA, ROL_ADMIN);
    expect(botones(el)).toEqual(['Cerrar sesión']);
    expect(el.textContent).not.toMatch(/reiniciar|datos de ejemplo/i);
  });

  it('«Cerrar sesión» limpia la sesión, borra el token y va a /login', async () => {
    const { fixture, el } = crear(MARTA, ROL_ADMIN);
    sessionStorage.setItem('datemanager.sesion.v2', JSON.stringify({ token: 'jwt-1' }));
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    el.querySelector<HTMLButtonElement>('button.accion')!.click();
    await fixture.whenStable();

    expect(TestBed.inject(SesionService).usuario()).toBeNull();
    expect(sessionStorage.getItem('datemanager.sesion.v2')).toBeNull();
    expect(navegar).toHaveBeenCalledWith('/login');
  });
});
