import { Rol, Usuario } from './models';
import { CATALOGO_PERMISOS, conDependencias, MENU, esRutaPublica, primeraRuta, puedeEliminarRol, puede, validarCambioRol } from './permisos';

function rol(parcial: Partial<Rol> = {}): Rol {
  return { id: 'r1', nombre: 'Rol', descripcion: '', permisos: [], ...parcial };
}

function usuario(parcial: Partial<Usuario> = {}): Usuario {
  return {
    id: 'u1', nombre: 'Ana', apellido: 'Pérez', correo: 'ana@x.com', telefono: '1',
    usuario: 'ana', rolId: 'r1', ...parcial,
  };
}

describe('permisos', () => {
  it('el catálogo tiene los 9 permisos con etiqueta en español', () => {
    expect(CATALOGO_PERMISOS.length).toBe(9);
    expect(CATALOGO_PERMISOS.find(p => p.id === 'gestionar_citas')!.etiqueta).toBe('Cancelar y mover citas');
    expect(CATALOGO_PERMISOS.find(p => p.id === 'gestionar_enlaces')!.etiqueta).toBe('Gestionar enlaces de invitación');
  });

  it('el menú tiene las rutas en orden', () => {
    expect(MENU.map(m => m.ruta)).toEqual(['/panel', '/agenda', '/citas', '/servicios', '/clientes', '/usuarios', '/enlaces']);
    expect(MENU[5].etiqueta).toBe('Usuarios y roles');
    expect(MENU[6]).toEqual({ ruta: '/enlaces', etiqueta: 'Enlaces', permisos: ['gestionar_enlaces'] });
  });

  it('gestionar_citas añade ver_citas', () => {
    expect(conDependencias(['gestionar_citas'])).toEqual(['gestionar_citas', 'ver_citas']);
  });

  it('atender_citas añade ver_citas', () => {
    expect(conDependencias(['atender_citas'])).toEqual(['atender_citas', 'ver_citas']);
  });

  it('ver_panel queda igual', () => {
    expect(conDependencias(['ver_panel'])).toEqual(['ver_panel']);
  });

  it('no duplica ver_citas si ya está', () => {
    expect(conDependencias(['ver_citas', 'gestionar_citas'])).toEqual(['ver_citas', 'gestionar_citas']);
  });

  it('puede devuelve true solo si el rol tiene el permiso', () => {
    expect(puede(rol({ permisos: ['ver_panel'] }), 'ver_panel')).toBe(true);
    expect(puede(rol({ permisos: ['ver_panel'] }), 'ver_citas')).toBe(false);
    expect(puede(undefined, 'ver_panel')).toBe(false);
  });

  it('primeraRuta de un rol con solo ver_citas es /agenda', () => {
    expect(primeraRuta(rol({ permisos: ['ver_citas'] }))).toBe('/agenda');
  });

  it('primeraRuta con ver_panel es /panel', () => {
    expect(primeraRuta(rol({ permisos: ['ver_citas', 'ver_panel'] }))).toBe('/panel');
  });

  it('primeraRuta de un rol con solo gestionar_enlaces es /enlaces', () => {
    expect(primeraRuta(rol({ permisos: ['gestionar_enlaces'] }))).toBe('/enlaces');
  });

  it('primeraRuta con ninguno es /sin-acceso', () => {
    expect(primeraRuta(rol({ permisos: [] }))).toBe('/sin-acceso');
    expect(primeraRuta(undefined)).toBe('/sin-acceso');
  });

  it('validarCambioRol rechaza que el actor se quite gestionar_roles de su propio rol', () => {
    const antes = rol({ id: 'r1', permisos: ['gestionar_roles', 'ver_panel'] });
    const despues = rol({ id: 'r1', permisos: ['ver_panel'] });
    const msg = validarCambioRol({ actor: usuario({ rolId: 'r1' }), rolAntes: antes, rolDespues: despues });
    expect(msg).not.toBeNull();
  });

  it('validarCambioRol permite quitárselo a otro rol', () => {
    const antes = rol({ id: 'r2', permisos: ['gestionar_roles'] });
    const despues = rol({ id: 'r2', permisos: [] });
    expect(validarCambioRol({ actor: usuario({ rolId: 'r1' }), rolAntes: antes, rolDespues: despues })).toBeNull();
  });

  it('validarCambioRol permite cambios en el propio rol que conservan gestionar_roles', () => {
    const antes = rol({ id: 'r1', permisos: ['gestionar_roles'] });
    const despues = rol({ id: 'r1', permisos: ['gestionar_roles', 'ver_panel'] });
    expect(validarCambioRol({ actor: usuario({ rolId: 'r1' }), rolAntes: antes, rolDespues: despues })).toBeNull();
  });

  it('puedeEliminarRol es false si hay usuarios con ese rol', () => {
    expect(puedeEliminarRol('r1', [usuario({ rolId: 'r1' })])).toBe(false);
  });

  it('puedeEliminarRol es true si nadie lo usa', () => {
    expect(puedeEliminarRol('r2', [usuario({ rolId: 'r1' })])).toBe(true);
  });
});

describe('esRutaPublica', () => {
  it('la zona del cliente (/agendar/<código>) es pública, con o sin query o fragmento', () => {
    expect(esRutaPublica('/agendar/ABC123')).toBe(true);
    expect(esRutaPublica('/agendar/ABC123?x=1')).toBe(true);
    expect(esRutaPublica('/agendar/ABC123#paso')).toBe(true);
  });

  it('el panel, el login y cualquier otra ruta no lo son', () => {
    for (const url of ['', '/', '/login', '/citas', '/panel', '/agendar', '/agendar/', '/agendarX/1', '/citas/agendar/1']) {
      expect(esRutaPublica(url), url).toBe(false);
    }
  });
});
