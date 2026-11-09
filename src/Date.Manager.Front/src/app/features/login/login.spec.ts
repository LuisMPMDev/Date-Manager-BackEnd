import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authInterceptor, errorInterceptor } from '../../core/interceptores';
import { ANDRES, MARTA, ROL_ADMIN, ROL_RECEPCION } from '../../core/sesion.pruebas';
import { SesionExpirada } from '../../core/sesion-expirada';
import { SesionService } from '../../core/sesion';
import { LoginComponent } from './login';

const API = 'http://localhost:8080/api';
const R401 = { status: 401, statusText: 'Unauthorized' };

describe('LoginComponent', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  function crear() {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement, ctrl: TestBed.inject(HttpTestingController) };
  }

  function escribir(el: HTMLElement, selector: string, valor: string) {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  function enviar(el: HTMLElement, usuario: string, clave: string) {
    escribir(el, 'input[name="usuario"]', usuario);
    escribir(el, 'input[name="clave"]', clave);
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
  }

  const alerta = (el: HTMLElement) => el.querySelector('[role="alert"]')?.textContent ?? '';
  const botonEnviar = (el: HTMLElement) => el.querySelector<HTMLButtonElement>('form button[type="submit"]')!;

  it('el título y el botón de envío dicen «Iniciar sesión»', () => {
    const { el } = crear();
    expect(el.querySelector('h1')!.textContent?.trim()).toBe('Iniciar sesión');
    expect(botonEnviar(el).textContent?.trim()).toBe('Iniciar sesión');
    expect(el.textContent).not.toContain('Entrar');
  });

  it('no hay botones de acceso rápido ni cuentas de ejemplo', () => {
    const { el } = crear();
    expect(el.querySelectorAll('button').length).toBe(1);
    expect(el.textContent).not.toMatch(/marta|andr[eé]s|demo123|ejemplo/i);
    expect(el.querySelector('.tarjeta, .tarjetas, .ejemplo')).toBeNull();
  });

  it('manda usuario (sin espacios sobrantes) y clave a POST /auth/login', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, el, ctrl } = crear();
    enviar(el, '  marta ', 'demo123');
    const req = ctrl.expectOne(`${API}/auth/login`);
    expect(req.request.body).toEqual({ usuario: 'marta', clave: 'demo123' });
    req.flush({ token: 'jwt-1', usuario: MARTA, rol: ROL_ADMIN });
    await fixture.whenStable();
  });

  it('credenciales inválidas muestran el mensaje de la API y no inician sesión', async () => {
    const { fixture, el, ctrl } = crear();
    enviar(el, 'marta', 'mala');
    ctrl.expectOne(`${API}/auth/login`).flush(
      { codigo: 'CREDENCIALES_INVALIDAS', mensaje: 'Usuario o clave incorrectos.' }, R401);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alerta(el)).toContain('Usuario o clave incorrectos.');
    expect(TestBed.inject(SesionService).usuario()).toBeNull();
    expect(sessionStorage.length).toBe(0);
    expect(botonEnviar(el).disabled).toBe(false);
  });

  it('un fallo de red muestra el mensaje del cliente y vuelve a habilitar el botón', async () => {
    const { fixture, el, ctrl } = crear();
    enviar(el, 'marta', 'demo123');
    ctrl.expectOne(`${API}/auth/login`).error(new ProgressEvent('error'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alerta(el)).toContain('No se pudo contactar al servidor');
    expect(botonEnviar(el).disabled).toBe(false);
  });

  it('deshabilita el botón mientras hay una petición y no envía una segunda', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, el, ctrl } = crear();
    enviar(el, 'marta', 'demo123');
    fixture.detectChanges();
    expect(botonEnviar(el).disabled).toBe(true);

    el.querySelector('form')!.dispatchEvent(new Event('submit')); // doble envío
    const pedidas = ctrl.match(`${API}/auth/login`);
    expect(pedidas.length).toBe(1);

    pedidas[0].flush({ token: 'jwt-1', usuario: MARTA, rol: ROL_ADMIN });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(botonEnviar(el).disabled).toBe(false);
  });

  it('tras entrar inicia la sesión y navega a la primera ruta del rol', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, el, ctrl } = crear();
    enviar(el, 'andres', 'demo123');
    ctrl.expectOne(`${API}/auth/login`).flush({ token: 'jwt-2', usuario: ANDRES, rol: ROL_RECEPCION });
    await fixture.whenStable();

    const sesion = TestBed.inject(SesionService);
    expect(sesion.usuario()?.usuario).toBe('andres');
    expect(sesion.rol()?.id).toBe(ROL_RECEPCION.id);
    expect(navegar).toHaveBeenCalledWith('/agenda');
  });

  it('el administrador entra al panel', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, el, ctrl } = crear();
    enviar(el, 'marta', 'demo123');
    ctrl.expectOne(`${API}/auth/login`).flush({ token: 'jwt-1', usuario: MARTA, rol: ROL_ADMIN });
    await fixture.whenStable();
    expect(navegar).toHaveBeenCalledWith('/panel');
  });

  it('muestra el aviso de una sesión que venció y desaparece al iniciar sesión', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const sesion = TestBed.inject(SesionService);
    sesion.iniciar(MARTA, ROL_ADMIN);
    TestBed.inject(SesionExpirada).emitir();
    expect(navegar).toHaveBeenCalledWith('/login');

    const { fixture, el, ctrl } = crear();
    const aviso = el.querySelector('[role="status"]');
    expect(aviso?.textContent).toContain('Tu sesión venció. Inicia sesión de nuevo.');

    enviar(el, 'marta', 'demo123');
    ctrl.expectOne(`${API}/auth/login`).flush({ token: 'jwt-3', usuario: MARTA, rol: ROL_ADMIN });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(sesion.aviso()).toBeNull();
    expect(el.querySelector('[role="status"]')).toBeNull();
  });

  it('sin aviso pendiente no hay región de estado', () => {
    const { el } = crear();
    expect(el.querySelector('[role="status"]')).toBeNull();
  });
});
