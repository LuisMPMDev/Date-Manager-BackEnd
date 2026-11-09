import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiError } from './api-error';
import { AuthApi } from './auth-api';
import { authInterceptor, errorInterceptor } from './interceptores';
import { MARTA, ROL_ADMIN } from './sesion.pruebas';
import { TokenStore } from './token-store';

const API = 'http://localhost:8080/api';
const CLAVE_TOKEN = 'datemanager.sesion.v2';

function configurar() {
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])), provideHttpClientTesting()],
  });
  return { api: TestBed.inject(AuthApi), ctrl: TestBed.inject(HttpTestingController), tokens: TestBed.inject(TokenStore) };
}

describe('AuthApi', () => {
  it('login envía usuario y clave, guarda el token y devuelve usuario y rol', async () => {
    const { api, ctrl, tokens } = configurar();
    const resultado = api.login('marta', 'demo123');
    const req = ctrl.expectOne(`${API}/auth/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ usuario: 'marta', clave: 'demo123' });
    req.flush({ token: 'jwt-1', usuario: MARTA, rol: ROL_ADMIN });

    expect(await resultado).toEqual({ usuario: MARTA, rol: ROL_ADMIN });
    expect(tokens.leer()).toBe('jwt-1');
  });

  it('login nunca lleva Bearer, aunque haya un token de otra sesión', async () => {
    const { api, ctrl, tokens } = configurar();
    tokens.guardar('viejo');
    const resultado = api.login('marta', 'demo123');
    const req = ctrl.expectOne(`${API}/auth/login`);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ token: 'nuevo', usuario: MARTA, rol: ROL_ADMIN });
    await resultado;
  });

  it('la clave no se escribe en el almacenamiento: solo queda {token}', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.login('marta', 'clave-secreta-9');
    ctrl.expectOne(`${API}/auth/login`).flush({ token: 'jwt-1', usuario: MARTA, rol: ROL_ADMIN });
    await resultado;

    expect(JSON.parse(sessionStorage.getItem(CLAVE_TOKEN)!)).toEqual({ token: 'jwt-1' });
    for (const almacen of [sessionStorage, localStorage]) {
      for (let i = 0; i < almacen.length; i++) {
        expect(almacen.getItem(almacen.key(i)!)).not.toContain('clave-secreta-9');
      }
    }
  });

  it('credenciales inválidas rechazan con ApiError y no guardan nada', async () => {
    const { api, ctrl, tokens } = configurar();
    const resultado = api.login('marta', 'mala');
    ctrl.expectOne(`${API}/auth/login`).flush(
      { codigo: 'CREDENCIALES_INVALIDAS', mensaje: 'Usuario o clave incorrectos.' },
      { status: 401, statusText: 'Unauthorized' });

    const e = (await resultado.catch((x: unknown) => x)) as ApiError;
    expect(e).toBeInstanceOf(ApiError);
    expect(e.codigo).toBe('CREDENCIALES_INVALIDAS');
    expect(e.message).toBe('Usuario o clave incorrectos.');
    expect(tokens.leer()).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });

  it('yo lee usuario y rol con el Bearer y NO toca el token guardado', async () => {
    const { api, ctrl, tokens } = configurar();
    tokens.guardar('jwt-1');
    const resultado = api.yo();
    const req = ctrl.expectOne(`${API}/auth/yo`);
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-1');
    req.flush({ usuario: MARTA, rol: ROL_ADMIN }); // sin token: /auth/yo no lo devuelve

    expect(await resultado).toEqual({ usuario: MARTA, rol: ROL_ADMIN });
    expect(tokens.leer()).toBe('jwt-1');
  });
});
