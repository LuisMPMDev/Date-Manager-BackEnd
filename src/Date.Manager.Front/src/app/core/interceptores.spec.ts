import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiError } from './api-error';
import { API_URL } from './config';
import { authInterceptor, errorInterceptor, llevaToken } from './interceptores';
import { SesionExpirada } from './sesion-expirada';
import { TokenStore } from './token-store';

const API = 'http://localhost:8080/api';
const NO_AUTENTICADO = { codigo: 'NO_AUTENTICADO', mensaje: 'Sesión vencida.' };
const R401 = { status: 401, statusText: 'Unauthorized' };

function configurar() {
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
      provideHttpClientTesting(),
    ],
  });
  return {
    http: TestBed.inject(HttpClient),
    ctrl: TestBed.inject(HttpTestingController),
    tokens: TestBed.inject(TokenStore),
    expirada: TestBed.inject(SesionExpirada),
  };
}

describe('API_URL', () => {
  it('por defecto apunta a http://localhost:8080/api', () => {
    TestBed.resetTestingModule();
    expect(TestBed.inject(API_URL)).toBe(API);
  });
});

describe('TokenStore', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  it('guarda, lee y borra el token en sessionStorage con la clave v2', () => {
    const t = TestBed.inject(TokenStore);
    expect(t.leer()).toBeNull();
    t.guardar('abc');
    expect(t.leer()).toBe('abc');
    expect(JSON.parse(sessionStorage.getItem('datemanager.sesion.v2')!)).toEqual({ token: 'abc' });
    t.borrar();
    expect(t.leer()).toBeNull();
    expect(sessionStorage.getItem('datemanager.sesion.v2')).toBeNull();
  });

  it('descarta un contenido guardado que no tiene la forma esperada', () => {
    sessionStorage.setItem('datemanager.sesion.v2', '{"otra":1}');
    expect(TestBed.inject(TokenStore).leer()).toBeNull();
    sessionStorage.setItem('datemanager.sesion.v2', 'no es json');
    expect(TestBed.inject(TokenStore).leer()).toBeNull();
  });
});

describe('llevaToken', () => {
  it('es true para /panel/** y para GET /auth/yo', () => {
    expect(llevaToken(`${API}/panel/servicios`, 'GET', API)).toBe(true);
    expect(llevaToken(`${API}/panel/citas/1/cancelar`, 'POST', API)).toBe(true);
    expect(llevaToken(`${API}/panel/inicio?fecha=2026-11-09`, 'GET', API)).toBe(true);
    expect(llevaToken(`${API}/auth/yo`, 'GET', API)).toBe(true);
    expect(llevaToken(`${API}/auth/yo`, 'get', API)).toBe(true);
  });

  it('es false para lo público, el login y cualquier otra cosa', () => {
    expect(llevaToken(`${API}/publico/invitaciones/X`, 'GET', API)).toBe(false);
    expect(llevaToken(`${API}/auth/login`, 'POST', API)).toBe(false);
    expect(llevaToken(`${API}/auth/yo`, 'POST', API)).toBe(false);
    expect(llevaToken(`${API}/auth/yo/otro`, 'GET', API)).toBe(false);
    expect(llevaToken(`${API}/panel`, 'GET', API)).toBe(false);
    expect(llevaToken(`${API}/panelx/servicios`, 'GET', API)).toBe(false);
    expect(llevaToken(`${API}/publico/panel/servicios`, 'GET', API)).toBe(false);
  });

  it('es false para URLs que no empiezan por la API configurada', () => {
    expect(llevaToken('https://ajena.com/api/panel/servicios', 'GET', API)).toBe(false);
    expect(llevaToken(`${API}.ajena.com/panel/servicios`, 'GET', API)).toBe(false);
    expect(llevaToken('/api/panel/servicios', 'GET', API)).toBe(false);
  });

  it('usa la dirección por defecto si no se indica otra', () => {
    expect(llevaToken(`${API}/panel/servicios`, 'GET')).toBe(true);
  });
});

describe('authInterceptor', () => {
  it('añade Authorization: Bearer a GET /panel/servicios y a GET /auth/yo', () => {
    const { http, ctrl, tokens } = configurar();
    tokens.guardar('abc');
    http.get(`${API}/panel/servicios`).subscribe();
    http.get(`${API}/auth/yo`).subscribe();
    expect(ctrl.expectOne(`${API}/panel/servicios`).request.headers.get('Authorization')).toBe('Bearer abc');
    expect(ctrl.expectOne(`${API}/auth/yo`).request.headers.get('Authorization')).toBe('Bearer abc');
    ctrl.verify();
  });

  it('no lo añade a la zona pública ni al login aunque haya token', () => {
    const { http, ctrl, tokens } = configurar();
    tokens.guardar('abc');
    http.get(`${API}/publico/invitaciones/X`).subscribe();
    http.post(`${API}/publico/invitaciones/X/citas`, {}).subscribe();
    http.post(`${API}/auth/login`, {}).subscribe();
    const pedidas = ctrl.match(() => true);
    expect(pedidas.length).toBe(3);
    for (const r of pedidas) expect(r.request.headers.has('Authorization')).toBe(false);
  });

  it('no lo añade a una URL ajena aunque el token exista', () => {
    const { http, ctrl, tokens } = configurar();
    tokens.guardar('abc');
    http.get('https://ajena.com/api/panel/servicios').subscribe();
    expect(ctrl.expectOne('https://ajena.com/api/panel/servicios').request.headers.has('Authorization')).toBe(false);
  });

  it('sin token no pone cabecera', () => {
    const { http, ctrl } = configurar();
    http.get(`${API}/panel/servicios`).subscribe();
    expect(ctrl.expectOne(`${API}/panel/servicios`).request.headers.has('Authorization')).toBe(false);
  });
});

describe('errorInterceptor', () => {
  it('envuelve los errores HTTP en ApiError', async () => {
    const { http, ctrl } = configurar();
    const resultado = new Promise<unknown>(res => http.get(`${API}/panel/citas`).subscribe({ error: res }));
    ctrl.expectOne(`${API}/panel/citas`).flush(
      { codigo: 'SIN_PERMISO', mensaje: 'No tienes permiso.' }, { status: 403, statusText: 'Forbidden' });
    const e = (await resultado) as ApiError;
    expect(e).toBeInstanceOf(ApiError);
    expect(e.status).toBe(403);
    expect(e.codigo).toBe('SIN_PERMISO');
  });

  it('un fallo de red llega como ERROR_RED', async () => {
    const { http, ctrl } = configurar();
    const resultado = new Promise<unknown>(res => http.get(`${API}/panel/citas`).subscribe({ error: res }));
    ctrl.expectOne(`${API}/panel/citas`).error(new ProgressEvent('error'));
    expect(((await resultado) as ApiError).codigo).toBe('ERROR_RED');
  });

  it('un 401 en una ruta con Bearer emite sesión expirada y llega como ApiError', async () => {
    const { http, ctrl, tokens, expirada } = configurar();
    let avisos = 0;
    expirada.alExpirar(() => avisos++);
    tokens.guardar('abc');
    const resultado = new Promise<unknown>(res => http.get(`${API}/panel/citas`).subscribe({ error: res }));
    ctrl.expectOne(`${API}/panel/citas`).flush(NO_AUTENTICADO, R401);
    const e = (await resultado) as ApiError;
    expect(e.codigo).toBe('NO_AUTENTICADO');
    expect(avisos).toBe(1);
  });

  it('tres 401 seguidos emiten sesión expirada una sola vez', () => {
    const { http, ctrl, tokens, expirada } = configurar();
    let avisos = 0;
    expirada.alExpirar(() => avisos++);
    tokens.guardar('abc');
    for (const ruta of ['citas', 'servicios', 'clientes']) {
      http.get(`${API}/panel/${ruta}`).subscribe({ error: () => undefined });
    }
    const pedidas = ctrl.match(() => true);
    expect(pedidas.length).toBe(3);
    for (const r of pedidas) r.flush(NO_AUTENTICADO, R401);
    expect(avisos).toBe(1);
  });

  it('guardar un token nuevo rearma el aviso para la sesión siguiente', () => {
    const { http, ctrl, tokens, expirada } = configurar();
    let avisos = 0;
    expirada.alExpirar(() => avisos++);
    tokens.guardar('abc');
    http.get(`${API}/panel/citas`).subscribe({ error: () => undefined });
    ctrl.expectOne(`${API}/panel/citas`).flush(NO_AUTENTICADO, R401);
    expect(avisos).toBe(1);

    tokens.borrar();
    tokens.guardar('nuevo');
    http.get(`${API}/panel/citas`).subscribe({ error: () => undefined });
    ctrl.expectOne(`${API}/panel/citas`).flush(NO_AUTENTICADO, R401);
    expect(avisos).toBe(2);
  });

  it('un 401 en POST /auth/login (credenciales inválidas) no emite y llega como ApiError', async () => {
    const { http, ctrl, tokens, expirada } = configurar();
    let avisos = 0;
    expirada.alExpirar(() => avisos++);
    tokens.guardar('abc'); // aunque haya token de otra sesión, el login no lleva Bearer
    const resultado = new Promise<unknown>(res => http.post(`${API}/auth/login`, {}).subscribe({ error: res }));
    ctrl.expectOne(`${API}/auth/login`).flush(
      { codigo: 'CREDENCIALES_INVALIDAS', mensaje: 'Usuario o clave incorrectos.' }, R401);
    const e = (await resultado) as ApiError;
    expect(e.codigo).toBe('CREDENCIALES_INVALIDAS');
    expect(e.message).toBe('Usuario o clave incorrectos.');
    expect(avisos).toBe(0);
  });

  it('un 401 en la zona pública no emite', () => {
    const { http, ctrl, tokens, expirada } = configurar();
    let avisos = 0;
    expirada.alExpirar(() => avisos++);
    tokens.guardar('abc');
    http.get(`${API}/publico/invitaciones/X`).subscribe({ error: () => undefined });
    ctrl.expectOne(`${API}/publico/invitaciones/X`).flush(NO_AUTENTICADO, R401);
    expect(avisos).toBe(0);
  });

  it('un 403 con Bearer no emite sesión expirada', () => {
    const { http, ctrl, tokens, expirada } = configurar();
    let avisos = 0;
    expirada.alExpirar(() => avisos++);
    tokens.guardar('abc');
    http.get(`${API}/panel/usuarios`).subscribe({ error: () => undefined });
    ctrl.expectOne(`${API}/panel/usuarios`).flush(
      { codigo: 'SIN_PERMISO', mensaje: 'x' }, { status: 403, statusText: 'Forbidden' });
    expect(avisos).toBe(0);
  });

  it('se puede dejar de escuchar', () => {
    const { http, ctrl, tokens, expirada } = configurar();
    let avisos = 0;
    const baja = expirada.alExpirar(() => avisos++);
    baja();
    tokens.guardar('abc');
    http.get(`${API}/panel/citas`).subscribe({ error: () => undefined });
    ctrl.expectOne(`${API}/panel/citas`).flush(NO_AUTENTICADO, R401);
    expect(avisos).toBe(0);
  });
});
