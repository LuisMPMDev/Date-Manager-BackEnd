import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { AgendaRepo, DatosCliente } from './agenda-repo';
import { API_URL } from './config';
import { Cita, Cliente, Permiso, Rol, Servicio, Usuario, UsuarioForm } from './models';

/** El rol tal como lo manda la API: los permisos son textos snake_case. */
type RolApi = Omit<Rol, 'permisos'> & { permisos: string[] };

function aRol(r: RolApi): Rol {
  return { ...r, permisos: r.permisos as Permiso[] };
}

/**
 * AgendaRepo contra la API (`/api/panel/**`). Los errores llegan ya como `ApiError` desde el interceptor;
 * `cambios` sube solo después de una escritura exitosa, para que las pantallas recarguen sus datos.
 */
@Injectable()
export class HttpAgendaRepo extends AgendaRepo {
  private readonly http = inject(HttpClient);
  private readonly panel = `${inject(API_URL)}/panel`;
  private readonly contador = signal(0);
  readonly cambios = this.contador.asReadonly();

  // --- Servicios y citas ---------------------------------------------------

  servicios(): Promise<Servicio[]> {
    return firstValueFrom(this.http.get<Servicio[]>(`${this.panel}/servicios`));
  }

  guardarServicio(s: Servicio): Promise<Servicio> {
    const { nombre, duracionMin, activo, valor } = s;
    const ventanas = s.ventanas.map(v => ({ id: v.id, dia: v.dia, inicio: v.inicio, fin: v.fin }));
    return this.escribir(this.http.put<Servicio>(`${this.panel}/servicios/${s.id}`, { nombre, duracionMin, activo, valor, ventanas }));
  }

  horariosLibres(servicioId: string, fecha: string, excluirCitaId?: string): Promise<string[]> {
    let params = new HttpParams().set('fecha', fecha);
    if (excluirCitaId) params = params.set('excluirCitaId', excluirCitaId);
    return firstValueFrom(this.http.get<string[]>(`${this.panel}/servicios/${servicioId}/horarios`, { params }));
  }

  reservar(p: { servicioId: string; fecha: string; inicio: string; cliente: DatosCliente }): Promise<Cita> {
    const { servicioId, fecha, inicio } = p;
    const { nombre, apellido, telefono, correo } = p.cliente;
    return this.escribir(
      this.http.post<Cita>(`${this.panel}/citas`, { servicioId, fecha, inicio, cliente: { nombre, apellido, telefono, correo } }),
    );
  }

  citas(): Promise<Cita[]> {
    return firstValueFrom(this.http.get<Cita[]>(`${this.panel}/citas`));
  }

  cancelarCita(id: string): Promise<Cita> {
    return this.escribir(this.http.post<Cita>(`${this.panel}/citas/${id}/cancelar`, null));
  }

  moverCita(id: string, fecha: string, inicio: string): Promise<Cita> {
    return this.escribir(this.http.post<Cita>(`${this.panel}/citas/${id}/mover`, { fecha, inicio }));
  }

  marcarAtendida(id: string): Promise<Cita> {
    return this.escribir(this.http.post<Cita>(`${this.panel}/citas/${id}/atender`, null));
  }

  // --- Clientes ------------------------------------------------------------

  clientes(): Promise<Cliente[]> {
    return firstValueFrom(this.http.get<Cliente[]>(`${this.panel}/clientes`));
  }

  guardarCliente(c: Cliente): Promise<Cliente> {
    const { nombre, apellido, telefono, correo } = c;
    return this.escribir(this.http.put<Cliente>(`${this.panel}/clientes/${c.id}`, { nombre, apellido, telefono, correo }));
  }

  // --- Roles y usuarios ----------------------------------------------------

  async roles(): Promise<Rol[]> {
    return (await firstValueFrom(this.http.get<RolApi[]>(`${this.panel}/roles`))).map(aRol);
  }

  async guardarRol(rol: Rol): Promise<Rol> {
    const { nombre, descripcion, permisos } = rol;
    return aRol(await this.escribir(this.http.put<RolApi>(`${this.panel}/roles/${rol.id}`, { nombre, descripcion, permisos })));
  }

  async eliminarRol(id: string): Promise<void> {
    await this.escribir(this.http.delete<void>(`${this.panel}/roles/${id}`));
  }

  usuarios(): Promise<Usuario[]> {
    return firstValueFrom(this.http.get<Usuario[]>(`${this.panel}/usuarios`));
  }

  guardarUsuario(u: UsuarioForm): Promise<Usuario> {
    const { nombre, apellido, correo, telefono, usuario, rolId, clave } = u;
    // vacía o ausente = conservar la actual: la propiedad ni se manda
    const cuerpo = { nombre, apellido, correo, telefono, usuario, rolId, ...(clave ? { clave } : {}) };
    return this.escribir(this.http.put<Usuario>(`${this.panel}/usuarios/${u.id}`, cuerpo));
  }

  async eliminarUsuario(id: string): Promise<void> {
    await this.escribir(this.http.delete<void>(`${this.panel}/usuarios/${id}`));
  }

  /** Hace la escritura y, solo si salió bien, avisa a las pantallas. */
  private async escribir<T>(peticion: Observable<T>): Promise<T> {
    const respuesta = await firstValueFrom(peticion);
    this.contador.update(n => n + 1);
    return respuesta;
  }
}
