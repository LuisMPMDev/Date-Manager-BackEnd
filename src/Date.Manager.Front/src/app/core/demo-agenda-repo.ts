import { inject, Injectable, signal } from '@angular/core';
import { AgendaRepo, DatosCliente } from './agenda-repo';
import { CambioNoPermitidoError, HorarioNoDisponibleError, RELOJ, RolEnUsoError } from './errores';
import { horariosLibres, sumarMinutos } from './horarios';
import { Cita, Cliente, Datos, Rol, Servicio, Usuario } from './models';
import { conDependencias, puedeEliminarRol, validarCambioRol } from './permisos';
import { sembrar } from './seed';

const CLAVE_ALMACEN = 'datemanager.demo.v1';
const LISTAS: (keyof Datos)[] = ['servicios', 'clientes', 'citas', 'roles', 'usuarios'];

function nuevoId(prefijo: string): string {
  return `${prefijo}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;
}

function copia<T>(valor: T): T {
  return structuredClone(valor);
}

/** Inserta o reemplaza por id. */
function guardarPorId<T extends { id: string }>(lista: T[], item: T): T[] {
  return lista.some(x => x.id === item.id) ? lista.map(x => (x.id === item.id ? item : x)) : [...lista, item];
}

@Injectable()
export class DemoAgendaRepo extends AgendaRepo {
  private readonly reloj = inject(RELOJ);
  private readonly datos = signal<Datos>(this.cargar());
  private readonly contador = signal(0);
  readonly cambios = this.contador.asReadonly();

  // --- Almacén -------------------------------------------------------------

  private cargar(): Datos {
    try {
      const crudo = localStorage.getItem(CLAVE_ALMACEN);
      if (crudo) {
        const dato = JSON.parse(crudo);
        if (dato && LISTAS.every(l => Array.isArray(dato[l]))) return dato as Datos;
      }
    } catch {
      // JSON corrupto o localStorage no disponible: se usan los datos de ejemplo
    }
    return sembrar(this.reloj());
  }

  /** Aplica un cambio, lo persiste y avisa a las pantallas. */
  private escribir(cambio: (d: Datos) => Datos): void {
    const siguiente = cambio(this.datos());
    this.datos.set(siguiente);
    try {
      localStorage.setItem(CLAVE_ALMACEN, JSON.stringify(siguiente));
    } catch {
      // sin almacenamiento persistente: la demo sigue funcionando en memoria
    }
    this.contador.update(n => n + 1);
  }

  private servicioPorId(id: string): Servicio {
    const servicio = this.datos().servicios.find(s => s.id === id);
    if (!servicio) throw new HorarioNoDisponibleError();
    return servicio;
  }

  private citaPorId(id: string): Cita {
    const cita = this.datos().citas.find(c => c.id === id);
    if (!cita) throw new Error('No se encontró la cita.');
    return cita;
  }

  private libres(servicio: Servicio, fecha: string, excluirCitaId?: string): string[] {
    return horariosLibres({ servicio, fecha, citas: this.datos().citas, ahora: this.reloj(), excluirCitaId });
  }

  private cambiarCita(id: string, cambio: (c: Cita) => Cita): Cita {
    const nueva = cambio(this.citaPorId(id));
    this.escribir(d => ({ ...d, citas: d.citas.map(c => (c.id === id ? nueva : c)) }));
    return copia(nueva);
  }

  // --- Servicios y citas ---------------------------------------------------

  async servicios(): Promise<Servicio[]> {
    return copia(this.datos().servicios);
  }

  async guardarServicio(s: Servicio): Promise<Servicio> {
    const guardado = copia(s);
    this.escribir(d => ({ ...d, servicios: guardarPorId(d.servicios, guardado) }));
    return copia(guardado);
  }

  async horariosLibres(servicioId: string, fecha: string, excluirCitaId?: string): Promise<string[]> {
    const servicio = this.datos().servicios.find(s => s.id === servicioId);
    return servicio ? this.libres(servicio, fecha, excluirCitaId) : [];
  }

  async reservar(p: { servicioId: string; fecha: string; inicio: string; cliente: DatosCliente }): Promise<Cita> {
    const servicio = this.servicioPorId(p.servicioId);
    if (!this.libres(servicio, p.fecha).includes(p.inicio)) throw new HorarioNoDisponibleError();

    const correo = p.cliente.correo.trim().toLowerCase();
    const existente = this.datos().clientes.find(c => c.correo.trim().toLowerCase() === correo);
    const cliente: Cliente = existente
      ? { ...existente, nombre: p.cliente.nombre, apellido: p.cliente.apellido, telefono: p.cliente.telefono }
      : { id: nuevoId('cli'), ...p.cliente };
    const cita: Cita = {
      id: nuevoId('cita'),
      servicioId: servicio.id,
      clienteId: cliente.id,
      fecha: p.fecha,
      inicio: p.inicio,
      fin: sumarMinutos(p.inicio, servicio.duracionMin),
      estado: 'Confirmada',
      creadaEn: this.reloj().toISOString(),
    };
    this.escribir(d => ({ ...d, clientes: guardarPorId(d.clientes, cliente), citas: [...d.citas, cita] }));
    return copia(cita);
  }

  async citas(): Promise<Cita[]> {
    return copia(this.datos().citas);
  }

  async cancelarCita(id: string): Promise<Cita> {
    return this.cambiarCita(id, c => ({ ...c, estado: 'Cancelada', canceladaEn: this.reloj().toISOString() }));
  }

  async moverCita(id: string, fecha: string, inicio: string): Promise<Cita> {
    const cita = this.citaPorId(id);
    const servicio = this.servicioPorId(cita.servicioId);
    if (!this.libres(servicio, fecha, id).includes(inicio)) throw new HorarioNoDisponibleError();
    return this.cambiarCita(id, c => ({ ...c, fecha, inicio, fin: sumarMinutos(inicio, servicio.duracionMin) }));
  }

  async marcarAtendida(id: string): Promise<Cita> {
    return this.cambiarCita(id, c => ({ ...c, estado: 'Atendida' }));
  }

  // --- Clientes ------------------------------------------------------------

  async clientes(): Promise<Cliente[]> {
    return copia(this.datos().clientes);
  }

  async guardarCliente(c: Cliente): Promise<Cliente> {
    const guardado = copia(c);
    this.escribir(d => ({ ...d, clientes: guardarPorId(d.clientes, guardado) }));
    return copia(guardado);
  }

  // --- Roles y usuarios ----------------------------------------------------

  async roles(): Promise<Rol[]> {
    return copia(this.datos().roles);
  }

  async guardarRol(rol: Rol, actorId: string): Promise<Rol> {
    const { roles, usuarios } = this.datos();
    const rolAntes = roles.find(r => r.id === rol.id);
    const guardado: Rol = { ...copia(rol), permisos: conDependencias(rol.permisos) };
    if (rolAntes) {
      const actor = usuarios.find(u => u.id === actorId);
      const mensaje = actor ? validarCambioRol({ actor, rolAntes, rolDespues: guardado }) : null;
      if (mensaje) throw new CambioNoPermitidoError(mensaje);
    }
    this.escribir(d => ({ ...d, roles: guardarPorId(d.roles, guardado) }));
    return copia(guardado);
  }

  async eliminarRol(id: string): Promise<void> {
    if (!puedeEliminarRol(id, this.datos().usuarios)) throw new RolEnUsoError();
    this.escribir(d => ({ ...d, roles: d.roles.filter(r => r.id !== id) }));
  }

  async usuarios(): Promise<Usuario[]> {
    return copia(this.datos().usuarios);
  }

  async guardarUsuario(u: Usuario): Promise<Usuario> {
    const guardado = copia(u);
    this.escribir(d => ({ ...d, usuarios: guardarPorId(d.usuarios, guardado) }));
    return copia(guardado);
  }

  async eliminarUsuario(id: string): Promise<void> {
    this.escribir(d => ({ ...d, usuarios: d.usuarios.filter(u => u.id !== id) }));
  }

  async iniciarSesion(usuario: string, clave: string): Promise<Usuario | null> {
    const encontrado = this.datos().usuarios.find(u => u.usuario === usuario && u.clave === clave);
    return encontrado ? copia(encontrado) : null;
  }

  async reiniciar(): Promise<void> {
    this.escribir(() => sembrar(this.reloj()));
  }
}
