import { Signal } from '@angular/core';
import { Cita, Cliente, Rol, Servicio, Usuario, UsuarioForm } from './models';

export interface DatosCliente { nombre: string; apellido: string; telefono: string; correo: string }

export abstract class AgendaRepo {
  abstract readonly cambios: Signal<number>; // sube con cada escritura, para recalcular pantallas
  abstract servicios(): Promise<Servicio[]>;
  abstract guardarServicio(s: Servicio): Promise<Servicio>; // crea si el id no existe
  abstract horariosLibres(servicioId: string, fecha: string, excluirCitaId?: string): Promise<string[]>;
  abstract reservar(p: { servicioId: string; fecha: string; inicio: string; cliente: DatosCliente }): Promise<Cita>;
  abstract citas(): Promise<Cita[]>;
  abstract cancelarCita(id: string): Promise<Cita>;
  abstract moverCita(id: string, fecha: string, inicio: string): Promise<Cita>;
  abstract marcarAtendida(id: string): Promise<Cita>;
  abstract clientes(): Promise<Cliente[]>;
  abstract guardarCliente(c: Cliente): Promise<Cliente>;
  abstract roles(): Promise<Rol[]>;
  abstract guardarRol(rol: Rol): Promise<Rol>;
  abstract eliminarRol(id: string): Promise<void>;
  abstract usuarios(): Promise<Usuario[]>;
  /** Envía `clave` solo si trae texto; vacía o ausente = conservar la actual. */
  abstract guardarUsuario(u: UsuarioForm): Promise<Usuario>;
  abstract eliminarUsuario(id: string): Promise<void>;
}
