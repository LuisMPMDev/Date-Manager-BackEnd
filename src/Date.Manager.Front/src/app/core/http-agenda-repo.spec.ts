import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AgendaRepo } from './agenda-repo';
import { ApiError } from './api-error';
import { HttpAgendaRepo } from './http-agenda-repo';
import { authInterceptor, errorInterceptor } from './interceptores';
import { Cita, Cliente, Rol, Servicio, Usuario } from './models';

const API = 'http://localhost:8080/api';
const ID = '3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f';
const SIN_CONTENIDO = { status: 204, statusText: 'No Content' };

const SERVICIO: Servicio = {
  id: ID, nombre: 'Consulta', duracionMin: 30, activo: true, valor: 40000,
  ventanas: [{ id: 'a1b2c3d4-0000-4000-8000-000000000001', dia: 1, inicio: '09:00', fin: '12:00' }],
};
const CITA: Cita = {
  id: 'c1', servicioId: ID, clienteId: 'k1', fecha: '2026-11-10', inicio: '10:00', fin: '10:30',
  estado: 'Confirmada', creadaEn: '2026-11-09T12:00:00Z',
};
const CLIENTE: Cliente = { id: 'k1', nombre: 'Laura', apellido: 'Gómez', telefono: '300', correo: 'l@correo.com' };
const USUARIO: Usuario = {
  id: 'u1', nombre: 'Lucía', apellido: 'Mora', correo: 'lucia@x.com', telefono: '', usuario: 'lucia', rolId: 'r1',
};

function configurar() {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
      provideHttpClientTesting(),
      { provide: AgendaRepo, useClass: HttpAgendaRepo },
    ],
  });
  return { repo: TestBed.inject(AgendaRepo), ctrl: TestBed.inject(HttpTestingController) };
}

/** Responde la única petición esperada, tras comprobar verbo y cuerpo. */
function atender(ctrl: HttpTestingController, metodo: string, url: string, cuerpo: unknown, respuesta: object | null): TestRequest {
  const req = ctrl.expectOne(url);
  expect(req.request.method).toBe(metodo);
  expect(req.request.body).toEqual(cuerpo);
  req.flush(respuesta);
  return req;
}

describe('HttpAgendaRepo', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('servicios: GET /panel/servicios', async () => {
    const { repo, ctrl } = configurar();
    const p = repo.servicios();
    atender(ctrl, 'GET', `${API}/panel/servicios`, null, [SERVICIO]);
    expect(await p).toEqual([SERVICIO]);
  });

  it('guardarServicio: PUT /panel/servicios/{id} con las ventanas completas', async () => {
    const { repo, ctrl } = configurar();
    const p = repo.guardarServicio(SERVICIO);
    atender(ctrl, 'PUT', `${API}/panel/servicios/${ID}`, {
      nombre: 'Consulta', duracionMin: 30, activo: true, valor: 40000,
      ventanas: [{ id: 'a1b2c3d4-0000-4000-8000-000000000001', dia: 1, inicio: '09:00', fin: '12:00' }],
    }, SERVICIO);
    expect(await p).toEqual(SERVICIO);
  });

  it('horariosLibres: GET con fecha, y excluirCitaId solo si se indica', async () => {
    const { repo, ctrl } = configurar();
    const a = repo.horariosLibres(ID, '2026-11-10');
    atender(ctrl, 'GET', `${API}/panel/servicios/${ID}/horarios?fecha=2026-11-10`, null, ['09:00', '09:30']);
    expect(await a).toEqual(['09:00', '09:30']);

    const b = repo.horariosLibres(ID, '2026-11-10', 'c1');
    atender(ctrl, 'GET', `${API}/panel/servicios/${ID}/horarios?fecha=2026-11-10&excluirCitaId=c1`, null, ['10:00']);
    expect(await b).toEqual(['10:00']);
  });

  it('reservar: POST /panel/citas con el cliente anidado', async () => {
    const { repo, ctrl } = configurar();
    const cliente = { nombre: 'Laura', apellido: 'Gómez', telefono: '300', correo: 'l@correo.com' };
    const p = repo.reservar({ servicioId: ID, fecha: '2026-11-10', inicio: '10:00', cliente });
    atender(ctrl, 'POST', `${API}/panel/citas`, { servicioId: ID, fecha: '2026-11-10', inicio: '10:00', cliente }, CITA);
    expect(await p).toEqual(CITA);
  });

  it('citas: GET /panel/citas', async () => {
    const { repo, ctrl } = configurar();
    const p = repo.citas();
    atender(ctrl, 'GET', `${API}/panel/citas`, null, [CITA]);
    expect(await p).toEqual([CITA]);
  });

  it('cancelarCita y marcarAtendida: POST sin cuerpo a /cancelar y /atender', async () => {
    const { repo, ctrl } = configurar();
    const a = repo.cancelarCita('c1');
    atender(ctrl, 'POST', `${API}/panel/citas/c1/cancelar`, null, { ...CITA, estado: 'Cancelada' });
    expect((await a).estado).toBe('Cancelada');

    const b = repo.marcarAtendida('c1');
    atender(ctrl, 'POST', `${API}/panel/citas/c1/atender`, null, { ...CITA, estado: 'Atendida' });
    expect((await b).estado).toBe('Atendida');
  });

  it('moverCita: POST /panel/citas/{id}/mover con {fecha, inicio}', async () => {
    const { repo, ctrl } = configurar();
    const p = repo.moverCita('c1', '2026-11-11', '11:00');
    atender(ctrl, 'POST', `${API}/panel/citas/c1/mover`, { fecha: '2026-11-11', inicio: '11:00' }, { ...CITA, fecha: '2026-11-11' });
    expect((await p).fecha).toBe('2026-11-11');
  });

  it('clientes y guardarCliente: GET y PUT /panel/clientes', async () => {
    const { repo, ctrl } = configurar();
    const a = repo.clientes();
    atender(ctrl, 'GET', `${API}/panel/clientes`, null, [CLIENTE]);
    expect(await a).toEqual([CLIENTE]);

    const b = repo.guardarCliente(CLIENTE);
    atender(ctrl, 'PUT', `${API}/panel/clientes/k1`, {
      nombre: 'Laura', apellido: 'Gómez', telefono: '300', correo: 'l@correo.com',
    }, CLIENTE);
    expect(await b).toEqual(CLIENTE);
  });

  it('roles: GET /panel/roles', async () => {
    const { repo, ctrl } = configurar();
    const p = repo.roles();
    atender(ctrl, 'GET', `${API}/panel/roles`, null, [{ id: 'r1', nombre: 'Admin', descripcion: 'Todo', permisos: ['ver_panel'] }]);
    const roles: Rol[] = await p;
    expect(roles[0].permisos).toEqual(['ver_panel']);
  });

  it('guardarRol: PUT /panel/roles/{id} con {nombre, descripcion, permisos}, sin actor', async () => {
    const { repo, ctrl } = configurar();
    const rol: Rol = { id: 'r1', nombre: 'Auxiliar', descripcion: 'Apoyo', permisos: ['atender_citas', 'ver_citas'] };
    const p = repo.guardarRol(rol);
    atender(ctrl, 'PUT', `${API}/panel/roles/r1`, {
      nombre: 'Auxiliar', descripcion: 'Apoyo', permisos: ['atender_citas', 'ver_citas'],
    }, rol);
    expect(await p).toEqual(rol);
  });

  it('eliminarRol: DELETE /panel/roles/{id}', async () => {
    const { repo, ctrl } = configurar();
    const p = repo.eliminarRol('r1');
    const req = ctrl.expectOne(`${API}/panel/roles/r1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, SIN_CONTENIDO);
    await expect(p).resolves.toBeUndefined();
  });

  it('usuarios y eliminarUsuario: GET y DELETE /panel/usuarios', async () => {
    const { repo, ctrl } = configurar();
    const a = repo.usuarios();
    atender(ctrl, 'GET', `${API}/panel/usuarios`, null, [USUARIO]);
    expect(await a).toEqual([USUARIO]);

    const b = repo.eliminarUsuario('u1');
    const req = ctrl.expectOne(`${API}/panel/usuarios/u1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, SIN_CONTENIDO);
    await expect(b).resolves.toBeUndefined();
  });

  describe('guardarUsuario', () => {
    const base = {
      nombre: 'Lucía', apellido: 'Mora', correo: 'lucia@x.com', telefono: '', usuario: 'lucia', rolId: 'r1',
    };

    it('PUT /panel/usuarios/{id}: sin clave (ausente o vacía) no envía la propiedad', async () => {
      const { repo, ctrl } = configurar();
      for (const clave of [undefined, '']) {
        const p = repo.guardarUsuario({ ...USUARIO, clave });
        const req = atender(ctrl, 'PUT', `${API}/panel/usuarios/u1`, base, USUARIO);
        expect(Object.keys(req.request.body)).not.toContain('clave');
        expect(await p).toEqual(USUARIO);
      }
    });

    it('con clave con texto, la envía', async () => {
      const { repo, ctrl } = configurar();
      const p = repo.guardarUsuario({ ...USUARIO, clave: 'nueva-1' });
      atender(ctrl, 'PUT', `${API}/panel/usuarios/u1`, { ...base, clave: 'nueva-1' }, USUARIO);
      await p;
    });
  });

  describe('cambios', () => {
    it('sube tras cada escritura exitosa y no tras las lecturas', async () => {
      const { repo, ctrl } = configurar();
      expect(repo.cambios()).toBe(0);

      const lectura = repo.servicios();
      atender(ctrl, 'GET', `${API}/panel/servicios`, null, []);
      await lectura;
      expect(repo.cambios()).toBe(0);

      const guardar = repo.guardarServicio(SERVICIO);
      ctrl.expectOne(`${API}/panel/servicios/${ID}`).flush(SERVICIO);
      await guardar;
      expect(repo.cambios()).toBe(1);

      const cancelar = repo.cancelarCita('c1');
      ctrl.expectOne(`${API}/panel/citas/c1/cancelar`).flush(CITA);
      await cancelar;
      expect(repo.cambios()).toBe(2);

      const borrar = repo.eliminarUsuario('u1');
      ctrl.expectOne(`${API}/panel/usuarios/u1`).flush(null, SIN_CONTENIDO);
      await borrar;
      expect(repo.cambios()).toBe(3);
    });

    it('no sube tras una escritura que falla', async () => {
      const { repo, ctrl } = configurar();
      const p = repo.guardarServicio(SERVICIO);
      ctrl.expectOne(`${API}/panel/servicios/${ID}`).flush(
        { codigo: 'VALIDACION', mensaje: 'Datos inválidos' }, { status: 400, statusText: 'Bad Request' },
      );
      await expect(p).rejects.toBeInstanceOf(ApiError);
      expect(repo.cambios()).toBe(0);
    });
  });

  it('un 409 HORARIO_NO_DISPONIBLE llega como ApiError con ese código', async () => {
    const { repo, ctrl } = configurar();
    const p = repo.moverCita('c1', '2026-11-11', '11:00');
    ctrl.expectOne(`${API}/panel/citas/c1/mover`).flush(
      { codigo: 'HORARIO_NO_DISPONIBLE', mensaje: 'Ese horario ya no está disponible.' },
      { status: 409, statusText: 'Conflict' },
    );
    const e = (await p.catch(x => x)) as ApiError;
    expect(e).toBeInstanceOf(ApiError);
    expect(e.codigo).toBe('HORARIO_NO_DISPONIBLE');
    expect(e.status).toBe(409);
    expect(repo.cambios()).toBe(0);
  });
});
