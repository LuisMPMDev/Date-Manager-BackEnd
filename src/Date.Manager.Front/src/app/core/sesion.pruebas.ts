import { inject, Injectable, Injector } from '@angular/core';
import { AgendaRepo } from './agenda-repo';
import { ApiError } from './api-error';
import { AuthApi } from './auth-api';
import { Permiso, Rol, Usuario } from './models';
import { SesionService } from './sesion';

/** Ayudas compartidas por las pruebas de sesión, guardas y pantallas (sin pruebas propias). */
export const TODOS_LOS_PERMISOS: Permiso[] = [
  'ver_panel', 'ver_citas', 'gestionar_citas', 'atender_citas', 'gestionar_servicios',
  'gestionar_clientes', 'gestionar_usuarios', 'gestionar_roles', 'gestionar_enlaces',
];

export const ROL_ADMIN: Rol = { id: 'r-admin', nombre: 'Administrador', descripcion: 'Todo', permisos: TODOS_LOS_PERMISOS };
export const ROL_RECEPCION: Rol = {
  id: 'r-recepcion', nombre: 'Recepción', descripcion: 'Atiende',
  permisos: ['ver_citas', 'gestionar_citas', 'atender_citas', 'gestionar_clientes'],
};

export const MARTA: Usuario = {
  id: 'u-marta', nombre: 'Marta', apellido: 'Ríos', correo: 'marta@datemanager.demo',
  telefono: '300 555 0201', usuario: 'marta', rolId: ROL_ADMIN.id,
};
export const ANDRES: Usuario = {
  id: 'u-andres', nombre: 'Andrés', apellido: 'Lora', correo: 'andres@datemanager.demo',
  telefono: '300 555 0202', usuario: 'andres', rolId: ROL_RECEPCION.id,
};

/** El rol de un usuario dentro de una lista de roles (las pantallas reciben un solo rol al iniciar sesión). */
export function rolDe(usuario: Usuario, roles: Rol[]): Rol {
  return roles.find(r => r.id === usuario.rolId)!;
}

/**
 * Doble de AuthApi para las pruebas de pantallas que aún usan el repositorio en memoria:
 * `yo()` devuelve el usuario con sesión y su rol tal como están ahora en el repositorio.
 */
@Injectable()
export class AuthApiDemo implements Pick<AuthApi, 'login' | 'yo'> {
  private readonly repo = inject(AgendaRepo);
  private readonly inyector = inject(Injector);

  async login(): Promise<{ usuario: Usuario; rol: Rol }> {
    throw new Error('AuthApiDemo no inicia sesión: las pruebas usan SesionService.iniciar.');
  }

  async yo(): Promise<{ usuario: Usuario; rol: Rol }> {
    const actual = this.inyector.get(SesionService).usuario();
    const usuario = (await this.repo.usuarios()).find(u => u.id === actual?.id);
    const rol = usuario && (await this.repo.roles()).find(r => r.id === usuario.rolId);
    if (!usuario || !rol) throw new ApiError(401, 'NO_AUTENTICADO', 'Inicia sesión para continuar.');
    return { usuario, rol };
  }
}
