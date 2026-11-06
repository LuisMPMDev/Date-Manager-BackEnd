import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AgendaRepo } from './agenda-repo';
import { DemoAgendaRepo } from './demo-agenda-repo';
import { RELOJ } from './errores';
import { conPermiso, conSesion, sinSesion } from './guards';
import { sembrar } from './seed';
import { SesionService } from './sesion';

const AHORA = new Date(2026, 10, 9, 8, 0);
const ESTADO = {} as RouterStateSnapshot;

function ruta(permisos?: string[]): ActivatedRouteSnapshot {
  return { data: permisos ? { permisos } : {} } as unknown as ActivatedRouteSnapshot;
}

function configurar() {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: RELOJ, useValue: () => AHORA },
      { provide: AgendaRepo, useClass: DemoAgendaRepo },
    ],
  });
}

describe('guardas', () => {
  const datos = sembrar(AHORA);
  const admin = datos.usuarios.find(u => u.usuario === 'marta')!;
  const recepcion = datos.usuarios.find(u => u.usuario === 'andres')!;
  let sesion: SesionService;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    configurar();
    sesion = TestBed.inject(SesionService);
  });

  const correr = (guarda: typeof conSesion, r: ActivatedRouteSnapshot) =>
    TestBed.runInInjectionContext(() => guarda(r, ESTADO));

  it('sin sesión redirige a /login', async () => {
    const res = await correr(conSesion, ruta());
    expect(res instanceof UrlTree).toBe(true);
    expect(String(res)).toBe('/login');
  });

  it('con sesión deja pasar', async () => {
    sesion.iniciar(admin, datos.roles);
    expect(await correr(conSesion, ruta())).toBe(true);
  });

  it('rol sin ver_panel que abre /panel va a su primera ruta', async () => {
    sesion.iniciar(recepcion, datos.roles);
    const res = await correr(conPermiso, ruta(['ver_panel']));
    expect(String(res)).toBe('/agenda');
  });

  it('rol con el permiso entra', async () => {
    sesion.iniciar(admin, datos.roles);
    expect(await correr(conPermiso, ruta(['ver_panel']))).toBe(true);
  });

  it('basta con uno de los permisos de la ruta', async () => {
    sesion.iniciar(admin, datos.roles);
    expect(await correr(conPermiso, ruta(['gestionar_usuarios', 'gestionar_roles']))).toBe(true);
  });

  it('un rol sin ningún permiso termina en /sin-acceso', async () => {
    sesion.iniciar({ ...admin, rolId: 'rol-vacio' }, [
      { id: 'rol-vacio', nombre: 'Vacío', descripcion: '', permisos: [] },
    ]);
    expect(String(await correr(conPermiso, ruta(['ver_panel'])))).toBe('/sin-acceso');
  });

  it('sinSesion deja ver el login sin sesión y redirige con sesión', async () => {
    expect(await correr(sinSesion, ruta())).toBe(true);
    sesion.iniciar(recepcion, datos.roles);
    expect(String(await correr(sinSesion, ruta()))).toBe('/agenda');
  });

  describe('SesionService', () => {
    it('deriva el rol del usuario y los roles recibidos', () => {
      sesion.iniciar(recepcion, datos.roles);
      expect(sesion.rol()?.nombre).toBe('Recepción');
      expect(sesion.puede('ver_citas')).toBe(true);
      expect(sesion.puede('ver_panel')).toBe(false);
    });

    it('cerrar limpia la sesión', () => {
      sesion.iniciar(admin, datos.roles);
      sesion.cerrar();
      expect(sesion.usuario()).toBeNull();
      expect(sesion.rol()).toBeUndefined();
      expect(sessionStorage.length).toBe(0);
    });

    it('restaura la sesión de la pestaña tras recargar, con los roles actuales', async () => {
      sesion.iniciar(recepcion, datos.roles);
      configurar();
      const nueva = TestBed.inject(SesionService);
      await nueva.listo;
      expect(nueva.usuario()?.usuario).toBe('andres');
      expect(nueva.rol()?.id).toBe('rol-recepcion');
    });
  });
});
