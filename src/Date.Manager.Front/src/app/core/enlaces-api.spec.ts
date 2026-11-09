import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiError } from './api-error';
import { Enlace, EnlacesApi } from './enlaces-api';
import { authInterceptor, errorInterceptor } from './interceptores';
import { TokenStore } from './token-store';

const API = 'http://localhost:8080/api';

const ENLACE: Enlace = {
  id: '6f1c0f6e-0000-4000-8000-000000000001',
  codigo: 'abc123XYZ',
  ruta: '/agendar/abc123XYZ',
  estado: 'Activo',
  usos: 1,
  venceEn: '2026-11-10T15:00:00Z',
  creadoEn: '2026-11-09T15:00:00Z',
  creadoPor: '6f1c0f6e-0000-4000-8000-000000000002',
  creadoPorNombre: 'Marta Ríos',
};

function configurar() {
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])), provideHttpClientTesting()],
  });
  return { api: TestBed.inject(EnlacesApi), ctrl: TestBed.inject(HttpTestingController), tokens: TestBed.inject(TokenStore) };
}

describe('EnlacesApi', () => {
  it('listar hace GET /panel/enlaces con el Bearer', async () => {
    const { api, ctrl, tokens } = configurar();
    tokens.guardar('jwt-1');
    const resultado = api.listar();
    const req = ctrl.expectOne(`${API}/panel/enlaces`);
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-1');
    req.flush([ENLACE]);
    expect(await resultado).toEqual([ENLACE]);
  });

  it('crear hace POST /panel/enlaces con duración y máximo', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.crear({ duracionMinutos: 120, maxUsos: 5 });
    const req = ctrl.expectOne(`${API}/panel/enlaces`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ duracionMinutos: 120, maxUsos: 5 });
    req.flush({ ...ENLACE, maxUsos: 5 }, { status: 201, statusText: 'Created' });
    expect((await resultado).maxUsos).toBe(5);
  });

  it('crear sin máximo no manda la clave maxUsos', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.crear({ duracionMinutos: 1440 });
    const req = ctrl.expectOne(`${API}/panel/enlaces`);
    expect(req.request.body).toEqual({ duracionMinutos: 1440 });
    expect('maxUsos' in req.request.body).toBe(false);
    req.flush(ENLACE, { status: 201, statusText: 'Created' });
    expect((await resultado).maxUsos).toBeUndefined();
  });

  it('desactivar hace POST /panel/enlaces/{id}/desactivar', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.desactivar(ENLACE.id);
    const req = ctrl.expectOne(`${API}/panel/enlaces/${ENLACE.id}/desactivar`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...ENLACE, estado: 'Desactivado' });
    expect((await resultado).estado).toBe('Desactivado');
  });

  it('un 400 rechaza con ApiError y el mensaje de la API', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.crear({ duracionMinutos: 10 });
    ctrl.expectOne(`${API}/panel/enlaces`).flush(
      { codigo: 'VALIDACION', mensaje: 'La duración debe estar entre 60 y 43200 minutos.' },
      { status: 400, statusText: 'Bad Request' });
    const e = (await resultado.catch((x: unknown) => x)) as ApiError;
    expect(e).toBeInstanceOf(ApiError);
    expect(e.status).toBe(400);
    expect(e.message).toBe('La duración debe estar entre 60 y 43200 minutos.');
  });
});
