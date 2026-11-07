import { Permiso, Rol, Usuario } from '../../core/models';
import { conDependencias } from '../../core/permisos';
import { esCorreoValido } from '../../core/validaciones';
import { normalizar } from '../clientes/clientes-logica';


export const MENSAJE_ULTIMO_GESTOR = 'Debe quedar al menos un usuario que pueda gestionar roles.';

/**
 * Mensaje de error en español si el usuario no se puede guardar; null si es válido.
 * Al editar (esNuevo = false) la clave puede ir vacía: significa conservar la actual.
 */
export function validarUsuario(u: Usuario, todos: Usuario[], roles: Rol[], esNuevo: boolean): string | null {
  if (!u.nombre.trim()) return 'Escribe el nombre.';
  if (!u.apellido.trim()) return 'Escribe el apellido.';
  const usuario = u.usuario.trim();
  if (!usuario) return 'Escribe el nombre de usuario.';
  const correo = u.correo.trim();
  if (!correo) return 'Escribe el correo.';
  if (!esCorreoValido(correo)) return 'Escribe un correo válido, por ejemplo nombre@correo.com.';
  if (esNuevo && !u.clave) return 'Escribe la clave.';
  if (!roles.some(r => r.id === u.rolId)) return 'Elige un rol.';
  const repetido = todos.some(o => o.id !== u.id && o.usuario.trim().toLowerCase() === usuario.toLowerCase());
  return repetido ? 'Ya existe otro usuario con ese nombre de usuario.' : null;
}

/** Mensaje de error si el rol no se puede guardar (nombre vacío o repetido); null si es válido. */
export function validarRolEditado(rol: Rol, todos: Rol[]): string | null {
  const nombre = normalizar(rol.nombre);
  if (!nombre) return 'Escribe el nombre del rol.';
  const repetido = todos.some(o => o.id !== rol.id && normalizar(o.nombre) === nombre);
  return repetido ? 'Ya existe otro rol con ese nombre.' : null;
}

/** «Ver citas» no se puede quitar mientras el rol cancele, mueva o atienda citas. */
export function verCitasObligatorio(permisos: Permiso[]): boolean {
  return permisos.includes('gestionar_citas') || permisos.includes('atender_citas');
}

/** Marca o desmarca un permiso aplicando las dependencias entre permisos. */
export function alternarPermiso(permisos: Permiso[], id: Permiso, marcado: boolean): Permiso[] {
  if (marcado) return conDependencias(permisos.includes(id) ? permisos : [...permisos, id]);
  const sin = permisos.filter(p => p !== id);
  return id === 'ver_citas' && verCitasObligatorio(sin) ? permisos : conDependencias(sin);
}

function gestionaRoles(u: Usuario, roles: Rol[]): boolean {
  return !!roles.find(r => r.id === u.rolId)?.permisos.includes('gestionar_roles');
}

function esUltimoGestorDeRoles(u: Usuario, usuarios: Usuario[], roles: Rol[]): boolean {
  return gestionaRoles(u, roles) && !usuarios.some(o => o.id !== u.id && gestionaRoles(o, roles));
}

/** Motivo por el que no se puede eliminar al usuario; null si se puede. */
export function motivoNoEliminarUsuario(p: {
  objetivo: Usuario; actorId: string; usuarios: Usuario[]; roles: Rol[];
}): string | null {
  if (p.objetivo.id === p.actorId) return 'No puedes eliminar tu propio usuario.';
  return esUltimoGestorDeRoles(p.objetivo, p.usuarios, p.roles) ? MENSAJE_ULTIMO_GESTOR : null;
}

/** Motivo por el que no se puede cambiar el rol del usuario (deja al sistema sin quien gestione roles). */
export function motivoNoCambiarRol(p: {
  antes: Usuario; despues: Usuario; usuarios: Usuario[]; roles: Rol[];
}): string | null {
  if (p.antes.rolId === p.despues.rolId) return null;
  if (!esUltimoGestorDeRoles(p.antes, p.usuarios, p.roles)) return null;
  return gestionaRoles(p.despues, p.roles) ? null : MENSAJE_ULTIMO_GESTOR;
}
