import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { AgendaRepo, DatosCliente } from '../core/agenda-repo';
import { RELOJ } from '../core/errores';
import { aFechaISO } from '../core/fechas';
import { nuevoId } from '../core/ids';
import { horariosLibres, sumarMinutos } from '../core/horarios';
import { Cita, Cliente, Datos, Rol, Servicio, Usuario, UsuarioForm } from '../core/models';
import { conDependencias, puedeEliminarRol } from '../core/permisos';
import { HorarioNoDisponibleError, RolEnUsoError } from './errores-demo';
import { sembrar } from './seed';

const CLAVE_ALMACEN = 'datemanager.demo.v1';
const LISTAS: (keyof Datos)[] = ['servicios', 'clientes', 'citas', 'roles', 'usuarios'];

/**
 * Lo que se guarda en el navegador: los datos más dos marcas.
 * `sembradoEn` es el día (local) en que se crearon los datos de ejemplo y `editado` indica si alguien
 * hizo algún cambio desde entonces. Los datos guardados por la versión anterior no traen marcas.
 */
interface Almacenado extends Datos {
  sembradoEn?: string;
  editado?: boolean;
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
  // Las marcas se declaran antes que `datos`: `arrancar` las rellena al construir.
  private sembradoEn = '';
  private editado = false;
  private readonly datos = signal<Datos>(this.arrancar());
  private readonly contador = signal(0);
  readonly cambios = this.contador.asReadonly();

  constructor() {
    super();
    // Otra pestaña escribió en el almacén: se recargan los datos y se avisa a las pantallas.
    if (typeof window !== 'undefined') {
      const alCambiarAlmacen = (e: StorageEvent): void => {
        if (e.key !== null && e.key !== CLAVE_ALMACEN) return;
        if (this.sincronizar()) this.contador.update(n => n + 1);
      };
      window.addEventListener('storage', alCambiarAlmacen);
      inject(DestroyRef).onDestroy(() => window.removeEventListener('storage', alCambiarAlmacen));
    }
  }

  // --- Almacén -------------------------------------------------------------

  /** Lee y valida lo guardado; null si no hay nada, está corrupto o no hay almacenamiento. */
  private leer(): Almacenado | null {
    try {
      const crudo = localStorage.getItem(CLAVE_ALMACEN);
      if (crudo) {
        const dato = JSON.parse(crudo);
        if (dato && LISTAS.every(l => Array.isArray(dato[l]))) return dato as Almacenado;
      }
    } catch {
      // JSON corrupto o localStorage no disponible
    }
    return null;
  }

  private guardar(datos: Datos): void {
    try {
      const almacenado: Almacenado = { ...datos, sembradoEn: this.sembradoEn, editado: this.editado };
      localStorage.setItem(CLAVE_ALMACEN, JSON.stringify(almacenado));
    } catch {
      // sin almacenamiento persistente: la demo sigue funcionando en memoria
    }
  }

  /** Toma las marcas de lo guardado; sin marcas (versión anterior) se asume editado para no perder nada. */
  private tomarMarcas(guardado: Almacenado): void {
    const valida = typeof guardado.sembradoEn === 'string';
    this.sembradoEn = valida ? guardado.sembradoEn! : '';
    this.editado = valida ? guardado.editado !== false : true;
  }

  private sembrarDeNuevo(): Datos {
    const datos = sembrar(this.reloj());
    this.sembradoEn = aFechaISO(this.reloj());
    this.editado = false;
    this.guardar(datos);
    return datos;
  }

  private arrancar(): Datos {
    const guardado = this.leer();
    if (guardado) {
      this.tomarMarcas(guardado);
      // Datos de ejemplo que nadie tocó y que ya son de un día anterior: se renuevan para que la demo no quede vieja.
      if (!this.editado && this.sembradoEn < aFechaISO(this.reloj())) return this.sembrarDeNuevo();
      return guardado;
    }
    return this.sembrarDeNuevo();
  }

  /** Vuelve a leer el almacén (otra pestaña pudo escribir). false si no hay nada válido que leer. */
  private sincronizar(): boolean {
    const guardado = this.leer();
    if (!guardado) return false;
    this.tomarMarcas(guardado);
    this.datos.set(guardado);
    return true;
  }

  /** Aplica un cambio sobre lo más reciente del almacén, lo persiste y avisa a las pantallas. */
  private escribir(cambio: (d: Datos) => Datos): void {
    this.sincronizar();
    const siguiente = cambio(this.datos());
    this.editado = true;
    this.datos.set(siguiente);
    this.guardar(siguiente);
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
    this.sincronizar();
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
    this.sincronizar();
    const servicio = this.servicioPorId(p.servicioId);
    if (!this.libres(servicio, p.fecha).includes(p.inicio)) throw new HorarioNoDisponibleError();

    const correo = p.cliente.correo.trim().toLowerCase();
    const existente = this.datos().clientes.find(c => c.correo.trim().toLowerCase() === correo);
    const cliente: Cliente = existente
      ? { ...existente, nombre: p.cliente.nombre, apellido: p.cliente.apellido, telefono: p.cliente.telefono }
      : { id: nuevoId(), ...p.cliente };
    const cita: Cita = {
      id: nuevoId(),
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
    this.sincronizar();
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

  async guardarRol(rol: Rol): Promise<Rol> {
    const guardado: Rol = { ...copia(rol), permisos: conDependencias(rol.permisos) };
    this.escribir(d => ({ ...d, roles: guardarPorId(d.roles, guardado) }));
    return copia(guardado);
  }

  async eliminarRol(id: string): Promise<void> {
    this.sincronizar();
    if (!puedeEliminarRol(id, this.datos().usuarios)) throw new RolEnUsoError();
    this.escribir(d => ({ ...d, roles: d.roles.filter(r => r.id !== id) }));
  }

  async usuarios(): Promise<Usuario[]> {
    return copia(this.datos().usuarios);
  }

  async guardarUsuario(u: UsuarioForm): Promise<Usuario> {
    const guardado: Usuario = copia(u);
    delete (guardado as UsuarioForm).clave; // la clave nunca se conserva: el servidor la guarda cifrada
    this.escribir(d => ({ ...d, usuarios: guardarPorId(d.usuarios, guardado) }));
    return copia(guardado);
  }

  async eliminarUsuario(id: string): Promise<void> {
    this.escribir(d => ({ ...d, usuarios: d.usuarios.filter(u => u.id !== id) }));
  }
}
