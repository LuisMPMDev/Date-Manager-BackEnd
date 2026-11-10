import { createEnvironmentInjector, EnvironmentInjector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AgendaRepo, DatosCliente } from '../core/agenda-repo';
import { DemoAgendaRepo } from './demo-agenda-repo';
import { RELOJ } from '../core/reloj';
import { HorarioNoDisponibleError, RolEnUsoError } from './errores-demo';
import { horariosLibres, sumarMinutos } from '../core/horarios';
import { Servicio } from '../core/models';
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

/** Otro repositorio sobre el mismo almacén, como una segunda pestaña del navegador. */
function crearRepoAparte(reloj: () => Date = () => AHORA): DemoAgendaRepo {
  const entorno = createEnvironmentInjector(
    [{ provide: RELOJ, useValue: reloj }, DemoAgendaRepo],
    TestBed.inject(EnvironmentInjector),
  );
  return entorno.get(DemoAgendaRepo);
}

function avisarCambioDeAlmacen(): void {
  window.dispatchEvent(new StorageEvent('storage', { key: CLAVE }));
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

  it('en un servicio inactivo no hay horarios para reservar, pero su cita sí se puede mover', async () => {
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    const [h1] = await repo.horariosLibres(consulta.id, FECHA);
    const cita = await repo.reservar({ servicioId: consulta.id, fecha: FECHA, inicio: h1, cliente: CLIENTE });
    await repo.guardarServicio({ ...consulta, activo: false });

    expect(await repo.horariosLibres(consulta.id, FECHA)).toEqual([]);
    const paraMover = await repo.horariosLibres(consulta.id, FECHA, cita.id);
    expect(paraMover).toContain(h1);
    const otro = paraMover.find(h => h !== h1)!;
    const movida = await repo.moverCita(cita.id, FECHA, otro);
    expect(movida.inicio).toBe(otro);
    expect(movida.fin).toBe(sumarMinutos(otro, 30));
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
    const nuevo = await repo.guardarRol({ id: 'rol-nuevo', nombre: 'Auxiliar', descripcion: '', permisos: ['ver_citas'] });
    expect((await repo.roles()).length).toBe(3);
    await repo.eliminarRol(nuevo.id);
    expect((await repo.roles()).map(r => r.nombre)).toEqual(['Administrador', 'Recepción']);
  });

  it('guardarRol normaliza dependencias y permite crear un rol nuevo', async () => {
    const guardado = await repo.guardarRol({ id: 'rol-x', nombre: 'Auxiliar', descripcion: 'Apoyo', permisos: ['atender_citas'] });
    expect(guardado.permisos).toEqual(['atender_citas', 'ver_citas']);
    expect((await repo.roles()).find(r => r.id === 'rol-x')!.permisos).toEqual(['atender_citas', 'ver_citas']);
  });

  it('guardarUsuario no conserva la clave que trae el formulario', async () => {
    const guardado = await repo.guardarUsuario({
      id: 'usr-x', nombre: 'Lucía', apellido: 'Mora', correo: 'lucia@datemanager.demo', telefono: '',
      usuario: 'lucia', clave: 'clave1', rolId: 'rol-recepcion',
    });
    expect(guardado).not.toHaveProperty('clave');
    expect((await repo.usuarios()).find(u => u.id === 'usr-x')!).not.toHaveProperty('clave');
  });

  it('cambios sube con cada escritura exitosa y no con las lecturas', async () => {
    expect(repo.cambios()).toBe(0);
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    await repo.guardarServicio({ ...consulta, valor: 45000 });
    expect(repo.cambios()).toBe(1);
    await repo.servicios();
    expect(repo.cambios()).toBe(1);
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

});

describe('DemoAgendaRepo con dos pestañas', () => {
  let a: DemoAgendaRepo;
  let b: DemoAgendaRepo;

  beforeEach(() => {
    localStorage.clear();
    a = crearRepo();
    b = crearRepoAparte();
  });

  it('una reserva de A no se pierde cuando B escribe después con datos viejos', async () => {
    const consulta = await servicioPorNombre(a, 'Consulta inicial');
    const [inicio] = await a.horariosLibres(consulta.id, FECHA);
    const reserva = await a.reservar({ servicioId: consulta.id, fecha: FECHA, inicio, cliente: CLIENTE });

    const otra = (await b.citas()).find(c => c.estado === 'Confirmada')!;
    await b.cancelarCita(otra.id);

    avisarCambioDeAlmacen(); // A se entera por el evento storage, como en el navegador
    for (const repo of [a, b, crearRepoAparte()]) {
      const citas = await repo.citas();
      expect(citas.find(c => c.id === reserva.id)?.estado, 'la reserva sigue en pie').toBe('Confirmada');
      expect(citas.find(c => c.id === otra.id)?.estado).toBe('Cancelada');
    }
    expect((await b.clientes()).some(c => c.correo === CLIENTE.correo)).toBe(true);
  });

  it('no deja reservar dos veces el mismo horario desde pestañas distintas', async () => {
    const consulta = await servicioPorNombre(a, 'Consulta inicial');
    const [inicio] = await a.horariosLibres(consulta.id, FECHA);
    await a.reservar({ servicioId: consulta.id, fecha: FECHA, inicio, cliente: CLIENTE });
    await expect(b.reservar({ servicioId: consulta.id, fecha: FECHA, inicio, cliente: CLIENTE })).rejects.toBeInstanceOf(
      HorarioNoDisponibleError,
    );
  });

  it('un evento storage recarga los datos de la otra pestaña y sube cambios', async () => {
    const consulta = await servicioPorNombre(a, 'Consulta inicial');
    const [inicio] = await a.horariosLibres(consulta.id, FECHA);
    const reserva = await a.reservar({ servicioId: consulta.id, fecha: FECHA, inicio, cliente: CLIENTE });
    expect((await b.citas()).some(c => c.id === reserva.id)).toBe(false); // todavía no se enteró

    const antes = b.cambios();
    avisarCambioDeAlmacen();
    expect(b.cambios()).toBe(antes + 1);
    expect((await b.citas()).some(c => c.id === reserva.id)).toBe(true);
  });

  it('un evento storage de otra clave se ignora', async () => {
    const antes = b.cambios();
    window.dispatchEvent(new StorageEvent('storage', { key: 'otra.clave' }));
    expect(b.cambios()).toBe(antes);
  });

  it('al destruir el repositorio deja de escuchar el almacén', () => {
    const entorno = createEnvironmentInjector(
      [{ provide: RELOJ, useValue: () => AHORA }, DemoAgendaRepo],
      TestBed.inject(EnvironmentInjector),
    );
    const c = entorno.get(DemoAgendaRepo);
    entorno.destroy();
    const antes = c.cambios();
    avisarCambioDeAlmacen();
    expect(c.cambios()).toBe(antes);
  });
});

describe('DemoAgendaRepo con el paso de los días', () => {
  const DIA_SIGUIENTE = new Date(2026, 10, 12, 9, 0); // jueves 2026-11-12

  beforeEach(() => {
    localStorage.clear();
  });

  it('guarda el día en que se sembraron los datos y que nadie los editó', () => {
    crearRepo();
    const guardado = JSON.parse(localStorage.getItem(CLAVE)!);
    expect(guardado.sembradoEn).toBe('2026-11-09');
    expect(guardado.editado).toBe(false);
  });

  it('cualquier escritura marca los datos como editados', async () => {
    const repo = crearRepo();
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    await repo.guardarServicio({ ...consulta, valor: 1 });
    const guardado = JSON.parse(localStorage.getItem(CLAVE)!);
    expect(guardado.editado).toBe(true);
    expect(guardado.sembradoEn).toBe('2026-11-09');
  });

  it('datos de ejemplo sin tocar de un día anterior se renuevan al abrir', async () => {
    crearRepo();
    const nuevo = crearRepoAparte(() => DIA_SIGUIENTE);
    const semilla = sembrar(DIA_SIGUIENTE);
    expect(await nuevo.citas()).toEqual(semilla.citas);
    const guardado = JSON.parse(localStorage.getItem(CLAVE)!);
    expect(guardado.sembradoEn).toBe('2026-11-12');
    expect(guardado.editado).toBe(false);
  });

  it('datos de ejemplo sin tocar del mismo día se conservan', async () => {
    const repo = crearRepo();
    const citas = await repo.citas();
    const mismoDia = crearRepoAparte(() => new Date(2026, 10, 9, 20, 0));
    expect(await mismoDia.citas()).toEqual(citas);
  });

  it('datos editados se conservan aunque pasen los días', async () => {
    const repo = crearRepo();
    const consulta = await servicioPorNombre(repo, 'Consulta inicial');
    await repo.guardarServicio({ ...consulta, valor: 45000 });
    const nuevo = crearRepoAparte(() => DIA_SIGUIENTE);
    expect((await servicioPorNombre(nuevo, 'Consulta inicial')).valor).toBe(45000);
    expect(await nuevo.citas()).toEqual(await repo.citas());
  });

  it('datos guardados por la versión anterior (sin marcas) cargan y se conservan', async () => {
    const antiguo = sembrar(new Date(2026, 9, 1, 9, 0));
    antiguo.servicios[0].valor = 12345;
    localStorage.setItem(CLAVE, JSON.stringify(antiguo));
    const repo = crearRepo();
    expect((await servicioPorNombre(repo, 'Consulta inicial')).valor).toBe(12345);
    expect(await repo.citas()).toEqual(antiguo.citas);
    await repo.guardarServicio({ ...antiguo.servicios[0], valor: 2 });
    expect(JSON.parse(localStorage.getItem(CLAVE)!).editado).toBe(true);
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
    expect(datos.usuarios.map(u => [u.usuario, u.rolId])).toEqual([
      ['marta', datos.roles[0].id],
      ['andres', datos.roles[1].id],
    ]);
    expect(JSON.stringify(datos.usuarios)).not.toContain('clave');
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

  describe('las citas de hoy siguen a la hora actual', () => {
    const horaAMin = (h: string) => Number(h.slice(0, 2)) * 60 + Number(h.slice(3));
    const citasDe = (ahora: Date) => sembrar(ahora).citas.filter(c => c.estado !== 'Cancelada');
    const hoyDe = (ahora: Date) =>
      `${ahora.getFullYear()}-${String(ahora.getMonth() + 1).padStart(2, '0')}-${String(ahora.getDate()).padStart(2, '0')}`;

    it('por la tarde hay una cita ya atendida y otras por venir hoy', () => {
      const tarde = new Date(2026, 10, 9, 14, 0);
      const hoy = citasDe(tarde).filter(c => c.fecha === hoyDe(tarde));
      const atendidas = hoy.filter(c => c.estado === 'Atendida');
      const porVenir = hoy.filter(c => c.estado === 'Confirmada');
      expect(atendidas.length).toBe(1);
      expect(horaAMin(atendidas[0].fin)).toBeLessThanOrEqual(14 * 60);
      expect(porVenir.length).toBeGreaterThanOrEqual(1);
      for (const c of porVenir) expect(horaAMin(c.inicio)).toBeGreaterThanOrEqual(14 * 60);
    });

    it('a primera hora las dos citas de hoy están por venir', () => {
      const hoy = citasDe(AHORA).filter(c => c.fecha === hoyDe(AHORA));
      expect(hoy.length).toBe(2);
      for (const c of hoy) {
        expect(c.estado).toBe('Confirmada');
        expect(horaAMin(c.inicio)).toBeGreaterThanOrEqual(8 * 60);
      }
    });

    it('de noche no queda ninguna cita de hoy por venir ni en el pasado: pasan al siguiente día hábil', () => {
      const noche = new Date(2026, 10, 9, 22, 0);
      const datos = sembrar(noche);
      expect(datos.citas.length).toBe(sembrar(AHORA).citas.length);
      expect(datos.citas.filter(c => c.fecha === hoyDe(noche) && c.estado === 'Confirmada')).toEqual([]);
      for (const c of datos.citas.filter(c => c.estado === 'Confirmada')) {
        expect(c.fecha > hoyDe(noche)).toBe(true);
      }
    });

    it('un domingo las citas de hoy pasan al lunes, sin atendidas en el futuro', () => {
      const domingo = new Date(2026, 10, 8, 15, 0);
      const datos = sembrar(domingo);
      for (const c of datos.citas) {
        if (c.fecha >= hoyDe(domingo)) expect(c.estado).not.toBe('Atendida');
      }
    });

    it('es determinista y coherente a cualquier hora', () => {
      for (const hora of [0, 8, 11, 14, 17, 23]) {
        const ahora = new Date(2026, 10, 9, hora, 15);
        const datos = sembrar(ahora);
        expect(sembrar(ahora)).toEqual(datos);
        for (const cita of datos.citas.filter(c => c.estado !== 'Cancelada')) {
          const servicio = datos.servicios.find(s => s.id === cita.servicioId)!;
          const libres = horariosLibres({
            servicio, fecha: cita.fecha, citas: datos.citas, ahora: medianoche(cita.fecha), excluirCitaId: cita.id,
          });
          expect(libres, `${hora}h ${cita.id}`).toContain(cita.inicio);
        }
      }
    });
  });
});
