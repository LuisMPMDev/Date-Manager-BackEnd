import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { conPermiso, conSesion, sinSesion } from './guards';
import { authInterceptor, errorInterceptor } from './interceptores';
import { ANDRES, MARTA, ROL_ADMIN, ROL_RECEPCION } from './sesion.pruebas';
import { SesionService } from './sesion';

const API = 'http://localhost:8080/api';
const ESTADO = {} as RouterStateSnapshot;

function ruta(permisos?: string[]): ActivatedRouteSnapshot {
  return { data: permisos ? { permisos } : {} } as unknown as ActivatedRouteSnapshot;
}

function configurar() {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
      provideHttpClientTesting(),
    ],
  });
}

describe('guardas', () => {
  let sesion: SesionService;

  beforeEach(() => {
    sessionStorage.clear();
    configurar();
    sesion = TestBed.inject(SesionService);
  });

  const correr = async (guarda: typeof conSesion, r: ActivatedRouteSnapshot) =>
    await TestBed.runInInjectionContext(() => guarda(r, ESTADO));

  it('sin sesión redirige a /login', async () => {
    const res = await correr(conSesion, ruta());
    expect(res instanceof UrlTree).toBe(true);
    expect(String(res)).toBe('/login');
  });

  it('con sesión deja pasar', async () => {
    sesion.iniciar(MARTA, ROL_ADMIN);
    expect(await correr(conSesion, ruta())).toBe(true);
  });

  it('rol sin ver_panel que abre /panel va a su primera ruta', async () => {
    sesion.iniciar(ANDRES, ROL_RECEPCION);
    const res = await correr(conPermiso, ruta(['ver_panel']));
    expect(String(res)).toBe('/agenda');
  });

  it('rol con el permiso entra', async () => {
    sesion.iniciar(MARTA, ROL_ADMIN);
    expect(await correr(conPermiso, ruta(['ver_panel']))).toBe(true);
  });

  it('basta con uno de los permisos de la ruta', async () => {
    sesion.iniciar(MARTA, ROL_ADMIN);
    expect(await correr(conPermiso, ruta(['gestionar_usuarios', 'gestionar_roles']))).toBe(true);
  });

  it('un rol sin ningún permiso termina en /sin-acceso', async () => {
    sesion.iniciar({ ...MARTA, rolId: 'rol-vacio' }, { id: 'rol-vacio', nombre: 'Vacío', descripcion: '', permisos: [] });
    expect(String(await correr(conPermiso, ruta(['ver_panel'])))).toBe('/sin-acceso');
  });

  it('sinSesion deja ver el login sin sesión y redirige con sesión', async () => {
    expect(await correr(sinSesion, ruta())).toBe(true);
    sesion.iniciar(ANDRES, ROL_RECEPCION);
    expect(String(await correr(sinSesion, ruta()))).toBe('/agenda');
  });

  describe('con una sesión por restaurar (token guardado en la pestaña)', () => {
    function recargar() {
      sessionStorage.setItem('datemanager.sesion.v2', JSON.stringify({ token: 'jwt-1' }));
      configurar(); // la pestaña se recarga: los servicios nacen de nuevo y el token sigue en sessionStorage
      TestBed.inject(SesionService); // al nacer pide /auth/yo con el token guardado
      return { ctrl: TestBed.inject(HttpTestingController) };
    }

    it('conSesion espera a /auth/yo y deja pasar si responde bien', async () => {
      const { ctrl } = recargar();
      let resuelto = false;
      const res = correr(conSesion, ruta()).then(r => ((resuelto = true), r));
      await Promise.resolve();
      expect(resuelto).toBe(false);

      ctrl.expectOne(`${API}/auth/yo`).flush({ usuario: ANDRES, rol: ROL_RECEPCION });
      expect(await res).toBe(true);
    });

    it('conSesion manda a /login si /auth/yo responde 401', async () => {
      const { ctrl } = recargar();
      vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      const res = correr(conSesion, ruta());
      ctrl.expectOne(`${API}/auth/yo`).flush(
        { codigo: 'NO_AUTENTICADO', mensaje: 'x' }, { status: 401, statusText: 'Unauthorized' });
      expect(String(await res)).toBe('/login');
    });

    it('sinSesion también espera: con sesión restaurada manda a la primera ruta del rol', async () => {
      const { ctrl } = recargar();
      const res = correr(sinSesion, ruta());
      ctrl.expectOne(`${API}/auth/yo`).flush({ usuario: ANDRES, rol: ROL_RECEPCION });
      expect(String(await res)).toBe('/agenda');
    });

    it('conPermiso espera y usa el rol restaurado', async () => {
      const { ctrl } = recargar();
      const res = correr(conPermiso, ruta(['ver_panel']));
      ctrl.expectOne(`${API}/auth/yo`).flush({ usuario: ANDRES, rol: ROL_RECEPCION });
      expect(String(await res)).toBe('/agenda');
    });
  });
});
