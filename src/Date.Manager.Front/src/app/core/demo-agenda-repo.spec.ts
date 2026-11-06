import { TestBed } from '@angular/core/testing';
import { AgendaRepo, DatosCliente } from './agenda-repo';
import { DemoAgendaRepo } from './demo-agenda-repo';
import { CambioNoPermitidoError, HorarioNoDisponibleError, RELOJ, RolEnUsoError } from './errores';
import { horariosLibres, sumarMinutos } from './horarios';
import { Servicio } from './models';
import { sembrar } from './seed';

const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 2026-11-09 08:00 local
const FECHA = '2026-11-23'; // lunes, fuera del rango de citas de ejemplo
const CLAVE = 'datemanager.demo.v1';

const CLIENTE: DatosCliente = { nombre: 'Laura', apellido: 'Gómez', telefono: '3001112233', correo: 'laura@correo.com' };

function crearRepo(): DemoAgendaRepo {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [{ provide: RELOJ, useValue: () => AHORA }, DemoAgendaRepo] });
  return TestBed.inject(DemoAgendaRepo);
}

async function servicioPorNombre(repo: AgendaRepo, nombre: string): Promise<Servicio> {
  return (await repo.servicios()).find(s => s.nombre === nombre)!;
}

describe('DemoAgendaRepo', () => {
  let repo: DemoAgendaRepo;

  beforeEach(() => {
    localStorage.clear();
    repo = crearRepo();
  });

  it('AgendaRepo se resuelve a DemoAgendaRepo cuando se provee', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: RELOJ, useValue: () => AHORA }, { provide: AgendaRepo, useClass: DemoAgendaRepo }],
    });
    expect(TestBed.inject(AgendaRepo) instanceof DemoAgendaRepo).toBe(true);
  });

  it('reservar crea cita Confirmada con fin = inicio + duración', async () => {
    const estandar = await servicioPorNombre(repo, 'Sesión estándar');
    const [inicio] = await repo.horariosLibres(estandar.id, FECHA);
    const cita = await repo.reservar({ servicioId: estandar.id, fecha: FECHA, inicio, cliente: CLIENTE });
    expect(cita.estado).toBe('Confirmada');
    expect(cita.fecha).toBe(FECHA);
    expect(cita.inicio).toBe(inicio);
    expect(cita.fin).toBe(sumarMinutos(inicio, 60));
    expect(cita.creadaEn).toBe(AHORA.toISOString());
    expect((await repo.citas()).some(c => c.id === cita.id)).toBe(true);
  });

  it('la segunda reserva del mismo horario lanza HorarioNoDisponibleError; la de otro servicio a la misma hora sí funciona', async () => {
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    const estandar = await servicioPorNombre(repo, 'Sesión estándar');
    const a = await repo.horariosLibres(consulta.id, FECHA);
    const b = await repo.horariosLibres(estandar.id, FECHA);
    const inicio = a.find(h => b.includes(h))!;
    await repo.reservar({ servicioId: consulta.id, fecha: FECHA, inicio, cliente: CLIENTE });
    await expect(repo.reservar({ servicioId: consulta.id, fecha: FECHA, inicio, cliente: CLIENTE })).rejects.toBeInstanceOf(
      HorarioNoDisponibleError,
    );
    await expect(repo.reservar({ servicioId: consulta.id, fecha: FECHA, inicio, cliente: CLIENTE })).rejects.toThrow(
      'Ese horario ya no está disponible, elige otro.',
    );
    const otra = await repo.reservar({ servicioId: estandar.id, fecha: FECHA, inicio, cliente: CLIENTE });
    expect(otra.servicioId).toBe(estandar.id);
    expect(otra.inicio).toBe(inicio);
  });

  it('reservar en servicio inactivo lanza HorarioNoDisponibleError', async () => {
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    const [inicio] = await repo.horariosLibres(consulta.id, FECHA);
    await repo.guardarServicio({ ...consulta, activo: false });
    await expect(repo.reservar({ servicioId: consulta.id, fecha: FECHA, inicio, cliente: CLIENTE })).rejects.toBeInstanceOf(
      HorarioNoDisponibleError,
    );
  });

  it('reutiliza el cliente por correo sin distinguir mayúsculas y actualiza el teléfono', async () => {
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    const [h1] = await repo.horariosLibres(consulta.id, FECHA);
    const primera = await repo.reservar({ servicioId: consulta.id, fecha: FECHA, inicio: h1, cliente: CLIENTE });
    const total = (await repo.clientes()).length;
    const [h2] = await repo.horariosLibres(consulta.id, FECHA);
    const segunda = await repo.reservar({
      servicioId: consulta.id,
      fecha: FECHA,
      inicio: h2,
      cliente: { ...CLIENTE, correo: 'LAURA@Correo.COM', telefono: '3999888777' },
    });
    expect(segunda.clienteId).toBe(primera.clienteId);
    const clientes = await repo.clientes();
    expect(clientes.length).toBe(total);
    expect(clientes.find(c => c.id === primera.clienteId)!.telefono).toBe('3999888777');
  });

  it('cancelarCita libera el horario y fija canceladaEn', async () => {
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    const [inicio] = await repo.horariosLibres(consulta.id, FECHA);
    const cita = await repo.reservar({ servicioId: consulta.id, fecha: FECHA, inicio, cliente: CLIENTE });
    expect(await repo.horariosLibres(consulta.id, FECHA)).not.toContain(inicio);
    const cancelada = await repo.cancelarCita(cita.id);
    expect(cancelada.estado).toBe('Cancelada');
    expect(cancelada.canceladaEn).toBe(AHORA.toISOString());
    expect(await repo.horariosLibres(consulta.id, FECHA)).toContain(inicio);
  });

  it('marcarAtendida cambia el estado a Atendida', async () => {
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    const [inicio] = await repo.horariosLibres(consulta.id, FECHA);
    const cita = await repo.reservar({ servicioId: consulta.id, fecha: FECHA, inicio, cliente: CLIENTE });
    expect((await repo.marcarAtendida(cita.id)).estado).toBe('Atendida');
    expect((await repo.citas()).find(c => c.id === cita.id)!.estado).toBe('Atendida');
  });

  it('moverCita a su propio horario es válido; a uno ocupado lanza HorarioNoDisponibleError', async () => {
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    const [h1] = await repo.horariosLibres(consulta.id, FECHA);
    const a = await repo.reservar({ servicioId: consulta.id, fecha: FECHA, inicio: h1, cliente: CLIENTE });
    const [h2] = await repo.horariosLibres(consulta.id, FECHA);
    const b = await repo.reservar({ servicioId: consulta.id, fecha: FECHA, inicio: h2, cliente: CLIENTE });

    const mismo = await repo.moverCita(a.id, FECHA, h1);
    expect(mismo.inicio).toBe(h1);
    await expect(repo.moverCita(b.id, FECHA, h1)).rejects.toBeInstanceOf(HorarioNoDisponibleError);

    const [libre] = await repo.horariosLibres(consulta.id, FECHA);
    const movida = await repo.moverCita(b.id, FECHA, libre);
    expect(movida.inicio).toBe(libre);
    expect(movida.fin).toBe(sumarMinutos(libre, 30));
  });

  it('cambiar la duración de un servicio no modifica citas existentes', async () => {
    const estandar = await servicioPorNombre(repo, 'Sesión estándar');
    const [inicio] = await repo.horariosLibres(estandar.id, FECHA);
    const cita = await repo.reservar({ servicioId: estandar.id, fecha: FECHA, inicio, cliente: CLIENTE });
    await repo.guardarServicio({ ...estandar, duracionMin: 90 });
    const guardada = (await repo.citas()).find(c => c.id === cita.id)!;
    expect(guardada.inicio).toBe(cita.inicio);
    expect(guardada.fin).toBe(sumarMinutos(inicio, 60));
    expect((await servicioPorNombre(repo, 'Sesión estándar')).duracionMin).toBe(90);
  });

  it('eliminarRol con usuarios lanza RolEnUsoError', async () => {
    const admin = (await repo.roles()).find(r => r.nombre === 'Administrador')!;
    await expect(repo.eliminarRol(admin.id)).rejects.toBeInstanceOf(RolEnUsoError);
    expect((await repo.roles()).length).toBe(2);
  });

  it('eliminarRol sin usuarios lo elimina', async () => {
    const marta = (await repo.usuarios()).find(u => u.usuario === 'marta')!;
    const nuevo = await repo.guardarRol({ id: 'rol-nuevo', nombre: 'Auxiliar', descripcion: '', permisos: ['ver_citas'] }, marta.id);
    expect((await repo.roles()).length).toBe(3);
    await repo.eliminarRol(nuevo.id);
    expect((await repo.roles()).map(r => r.nombre)).toEqual(['Administrador', 'Recepción']);
  });

  it('guardarRol quitando gestionar_roles al rol del actor lanza CambioNoPermitidoError', async () => {
    const admin = (await repo.roles()).find(r => r.nombre === 'Administrador')!;
    const marta = (await repo.usuarios()).find(u => u.usuario === 'marta')!;
    const sinRoles = { ...admin, permisos: admin.permisos.filter(p => p !== 'gestionar_roles') };
    await expect(repo.guardarRol(sinRoles, marta.id)).rejects.toBeInstanceOf(CambioNoPermitidoError);
    await expect(repo.guardarRol(sinRoles, marta.id)).rejects.toThrow(
      'No puedes quitarte el permiso de gestionar roles de tu propio rol.',
    );
    expect((await repo.roles()).find(r => r.id === admin.id)!.permisos).toContain('gestionar_roles');
  });

  it('guardarRol normaliza dependencias y permite crear un rol nuevo', async () => {
    const marta = (await repo.usuarios()).find(u => u.usuario === 'marta')!;
    const guardado = await repo.guardarRol(
      { id: 'rol-x', nombre: 'Auxiliar', descripcion: 'Apoyo', permisos: ['atender_citas'] },
      marta.id,
    );
    expect(guardado.permisos).toEqual(['atender_citas', 'ver_citas']);
    expect((await repo.roles()).find(r => r.id === 'rol-x')!.permisos).toEqual(['atender_citas', 'ver_citas']);
  });

  it('iniciarSesion devuelve el usuario con credenciales correctas y null con incorrectas', async () => {
    const ok = await repo.iniciarSesion('marta', 'demo123');
    expect(ok!.usuario).toBe('marta');
    expect((await repo.iniciarSesion('andres', 'demo123'))!.usuario).toBe('andres');
    expect(await repo.iniciarSesion('marta', 'otra')).toBeNull();
    expect(await repo.iniciarSesion('nadie', 'demo123')).toBeNull();
  });

  it('iniciarSesion ignora mayúsculas y espacios del usuario, pero la clave es exacta', async () => {
    expect((await repo.iniciarSesion('  MARTA ', 'demo123'))!.usuario).toBe('marta');
    expect(await repo.iniciarSesion('  MARTA ', 'DEMO123')).toBeNull();
    expect(await repo.iniciarSesion('MARTA', 'otra')).toBeNull();
  });

  it('cambios sube con cada escritura exitosa y no con las lecturas', async () => {
    expect(repo.cambios()).toBe(0);
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    await repo.guardarServicio({ ...consulta, valor: 45000 });
    expect(repo.cambios()).toBe(1);
    await repo.reiniciar();
    expect(repo.cambios()).toBe(2);
    await repo.servicios();
    expect(repo.cambios()).toBe(2);
  });

  it('persiste en localStorage y un repositorio nuevo lo recupera', async () => {
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    await repo.guardarServicio({ ...consulta, valor: 45000 });
    const guardado = JSON.parse(localStorage.getItem(CLAVE)!);
    expect(guardado.servicios.find((s: Servicio) => s.id === consulta.id).valor).toBe(45000);
    const otro = crearRepo();
    expect((await servicioPorNombre(otro, 'Consulta inicial')).valor).toBe(45000);
  });

  it('localStorage con JSON corrupto cae en los datos de ejemplo', async () => {
    localStorage.setItem(CLAVE, '{');
    const nuevo = crearRepo();
    expect((await nuevo.servicios()).map(s => s.nombre)).toEqual(['Consulta inicial', 'Sesión estándar', 'Sesión extendida']);
    localStorage.setItem(CLAVE, '{"servicios":[]}');
    expect((await crearRepo().roles()).length).toBe(2);
  });

  it('reiniciar restaura los datos de ejemplo', async () => {
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    const [inicio] = await repo.horariosLibres(consulta.id, FECHA);
    await repo.reservar({ servicioId: consulta.id, fecha: FECHA, inicio, cliente: CLIENTE });
    await repo.guardarServicio({ ...consulta, valor: 1 });
    await repo.reiniciar();
    const semilla = sembrar(AHORA);
    expect(await repo.citas()).toEqual(semilla.citas);
    expect(await repo.servicios()).toEqual(semilla.servicios);
    expect(await repo.clientes()).toEqual(semilla.clientes);
  });
});

describe('sembrar', () => {
  const datos = sembrar(AHORA);
  const medianoche = (fecha: string) => {
    const [y, m, d] = fecha.split('-').map(Number);
    return new Date(y, m - 1, d);
  };

  it('define roles, usuarios, servicios y clientes esperados', () => {
    expect(datos.roles.map(r => r.nombre)).toEqual(['Administrador', 'Recepción']);
    expect(datos.roles[0].permisos.length).toBe(8);
    expect(datos.roles[1].permisos).toEqual(['ver_citas', 'gestionar_citas', 'atender_citas', 'gestionar_clientes']);
    expect(datos.usuarios.map(u => [u.usuario, u.clave, u.rolId])).toEqual([
      ['marta', 'demo123', datos.roles[0].id],
      ['andres', 'demo123', datos.roles[1].id],
    ]);
    expect(datos.servicios.map(s => [s.nombre, s.duracionMin, s.valor])).toEqual([
      ['Consulta inicial', 30, 40000],
      ['Sesión estándar', 60, 80000],
      ['Sesión extendida', 90, 110000],
    ]);
    expect(datos.clientes.length).toBe(8);
  });

  it('los datos de ejemplo son coherentes', () => {
    const vivas = datos.citas.filter(c => c.estado !== 'Cancelada');
    expect(vivas.length).toBeGreaterThan(10);
    for (const cita of vivas) {
      const servicio = datos.servicios.find(s => s.id === cita.servicioId)!;
      const libres = horariosLibres({
        servicio,
        fecha: cita.fecha,
        citas: datos.citas,
        ahora: medianoche(cita.fecha),
        excluirCitaId: cita.id,
      });
      expect(libres, `${cita.id} ${cita.fecha} ${cita.inicio}`).toContain(cita.inicio);
    }
  });

  it('las citas no se cruzan y referencian servicios y clientes existentes', () => {
    for (const a of datos.citas) {
      expect(datos.servicios.some(s => s.id === a.servicioId)).toBe(true);
      expect(datos.clientes.some(c => c.id === a.clienteId)).toBe(true);
      for (const b of datos.citas) {
        if (a.id >= b.id || a.servicioId !== b.servicioId || a.fecha !== b.fecha) continue;
        expect(a.inicio < b.fin && a.fin > b.inicio, `${a.id} cruza ${b.id}`).toBe(false);
      }
    }
  });

  it('cubre hoy, creadas y canceladas recientes y atendidas pasadas', () => {
    const hace24h = AHORA.getTime() - 24 * 3600 * 1000;
    expect(datos.citas.some(c => c.fecha === '2026-11-09')).toBe(true);
    expect(datos.citas.some(c => new Date(c.creadaEn).getTime() >= hace24h && c.estado !== 'Cancelada')).toBe(true);
    expect(datos.citas.some(c => c.canceladaEn && new Date(c.canceladaEn).getTime() >= hace24h)).toBe(true);
    expect(datos.citas.filter(c => c.estado === 'Atendida' && c.fecha < '2026-11-09').length).toBeGreaterThanOrEqual(3);
    const fechas = datos.citas.map(c => c.fecha).sort();
    expect(fechas[0] >= '2026-10-19').toBe(true);
    expect(fechas[fechas.length - 1] <= '2026-11-19').toBe(true);
  });
});
