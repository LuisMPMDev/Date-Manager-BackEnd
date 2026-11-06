import { TestBed } from '@angular/core/testing';
import { AgendaRepo } from './agenda-repo';
import { DemoAgendaRepo } from './demo-agenda-repo';
import { RELOJ } from './errores';
import { sembrar } from './seed';
import { SesionService } from './sesion';

const AHORA = new Date(2026, 10, 9, 8, 0);
const CLAVE_SESION = 'datemanager.sesion.v1';

function configurar() {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: RELOJ, useValue: () => AHORA },
      { provide: AgendaRepo, useClass: DemoAgendaRepo },
    ],
  });
}

describe('SesionService.refrescar', () => {
  const datos = sembrar(AHORA);
  const andres = datos.usuarios.find(u => u.usuario === 'andres')!;
  const marta = datos.usuarios.find(u => u.usuario === 'marta')!;
  const recepcion = datos.roles.find(r => r.id === 'rol-recepcion')!;

  beforeEach(() => configurar());

  const repo = () => TestBed.inject(AgendaRepo);

  it('si el rol del usuario se edita, sus permisos cambian al refrescar', async () => {
    const sesion = TestBed.inject(SesionService);
    sesion.iniciar(andres, datos.roles);
    expect(sesion.puede('gestionar_clientes')).toBe(true);

    await repo().guardarRol({ ...recepcion, permisos: ['ver_citas'] }, marta.id);
    expect(sesion.puede('gestionar_clientes')).toBe(true); // aún no se refresca
    await sesion.refrescar();

    expect(sesion.puede('gestionar_clientes')).toBe(false);
    expect(sesion.puede('ver_citas')).toBe(true);
  });

  it('si al usuario le cambian el rol, el rol de la sesión cambia al refrescar', async () => {
    const sesion = TestBed.inject(SesionService);
    sesion.iniciar(andres, datos.roles);
    await repo().guardarUsuario({ ...andres, rolId: 'rol-admin' });
    await sesion.refrescar();

    expect(sesion.usuario()?.rolId).toBe('rol-admin');
    expect(sesion.rol()?.id).toBe('rol-admin');
    expect(sesion.puede('gestionar_roles')).toBe(true);
  });

  it('si el usuario ya no existe, cierra la sesión', async () => {
    const sesion = TestBed.inject(SesionService);
    sesion.iniciar(andres, datos.roles);
    await repo().eliminarUsuario(andres.id);
    await sesion.refrescar();

    expect(sesion.usuario()).toBeNull();
    expect(sesion.rol()).toBeUndefined();
    expect(sessionStorage.getItem(CLAVE_SESION)).toBeNull();
  });

  it('sin sesión no hace nada', async () => {
    const sesion = TestBed.inject(SesionService);
    await sesion.refrescar();
    expect(sesion.usuario()).toBeNull();
  });

  it('nunca guarda la clave en sessionStorage, ni al iniciar ni al refrescar', async () => {
    const sesion = TestBed.inject(SesionService);
    sesion.iniciar(andres, datos.roles);
    expect(sessionStorage.getItem(CLAVE_SESION)).not.toContain('demo123');
    expect(JSON.parse(sessionStorage.getItem(CLAVE_SESION)!)).toMatchObject({ id: andres.id, clave: '' });

    await repo().guardarUsuario({ ...andres, nombre: 'Andrés Felipe' });
    await sesion.refrescar();
    const guardado = sessionStorage.getItem(CLAVE_SESION)!;
    expect(guardado).not.toContain('demo123');
    expect(JSON.parse(guardado)).toMatchObject({ id: andres.id, nombre: 'Andrés Felipe', clave: '' });
  });
});

describe('SesionService: sesión restaurada', () => {
  beforeEach(() => configurar());

  it('restaura una sesión guardada con la forma esperada', async () => {
    sessionStorage.setItem(CLAVE_SESION, JSON.stringify({ id: 'usr-andres', rolId: 'rol-recepcion', clave: '' }));
    const sesion = TestBed.inject(SesionService);
    await sesion.listo;
    expect(sesion.usuario()?.id).toBe('usr-andres');
    expect(sesion.rol()?.id).toBe('rol-recepcion');
  });

  it.each([
    ['un texto', '"hola"'],
    ['un arreglo', '[]'],
    ['sin rolId', '{"id":"usr-andres"}'],
    ['con id que no es texto', '{"id":5,"rolId":"rol-admin"}'],
    ['JSON dañado', '{no es json'],
  ])('descarta una sesión guardada inválida (%s)', async (_nombre, crudo) => {
    sessionStorage.setItem(CLAVE_SESION, crudo);
    const sesion = TestBed.inject(SesionService);
    await sesion.listo;
    expect(sesion.usuario()).toBeNull();
    expect(sessionStorage.getItem(CLAVE_SESION)).toBeNull();
  });
});
