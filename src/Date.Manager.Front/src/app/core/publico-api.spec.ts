import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiError } from './api-error';
import { authInterceptor, errorInterceptor } from './interceptores';
import { Servicio } from './models';
import { CitaPublica, PublicoApi } from './publico-api';
import { TokenStore } from './token-store';

const API = 'http://localhost:8080/api';
const BASE = `${API}/publico/invitaciones/abc123`;
const SERVICIO_ID = '6f1c0f6e-0000-4000-8000-0000000000a1';

const SERVICIO: Servicio = {
  id: SERVICIO_ID, nombre: 'Sesión estándar', duracionMin: 60, activo: true, valor: 80000,
  ventanas: [{ id: '6f1c0f6e-0000-4000-8000-0000000000b1', dia: 1, inicio: '08:00', fin: '12:00' }],
};
const CITA: CitaPublica = {
  servicioId: SERVICIO_ID, servicio: 'Sesión estándar', fecha: '2026-11-10', inicio: '10:00', fin: '11:00', estado: 'Confirmada',
};
const CLIENTE = { nombre: 'Camila', apellido: 'Rojas', telefono: '300 123 4567', correo: 'camila@correo.com' };

function configurar() {
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])), provideHttpClientTesting()],
  });
  TestBed.inject(TokenStore).guardar('jwt-del-negocio'); // hay sesión del negocio abierta en la misma pestaña
  return { api: TestBed.inject(PublicoApi), ctrl: TestBed.inject(HttpTestingController) };
}

describe('PublicoApi', () => {
  it('estado hace GET /publico/invitaciones/{codigo} y devuelve estado y vencimiento', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.estado('abc123');
    const req = ctrl.expectOne(BASE);
    expect(req.request.method).toBe('GET');
    req.flush({ estado: 'Activo', venceEn: '2026-11-10T15:00:00Z' });
    expect(await resultado).toEqual({ estado: 'Activo', venceEn: '2026-11-10T15:00:00Z' });
  });

  it('servicios hace GET …/servicios', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.servicios('abc123');
    const req = ctrl.expectOne(`${BASE}/servicios`);
    expect(req.request.method).toBe('GET');
    req.flush([SERVICIO]);
    expect(await resultado).toEqual([SERVICIO]);
  });

  it('horarios hace GET …/servicios/{id}/horarios?fecha=', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.horarios('abc123', SERVICIO_ID, '2026-11-10');
    const req = ctrl.expectOne(`${BASE}/servicios/${SERVICIO_ID}/horarios?fecha=2026-11-10`);
    expect(req.request.method).toBe('GET');
    req.flush(['08:00', '09:00']);
    expect(await resultado).toEqual(['08:00', '09:00']);
  });

  it('reservar hace POST …/citas con servicioId, fecha, inicio y cliente, y devuelve la cita pública', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.reservar('abc123', { servicioId: SERVICIO_ID, fecha: '2026-11-10', inicio: '10:00', cliente: CLIENTE });
    const req = ctrl.expectOne(`${BASE}/citas`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ servicioId: SERVICIO_ID, fecha: '2026-11-10', inicio: '10:00', cliente: CLIENTE });
    req.flush(CITA, { status: 201, statusText: 'Created' });
    expect(await resultado).toEqual(CITA);
  });

  it('el código se escapa en la ruta', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.estado('a/b c');
    ctrl.expectOne(`${API}/publico/invitaciones/a%2Fb%20c`).flush({ estado: 'Activo', venceEn: '2026-11-10T15:00:00Z' });
    await resultado;
  });

  it('ninguna llamada lleva Authorization, aunque haya token del negocio guardado', async () => {
    const { api, ctrl } = configurar();
    const llamadas = [
      api.estado('abc123'),
      api.servicios('abc123'),
      api.horarios('abc123', SERVICIO_ID, '2026-11-10'),
      api.reservar('abc123', { servicioId: SERVICIO_ID, fecha: '2026-11-10', inicio: '10:00', cliente: CLIENTE }),
    ];
    const pedidas = ctrl.match(() => true);
    expect(pedidas.length).toBe(4);
    for (const req of pedidas) {
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush(null);
    }
    await Promise.all(llamadas);
  });

  it('los errores llegan como ApiError con su código y estado', async () => {
    const { api, ctrl } = configurar();
    const resultado = api.estado('nada');
    ctrl.expectOne(`${API}/publico/invitaciones/nada`).flush(
      { codigo: 'ENLACE_NO_ENCONTRADO', mensaje: 'No encontramos ese enlace.' },
      { status: 404, statusText: 'Not Found' },
    );
    const error = await resultado.catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(404);
    expect((error as ApiError).codigo).toBe('ENLACE_NO_ENCONTRADO');
  });
});
