import { Rol, Usuario } from '../../core/models';
import {
  alternarPermiso, motivoNoCambiarRol, motivoNoEliminarUsuario, verCitasObligatorio, validarRolEditado, validarUsuario,
} from './usuarios-logica';

const ADMIN: Rol = {
  id: 'rol-admin', nombre: 'Administrador', descripcion: '',
  permisos: ['ver_panel', 'ver_citas', 'gestionar_usuarios', 'gestionar_roles'],
};
const RECEPCION: Rol = { id: 'rol-recepcion', nombre: 'Recepción', descripcion: '', permisos: ['ver_citas'] };
const ROLES = [ADMIN, RECEPCION];

const marta: Usuario = {
  id: 'usr-marta', nombre: 'Marta', apellido: 'Rivas', correo: 'marta@datemanager.demo',
  telefono: '300 555 0201', usuario: 'marta', clave: 'demo123', rolId: 'rol-admin',
};
const andres: Usuario = { ...marta, id: 'usr-andres', nombre: 'Andrés', usuario: 'andres', correo: 'andres@datemanager.demo', rolId: 'rol-recepcion' };
const USUARIOS = [marta, andres];

describe('validarUsuario', () => {
  const nuevo: Usuario = { ...marta, id: 'usr-nuevo', usuario: 'lucia', correo: 'lucia@datemanager.demo', rolId: 'rol-recepcion' };

  it('acepta un usuario completo', () => {
    expect(validarUsuario(nuevo, USUARIOS, ROLES, true)).toBeNull();
  });

  it('pide cada dato obligatorio, en orden', () => {
    expect(validarUsuario({ ...nuevo, nombre: ' ' }, USUARIOS, ROLES, true)).toBe('Escribe el nombre.');
    expect(validarUsuario({ ...nuevo, apellido: '' }, USUARIOS, ROLES, true)).toBe('Escribe el apellido.');
    expect(validarUsuario({ ...nuevo, usuario: '' }, USUARIOS, ROLES, true)).toBe('Escribe el nombre de usuario.');
    expect(validarUsuario({ ...nuevo, correo: '' }, USUARIOS, ROLES, true)).toBe('Escribe el correo.');
    expect(validarUsuario({ ...nuevo, clave: '' }, USUARIOS, ROLES, true)).toBe('Escribe la clave.');
    expect(validarUsuario({ ...nuevo, rolId: '' }, USUARIOS, ROLES, true)).toBe('Elige un rol.');
  });

  it('el teléfono es opcional', () => {
    expect(validarUsuario({ ...nuevo, telefono: '' }, USUARIOS, ROLES, true)).toBeNull();
  });

  it('al editar, dejar la clave vacía significa conservar la actual', () => {
    expect(validarUsuario({ ...andres, clave: '' }, USUARIOS, ROLES, false)).toBeNull();
  });

  it('el correo necesita @ y un punto después', () => {
    expect(validarUsuario({ ...nuevo, correo: 'lucia' }, USUARIOS, ROLES, true)).toBe('Escribe un correo válido, por ejemplo nombre@correo.com.');
    expect(validarUsuario({ ...nuevo, correo: 'lucia@correo' }, USUARIOS, ROLES, true)).toBe('Escribe un correo válido, por ejemplo nombre@correo.com.');
  });

  it('el usuario no se repite entre los demás, sin importar mayúsculas', () => {
    expect(validarUsuario({ ...nuevo, usuario: ' MARTA ' }, USUARIOS, ROLES, true)).toBe('Ya existe otro usuario con ese nombre de usuario.');
  });

  it('al editar, conservar el propio usuario no cuenta como repetido', () => {
    expect(validarUsuario({ ...marta, usuario: 'Marta' }, USUARIOS, ROLES, false)).toBeNull();
  });

  it('el rol debe existir', () => {
    expect(validarUsuario({ ...nuevo, rolId: 'rol-fantasma' }, USUARIOS, ROLES, true)).toBe('Elige un rol.');
  });
});

describe('validarRolEditado', () => {
  it('pide el nombre', () => {
    expect(validarRolEditado({ ...RECEPCION, nombre: '  ' }, ROLES)).toBe('Escribe el nombre del rol.');
  });

  it('el nombre no se repite entre los demás roles, sin importar mayúsculas ni tildes', () => {
    expect(validarRolEditado({ id: 'rol-x', nombre: 'recepcion', descripcion: '', permisos: [] }, ROLES))
      .toBe('Ya existe otro rol con ese nombre.');
  });

  it('conservar el nombre del propio rol es válido', () => {
    expect(validarRolEditado({ ...RECEPCION, nombre: 'RECEPCIÓN' }, ROLES)).toBeNull();
  });
});

describe('permisos del editor de roles', () => {
  it('marcar «Cancelar y mover citas» o «Marcar como atendidas» marca también «Ver citas»', () => {
    expect(alternarPermiso([], 'gestionar_citas', true)).toEqual(['gestionar_citas', 'ver_citas']);
    expect(alternarPermiso(['ver_panel'], 'atender_citas', true)).toEqual(['ver_panel', 'atender_citas', 'ver_citas']);
  });

  it('desmarcar un permiso lo quita', () => {
    expect(alternarPermiso(['ver_panel', 'ver_citas'], 'ver_panel', false)).toEqual(['ver_citas']);
  });

  it('«Ver citas» no se puede quitar mientras haya gestionar o atender citas', () => {
    expect(alternarPermiso(['ver_citas', 'gestionar_citas'], 'ver_citas', false)).toEqual(['ver_citas', 'gestionar_citas']);
    expect(verCitasObligatorio(['ver_citas', 'atender_citas'])).toBe(true);
    expect(verCitasObligatorio(['ver_citas'])).toBe(false);
  });

  it('quitar gestionar_citas deja «Ver citas» marcado', () => {
    expect(alternarPermiso(['ver_citas', 'gestionar_citas'], 'gestionar_citas', false)).toEqual(['ver_citas']);
  });
});

describe('protecciones de usuarios', () => {
  it('nadie puede eliminarse a sí mismo', () => {
    expect(motivoNoEliminarUsuario({ objetivo: andres, actorId: andres.id, usuarios: USUARIOS, roles: ROLES }))
      .toBe('No puedes eliminar tu propio usuario.');
  });

  it('no se puede eliminar al último usuario que gestiona roles', () => {
    expect(motivoNoEliminarUsuario({ objetivo: marta, actorId: andres.id, usuarios: USUARIOS, roles: ROLES }))
      .toBe('Debe quedar al menos un usuario que pueda gestionar roles.');
  });

  it('se puede eliminar a un gestor de roles si queda otro', () => {
    const otro: Usuario = { ...marta, id: 'usr-otra', usuario: 'otra' };
    expect(motivoNoEliminarUsuario({ objetivo: marta, actorId: otro.id, usuarios: [...USUARIOS, otro], roles: ROLES })).toBeNull();
  });

  it('se puede eliminar a un usuario cualquiera', () => {
    expect(motivoNoEliminarUsuario({ objetivo: andres, actorId: marta.id, usuarios: USUARIOS, roles: ROLES })).toBeNull();
  });

  it('no se puede cambiar el rol del último gestor de roles a uno sin gestionar_roles', () => {
    expect(motivoNoCambiarRol({ antes: marta, despues: { ...marta, rolId: 'rol-recepcion' }, usuarios: USUARIOS, roles: ROLES }))
      .toBe('Debe quedar al menos un usuario que pueda gestionar roles.');
  });

  it('el cambio de rol es válido si queda otro gestor o el nuevo rol también gestiona roles', () => {
    const otro: Usuario = { ...marta, id: 'usr-otra', usuario: 'otra' };
    expect(motivoNoCambiarRol({ antes: marta, despues: { ...marta, rolId: 'rol-recepcion' }, usuarios: [...USUARIOS, otro], roles: ROLES })).toBeNull();
    expect(motivoNoCambiarRol({ antes: marta, despues: { ...marta, nombre: 'Marta M.' }, usuarios: USUARIOS, roles: ROLES })).toBeNull();
  });

  it('cambiar el rol de alguien que no gestionaba roles siempre es válido', () => {
    expect(motivoNoCambiarRol({ antes: andres, despues: { ...andres, rolId: 'rol-admin' }, usuarios: USUARIOS, roles: ROLES })).toBeNull();
  });
});
