import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiError } from './api-error';
import { InicioApi } from './inicio-api';
import { authInterceptor, errorInterceptor } from './interceptores';
import { TokenStore } from './token-store';

const API = 'http://localhost:8080/api';

function configurar() {
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])), provideHttpClientTesting()],
  });
  return { api: TestBed.inject(InicioApi), ctrl: TestBed.inject(HttpTestingController), tokens: TestBed.inject(TokenStore) };
}

describe('InicioApi', () => {
  it('sin fecha llama a /panel/inicio sin parámetros y con el Bearer', async () => {
    const { api, ctrl, tokens } = configurar();
    tokens.guardar('jwt-1');
    const resultado = api.inicio();
    const req = ctrl.expectOne(r => r.url === `${API}/panel/inicio`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-1');
    req.flush({ fecha: '2026-11-09' });
    expect((await resultado).fecha).toBe('2026-11-09');
  });

  it('con fecha la manda como parámetro', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.inicio('2026-11-02');
    const req = ctrl.expectOne(r => r.url === `${API}/panel/inicio`);
    expect(req.request.params.get('fecha')).toBe('2026-11-02');
    req.flush({ fecha: '2026-11-02' });
    expect((await resultado).fecha).toBe('2026-11-02');
  });

  it('un 403 rechaza con ApiError SIN_PERMISO', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.inicio();
    ctrl.expectOne(r => r.url === `${API}/panel/inicio`).flush(
      { codigo: 'SIN_PERMISO', mensaje: 'No tienes permiso para esta acción.' },
      { status: 403, statusText: 'Forbidden' });
    const e = (await resultado.catch((x: unknown) => x)) as ApiError;
    expect(e).toBeInstanceOf(ApiError);
    expect(e.status).toBe(403);
    expect(e.codigo).toBe('SIN_PERMISO');
  });
});
