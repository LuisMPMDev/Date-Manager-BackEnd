import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthApi } from './auth-api';
import { authInterceptor, errorInterceptor } from './interceptores';
import { Rol } from './models';
import { ANDRES, MARTA, ROL_ADMIN, ROL_RECEPCION } from './sesion.pruebas';
import { SesionExpirada } from './sesion-expirada';
import { SesionService } from './sesion';
import { TokenStore } from './token-store';

const API = 'http://localhost:8080/api';
const CLAVE_TOKEN = 'datemanager.sesion.v2';
const R401 = { status: 401, statusText: 'Unauthorized' };
const NO_AUTENTICADO = { codigo: 'NO_AUTENTICADO', mensaje: 'Inicia sesión para continuar.' };
const VENCIDA = 'Tu sesión venció. Inicia sesión de nuevo.';
const SIN_USUARIO = 'Tu usuario ya no existe.';

/** `ruta`: la URL del navegador al arrancar (antes de que el router termine su primera navegación). */
function configurar(ruta?: string) {
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideLocationMocks(),
      provideRouter([]),
      provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
      provideHttpClientTesting(),
    ],
  });
  if (ruta) TestBed.inject(Location).go(ruta);
  const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  return {
    ctrl: TestBed.inject(HttpTestingController),
    tokens: TestBed.inject(TokenStore),
    http: TestBed.inject(HttpClient),
    expirada: TestBed.inject(SesionExpirada),
    navegar,
  };
}

/** Inicia sesión como marta con el AuthApi real (guarda el token) y la sesión del servicio. */
async function entrarComoMarta(ctrl: HttpTestingController, rol: Rol = ROL_ADMIN): Promise<SesionService> {
  const sesion = TestBed.inject(SesionService);
  const resultado = TestBed.inject(AuthApi).login('marta', 'demo123');
  ctrl.expectOne(`${API}/auth/login`).flush({ token: 'jwt-1', usuario: MARTA, rol });
  const { usuario, rol: elRol } = await resultado;
  sesion.iniciar(usuario, elRol);
  return sesion;
}

describe('SesionService: restaurar al cargar', () => {
  it('sin token no pide nada y queda sin sesión', async () => {
    const { ctrl } = configurar();
    const sesion = TestBed.inject(SesionService);
    await sesion.listo;
    ctrl.expectNone(`${API}/auth/yo`);
    expect(sesion.usuario()).toBeNull();
    expect(sesion.rol()).toBeUndefined();
    expect(sesion.aviso()).toBeNull();
  });

  it('con token y /auth/yo correcto carga usuario y rol y conserva el token (yo no devuelve token)', async () => {
    const { ctrl, tokens } = configurar();
    tokens.guardar('jwt-1');
    const sesion = TestBed.inject(SesionService);
    const req = ctrl.expectOne(`${API}/auth/yo`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-1');
    req.flush({ usuario: ANDRES, rol: ROL_RECEPCION });
    await sesion.listo;

    expect(sesion.usuario()?.usuario).toBe('andres');
    expect(sesion.rol()?.id).toBe(ROL_RECEPCION.id);
    expect(sesion.puede('ver_citas')).toBe(true);
    expect(sesion.puede('gestionar_usuarios')).toBe(false);
    expect(tokens.leer()).toBe('jwt-1');
    expect(sesion.aviso()).toBeNull();
  });

  it('con token y /auth/yo → 401 queda sin sesión, borra el token y explica el motivo', async () => {
    const { ctrl, tokens, navegar } = configurar();
    tokens.guardar('vencido');
    const sesion = TestBed.inject(SesionService);
    ctrl.expectOne(`${API}/auth/yo`).flush(NO_AUTENTICADO, R401);
    await sesion.listo;

    expect(sesion.usuario()).toBeNull();
    expect(sesion.rol()).toBeUndefined();
    expect(tokens.leer()).toBeNull();
    expect(sessionStorage.getItem(CLAVE_TOKEN)).toBeNull();
    expect(sesion.aviso()).toBe(VENCIDA);
    expect(navegar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith('/login');
  });

  it('arrancando en /citas con el token vencido manda a /login con el aviso, una sola vez', async () => {
    const { ctrl, tokens, navegar } = configurar('/citas');
    tokens.guardar('vencido');
    const sesion = TestBed.inject(SesionService);
    ctrl.expectOne(`${API}/auth/yo`).flush(NO_AUTENTICADO, R401);
    await sesion.listo;

    expect(tokens.leer()).toBeNull();
    expect(sesion.aviso()).toBe(VENCIDA);
    expect(navegar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith('/login');
  });

  it('arrancando en /agendar/X con el token vencido borra el token pero no saca al cliente de la página pública', async () => {
    const { ctrl, tokens, navegar } = configurar('/agendar/ABC123');
    tokens.guardar('vencido');
    const sesion = TestBed.inject(SesionService);
    // el router aún no navegó (router.url es '/'): la ruta sale de la URL del navegador
    expect(TestBed.inject(Router).url).toBe('/');
    ctrl.expectOne(`${API}/auth/yo`).flush(NO_AUTENTICADO, R401);
    await sesion.listo;

    expect(tokens.leer()).toBeNull();
    expect(sessionStorage.getItem(CLAVE_TOKEN)).toBeNull();
    expect(sesion.usuario()).toBeNull();
    expect(navegar).not.toHaveBeenCalled();
    expect(TestBed.inject(Location).path()).toBe('/agendar/ABC123');
  });

  it('un fallo de red al restaurar deja sin sesión pero conserva el token para reintentar', async () => {
    const { ctrl, tokens } = configurar();
    tokens.guardar('jwt-1');
    const sesion = TestBed.inject(SesionService);
    ctrl.expectOne(`${API}/auth/yo`).error(new ProgressEvent('error'));
    await sesion.listo;

    expect(sesion.usuario()).toBeNull();
    expect(tokens.leer()).toBe('jwt-1');
    expect(sesion.aviso()).toContain('No se pudo contactar al servidor');
  });

  it('si el usuario inicia sesión mientras /auth/yo está pendiente, esa sesión manda', async () => {
    const { ctrl, tokens } = configurar();
    tokens.guardar('viejo');
    const sesion = TestBed.inject(SesionService);
    const pendiente = ctrl.expectOne(`${API}/auth/yo`);
    sesion.iniciar(MARTA, ROL_ADMIN);
    pendiente.flush({ usuario: ANDRES, rol: ROL_RECEPCION });
    await sesion.listo;

    expect(sesion.usuario()?.usuario).toBe('marta');
    expect(sesion.rol()?.id).toBe(ROL_ADMIN.id);
  });
});

describe('SesionService: iniciar y cerrar', () => {
  it('login correcto guarda el token y deja la sesión iniciada', async () => {
    const { ctrl, tokens } = configurar();
    const sesion = await entrarComoMarta(ctrl);

    expect(tokens.leer()).toBe('jwt-1');
    expect(sesion.usuario()?.usuario).toBe('marta');
    expect(sesion.rol()?.nombre).toBe('Administrador');
    expect(sesion.puede('gestionar_enlaces')).toBe(true);
  });

  it('login con credenciales inválidas no deja sesión ni guarda nada', async () => {
    const { ctrl, tokens } = configurar();
    const sesion = TestBed.inject(SesionService);
    const resultado = TestBed.inject(AuthApi).login('marta', 'mala').catch((e: unknown) => e);
    ctrl.expectOne(`${API}/auth/login`).flush(
      { codigo: 'CREDENCIALES_INVALIDAS', mensaje: 'Usuario o clave incorrectos.' }, R401);

    expect(((await resultado) as Error).message).toBe('Usuario o clave incorrectos.');
    expect(sesion.usuario()).toBeNull();
    expect(tokens.leer()).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });

  it('lo único que queda en sessionStorage es {token}: ni la clave ni el usuario', async () => {
    const { ctrl } = configurar();
    const sesion = TestBed.inject(SesionService);
    const resultado = TestBed.inject(AuthApi).login('marta', 'clave-secreta-9');
    ctrl.expectOne(`${API}/auth/login`).flush({ token: 'jwt-1', usuario: MARTA, rol: ROL_ADMIN });
    const { usuario, rol } = await resultado;
    sesion.iniciar(usuario, rol);

    expect(sessionStorage.length).toBe(1);
    expect(JSON.parse(sessionStorage.getItem(CLAVE_TOKEN)!)).toEqual({ token: 'jwt-1' });
    expect(sessionStorage.getItem(CLAVE_TOKEN)).not.toContain('clave-secreta-9');
  });

  it('cerrar borra el token y limpia la sesión sin aviso', async () => {
    const { ctrl, tokens } = configurar();
    const sesion = await entrarComoMarta(ctrl);
    sesion.cerrar();

    expect(sesion.usuario()).toBeNull();
    expect(sesion.rol()).toBeUndefined();
    expect(tokens.leer()).toBeNull();
    expect(sessionStorage.length).toBe(0);
    expect(sesion.aviso()).toBeNull();
  });

  it('iniciar una sesión nueva borra el aviso de la anterior', async () => {
    const { ctrl, expirada } = configurar();
    const sesion = await entrarComoMarta(ctrl);
    expirada.emitir();
    expect(sesion.aviso()).toBe(VENCIDA);
    sesion.iniciar(ANDRES, ROL_RECEPCION);
    expect(sesion.aviso()).toBeNull();
  });
});

describe('SesionService: sesión vencida a mitad de una llamada', () => {
  it('un 401 en una llamada del panel cierra la sesión, navega a /login una vez y avisa', async () => {
    const { ctrl, http, tokens, navegar } = configurar();
    const sesion = await entrarComoMarta(ctrl);

    // varias llamadas del panel en vuelo fallan juntas con 401
    const fallos: unknown[] = [];
    for (const ruta of ['citas', 'servicios', 'clientes']) {
      http.get(`${API}/panel/${ruta}`).subscribe({ error: e => fallos.push(e) });
    }
    const pedidas = ctrl.match(() => true);
    expect(pedidas.length).toBe(3);
    for (const p of pedidas) {
      expect(p.request.headers.get('Authorization')).toBe('Bearer jwt-1');
      p.flush(NO_AUTENTICADO, R401);
    }

    expect(fallos.length).toBe(3); // cada llamada recibe su error: ninguna pantalla se queda «guardando»
    expect(sesion.usuario()).toBeNull();
    expect(sesion.rol()).toBeUndefined();
    expect(tokens.leer()).toBeNull();
    expect(navegar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith('/login');
    expect(sesion.aviso()).toBe(VENCIDA);
  });

  it('un 401 en una escritura (POST) también cierra la sesión', async () => {
    const { ctrl, http, navegar } = configurar();
    const sesion = await entrarComoMarta(ctrl);
    let fallo: unknown;
    http.post(`${API}/panel/citas/1/cancelar`, {}).subscribe({ error: e => (fallo = e) });
    ctrl.expectOne(`${API}/panel/citas/1/cancelar`).flush(NO_AUTENTICADO, R401);

    expect(fallo).toBeDefined();
    expect(sesion.usuario()).toBeNull();
    expect(navegar).toHaveBeenCalledTimes(1);
  });

  it('la suscripción existe desde que se crea el servicio', () => {
    const { expirada, navegar } = configurar();
    const sesion = TestBed.inject(SesionService);
    sesion.iniciar(MARTA, ROL_ADMIN);
    expirada.emitir();
    expect(sesion.usuario()).toBeNull();
    expect(navegar).toHaveBeenCalledTimes(1);
  });

  it('tras volver a iniciar sesión, un nuevo vencimiento vuelve a avisar y navegar', async () => {
    const { ctrl, http, navegar } = configurar();
    const sesion = await entrarComoMarta(ctrl);
    http.get(`${API}/panel/citas`).subscribe({ error: () => undefined });
    ctrl.expectOne(`${API}/panel/citas`).flush(NO_AUTENTICADO, R401);
    expect(navegar).toHaveBeenCalledTimes(1);

    await entrarComoMarta(ctrl);
    expect(sesion.usuario()).not.toBeNull();
    http.get(`${API}/panel/citas`).subscribe({ error: () => undefined });
    ctrl.expectOne(`${API}/panel/citas`).flush(NO_AUTENTICADO, R401);
    expect(navegar).toHaveBeenCalledTimes(2);
    expect(sesion.aviso()).toBe(VENCIDA);
  });

  it('un 401 en una llamada del panel estando en /citas expulsa a /login como siempre', async () => {
    const { ctrl, http, tokens, navegar } = configurar('/citas');
    const sesion = await entrarComoMarta(ctrl);
    http.get(`${API}/panel/citas`).subscribe({ error: () => undefined });
    ctrl.expectOne(`${API}/panel/citas`).flush(NO_AUTENTICADO, R401);

    expect(sesion.usuario()).toBeNull();
    expect(tokens.leer()).toBeNull();
    expect(navegar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith('/login');
  });

  it('un 403 no cierra la sesión', async () => {
    const { ctrl, http, navegar } = configurar();
    const sesion = await entrarComoMarta(ctrl);
    http.get(`${API}/panel/usuarios`).subscribe({ error: () => undefined });
    ctrl.expectOne(`${API}/panel/usuarios`).flush(
      { codigo: 'SIN_PERMISO', mensaje: 'No tienes permiso.' }, { status: 403, statusText: 'Forbidden' });

    expect(sesion.usuario()).not.toBeNull();
    expect(navegar).not.toHaveBeenCalled();
  });
});

describe('SesionService.refrescar', () => {
  it('sin sesión no hace nada', async () => {
    const { ctrl } = configurar();
    const sesion = TestBed.inject(SesionService);
    await sesion.refrescar();
    ctrl.expectNone(`${API}/auth/yo`);
    expect(sesion.usuario()).toBeNull();
  });

  it('lee /auth/yo y actualiza el rol y los permisos vigentes sin tocar el token', async () => {
    const { ctrl, tokens } = configurar();
    const sesion = await entrarComoMarta(ctrl, ROL_RECEPCION);
    expect(sesion.puede('gestionar_clientes')).toBe(true);

    const refresco = sesion.refrescar();
    const req = ctrl.expectOne(`${API}/auth/yo`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-1');
    req.flush({ usuario: { ...MARTA, nombre: 'Marta Elena' }, rol: { ...ROL_RECEPCION, permisos: ['ver_citas'] } });
    await refresco;

    expect(sesion.puede('gestionar_clientes')).toBe(false);
    expect(sesion.puede('ver_citas')).toBe(true);
    expect(sesion.usuario()?.nombre).toBe('Marta Elena');
    expect(tokens.leer()).toBe('jwt-1');
  });

  it('si /auth/yo responde 401 la sesión se cierra, avisa que el usuario ya no existe y navega una vez', async () => {
    const { ctrl, tokens, navegar } = configurar();
    const sesion = await entrarComoMarta(ctrl);

    const refresco = sesion.refrescar().catch((e: unknown) => e);
    ctrl.expectOne(`${API}/auth/yo`).flush(NO_AUTENTICADO, R401);
    await refresco;

    expect(sesion.usuario()).toBeNull();
    expect(tokens.leer()).toBeNull();
    expect(sesion.aviso()).toBe(SIN_USUARIO);
    expect(navegar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith('/login');
  });

  it('si la sesión cambió mientras esperaba, descarta la respuesta', async () => {
    const { ctrl } = configurar();
    const sesion = await entrarComoMarta(ctrl);
    const refresco = sesion.refrescar();
    const req = ctrl.expectOne(`${API}/auth/yo`);
    sesion.iniciar(ANDRES, ROL_RECEPCION);
    req.flush({ usuario: MARTA, rol: ROL_ADMIN });
    await refresco;

    expect(sesion.usuario()?.usuario).toBe('andres');
  });

  it('un fallo que no es 401 llega a quien llamó y no cierra la sesión', async () => {
    const { ctrl } = configurar();
    const sesion = await entrarComoMarta(ctrl);
    const refresco = sesion.refrescar().catch((e: unknown) => e);
    ctrl.expectOne(`${API}/auth/yo`).flush(
      { codigo: 'ERROR_INTERNO', mensaje: 'Algo falló.' }, { status: 500, statusText: 'Server Error' });

    expect(((await refresco) as Error).message).toBe('Algo falló.');
    expect(sesion.usuario()?.usuario).toBe('marta');
  });
});
