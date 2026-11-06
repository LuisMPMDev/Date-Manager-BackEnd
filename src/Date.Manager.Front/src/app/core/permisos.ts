import { Permiso, Rol, Usuario } from './models';

export const CATALOGO_PERMISOS: { id: Permiso; etiqueta: string }[] = [
  { id: 'ver_panel', etiqueta: 'Ver el panel' },
  { id: 'ver_citas', etiqueta: 'Ver citas' },
  { id: 'gestionar_citas', etiqueta: 'Cancelar y mover citas' },
  { id: 'atender_citas', etiqueta: 'Marcar citas como atendidas' },
  { id: 'gestionar_servicios', etiqueta: 'Gestionar servicios y horarios' },
  { id: 'gestionar_clientes', etiqueta: 'Gestionar clientes' },
  { id: 'gestionar_usuarios', etiqueta: 'Gestionar usuarios' },
  { id: 'gestionar_roles', etiqueta: 'Gestionar roles y permisos' },
];

/** Una entrada es visible si el rol tiene ALGUNO de sus permisos. */
export const MENU: { ruta: string; etiqueta: string; permisos: Permiso[] }[] = [
  { ruta: '/panel', etiqueta: 'Panel', permisos: ['ver_panel'] },
  { ruta: '/agenda', etiqueta: 'Agenda', permisos: ['ver_citas'] },
  { ruta: '/citas', etiqueta: 'Citas', permisos: ['ver_citas'] },
  { ruta: '/servicios', etiqueta: 'Servicios', permisos: ['gestionar_servicios'] },
  { ruta: '/clientes', etiqueta: 'Clientes', permisos: ['gestionar_clientes'] },
  { ruta: '/usuarios', etiqueta: 'Usuarios y roles', permisos: ['gestionar_usuarios', 'gestionar_roles'] },
];

/** gestionar_citas y atender_citas requieren ver_citas. */
export function conDependencias(permisos: Permiso[]): Permiso[] {
  const resultado = [...permisos];
  const requiereVerCitas = permisos.includes('gestionar_citas') || permisos.includes('atender_citas');
  if (requiereVerCitas && !resultado.includes('ver_citas')) resultado.push('ver_citas');
  return resultado;
}

export function puede(rol: Rol | undefined, permiso: Permiso): boolean {
  return !!rol && rol.permisos.includes(permiso);
}

export function primeraRuta(rol: Rol | undefined): string {
  const entrada = MENU.find(m => m.permisos.some(p => puede(rol, p)));
  return entrada ? entrada.ruta : '/sin-acceso';
}

/** Mensaje de error si el cambio deja al actor sin gestionar_roles en su propio rol; null si es válido. */
export function validarCambioRol(p: { actor: Usuario; rolAntes: Rol; rolDespues: Rol }): string | null {
  const { actor, rolAntes, rolDespues } = p;
  if (
    actor.rolId === rolAntes.id &&
    rolAntes.permisos.includes('gestionar_roles') &&
    !rolDespues.permisos.includes('gestionar_roles')
  ) {
    return 'No puedes quitarte el permiso de gestionar roles de tu propio rol.';
  }
  return null;
}

export function puedeEliminarRol(rolId: string, usuarios: Usuario[]): boolean {
  return !usuarios.some(u => u.rolId === rolId);
}
