import { CATALOGO_PERMISOS } from '../../core/permisos';
import {
  alerta, aviso, boton, botonDe, campo, celdas, configurar, crear, elegir, escribir, esperar, fila, filasDe, marcar, pulsar,
  repo, seccion, sesion,
} from './usuarios.pruebas';

const casilla = (el: HTMLElement, permiso: string) =>
  seccion(el, 'roles').querySelector<HTMLInputElement>(`form input[type="checkbox"][name="permiso-${permiso}"]`)!;
const roles = (el: HTMLElement) => seccion(el, 'roles');

async function nuevoRol(fixture: Parameters<typeof esperar>[0], el: HTMLElement) {
  await pulsar(fixture, boton(roles(el), 'Nuevo rol'));
}

describe('Usuarios y roles: sección Roles', () => {
  beforeEach(() => configurar());

  it('lista los roles con descripción, cuántos usuarios los tienen y cuántos permisos', async () => {
    const { el } = await crear();
    const s = roles(el);
    expect(s.querySelector('h3')!.textContent).toContain('Roles');
    expect(Array.from(s.querySelectorAll('thead th')).map(t => t.textContent?.trim()))
      .toEqual(['Nombre', 'Descripción', 'Usuarios con este rol', 'Permisos', 'Acciones']);
    expect(celdas(fila(s, 'rol-admin')).slice(0, 4)).toEqual(['Administrador', 'Acceso completo al sistema', '1', '8']);
    expect(celdas(fila(s, 'rol-recepcion')).slice(0, 4)).toEqual(['Recepción', 'Atiende la agenda y a los clientes', '1', '4']);
  });

  it('los botones de fila incluyen el rol en su nombre accesible y empiezan por el texto visible', async () => {
    const { el } = await crear();
    const editar = botonDe(roles(el), 'Editar rol Recepción');
    const eliminar = botonDe(roles(el), 'Eliminar rol Recepción');
    expect(editar.textContent?.trim()).toBe('Editar');
    expect(eliminar.textContent?.trim()).toBe('Eliminar');
  });

  describe('editor', () => {
    it('«Nuevo rol» abre el editor con una casilla por permiso del catálogo, con su etiqueta', async () => {
      const { fixture, el } = await crear();
      expect(roles(el).querySelector('form')).toBeNull();
      await nuevoRol(fixture, el);

      const fieldset = roles(el).querySelector('form fieldset')!;
      expect(fieldset.querySelector('legend')!.textContent?.trim()).toBe('Permisos');
      for (const p of CATALOGO_PERMISOS) {
        const c = casilla(el, p.id);
        expect(c.checked).toBe(false);
        expect(roles(el).querySelector(`label[for="${c.id}"]`)!.textContent?.trim()).toBe(p.etiqueta);
      }
      for (const n of ['nombre', 'descripcion']) {
        expect(roles(el).querySelector(`label[for="${campo(roles(el), n).id}"]`)).not.toBeNull();
      }
      expect(boton(roles(el), 'Guardar rol')).toBeTruthy();
      expect(boton(roles(el), 'Cancelar')).toBeTruthy();
    });

    it('marcar «Cancelar y mover citas» marca también «Ver citas»', async () => {
      const { fixture, el } = await crear();
      await nuevoRol(fixture, el);
      expect(casilla(el, 'ver_citas').checked).toBe(false);

      await marcar(fixture, casilla(el, 'gestionar_citas'), true);
      expect(casilla(el, 'gestionar_citas').checked).toBe(true);
      expect(casilla(el, 'ver_citas').checked).toBe(true);
    });

    it('marcar «Marcar citas como atendidas» también marca «Ver citas»', async () => {
      const { fixture, el } = await crear();
      await nuevoRol(fixture, el);
      await marcar(fixture, casilla(el, 'atender_citas'), true);
      expect(casilla(el, 'ver_citas').checked).toBe(true);
    });

    it('«Ver citas» queda bloqueada, con su explicación, mientras haya cancelar/mover o atender citas', async () => {
      const { fixture, el } = await crear();
      await nuevoRol(fixture, el);
      expect(casilla(el, 'ver_citas').disabled).toBe(false);

      await marcar(fixture, casilla(el, 'gestionar_citas'), true);
      const ver = casilla(el, 'ver_citas');
      expect(ver.disabled).toBe(true);
      const ayuda = roles(el).querySelector(`#${ver.getAttribute('aria-describedby')}`)!;
      expect(ayuda.textContent?.trim()).toBe('Necesario para cancelar, mover o atender citas.');

      await marcar(fixture, casilla(el, 'gestionar_citas'), false);
      expect(ver.disabled).toBe(false);
      expect(ver.checked).toBe(true); // se queda marcada, pero ya se puede quitar
      await marcar(fixture, ver, false);
      expect(ver.checked).toBe(false);
    });

    it('«Guardar rol» crea el rol con los permisos marcados (más sus dependencias) y avisa', async () => {
      const { fixture, el } = await crear();
      await nuevoRol(fixture, el);
      escribir(campo(roles(el), 'nombre'), '  Solo lectura ');
      escribir(campo(roles(el), 'descripcion'), 'Mira la agenda');
      await marcar(fixture, casilla(el, 'atender_citas'), true);
      await pulsar(fixture, boton(roles(el), 'Guardar rol'));

      const creado = (await repo().roles()).find(r => r.nombre === 'Solo lectura')!;
      expect(creado.descripcion).toBe('Mira la agenda');
      expect([...creado.permisos].sort()).toEqual(['atender_citas', 'ver_citas']);
      expect(roles(el).querySelector('form')).toBeNull();
      expect(celdas(fila(roles(el), creado.id)).slice(0, 4)).toEqual(['Solo lectura', 'Mira la agenda', '0', '2']);
      expect(aviso(roles(el))).toBe('Rol guardado.');
    });

    it('crear un rol nuevo con permisos y asignarlo a un usuario lo hace aparecer en el selector', async () => {
      const { fixture, el } = await crear();
      const usuarios = seccion(el, 'usuarios');
      await pulsar(fixture, boton(usuarios, 'Nuevo usuario'));
      const opciones = () => Array.from(campo<HTMLSelectElement>(usuarios, 'rol').options).map(o => o.textContent?.trim());
      expect(opciones()).not.toContain('Solo citas');
      await pulsar(fixture, boton(usuarios, 'Cancelar'));

      await nuevoRol(fixture, el);
      escribir(campo(roles(el), 'nombre'), 'Solo citas');
      await marcar(fixture, casilla(el, 'ver_citas'), true);
      await pulsar(fixture, boton(roles(el), 'Guardar rol'));

      await pulsar(fixture, boton(usuarios, 'Nuevo usuario'));
      expect(opciones()).toContain('Solo citas');
      const idNuevo = (await repo().roles()).find(r => r.nombre === 'Solo citas')!.id;
      escribir(campo(usuarios, 'nombre'), 'Lucía');
      escribir(campo(usuarios, 'apellido'), 'Mora');
      escribir(campo(usuarios, 'usuario'), 'lucia');
      escribir(campo(usuarios, 'correo'), 'lucia@datemanager.demo');
      escribir(campo(usuarios, 'clave'), 'clave1');
      elegir(campo<HTMLSelectElement>(usuarios, 'rol'), idNuevo);
      await pulsar(fixture, boton(usuarios, 'Guardar usuario'));

      const guardado = (await repo().usuarios()).find(u => u.usuario === 'lucia')!;
      expect(guardado.rolId).toBe(idNuevo);
      expect(celdas(fila(usuarios, guardado.id))[5]).toBe('Solo citas');
      expect(celdas(fila(roles(el), idNuevo))[2]).toBe('1');
    });

    it('«Editar» muestra los datos del rol y «Cancelar» descarta lo escrito: se edita una copia', async () => {
      const { fixture, el } = await crear();
      await pulsar(fixture, botonDe(roles(el), 'Editar rol Recepción'));
      expect(campo(roles(el), 'nombre').value).toBe('Recepción');
      expect(casilla(el, 'gestionar_clientes').checked).toBe(true);
      expect(casilla(el, 'gestionar_servicios').checked).toBe(false);

      escribir(campo(roles(el), 'nombre'), 'Otro nombre');
      await marcar(fixture, casilla(el, 'gestionar_servicios'), true);
      expect(celdas(fila(roles(el), 'rol-recepcion'))[0]).toBe('Recepción'); // la tabla no cambia mientras se escribe

      await pulsar(fixture, boton(roles(el), 'Cancelar'));
      expect(roles(el).querySelector('form')).toBeNull();
      expect((await repo().roles()).find(r => r.id === 'rol-recepcion')!.nombre).toBe('Recepción');

      await pulsar(fixture, botonDe(roles(el), 'Editar rol Recepción'));
      expect(campo(roles(el), 'nombre').value).toBe('Recepción');
      expect(casilla(el, 'gestionar_servicios').checked).toBe(false);
    });

    it('el nombre es obligatorio y no puede repetir el de otro rol, y la alerta lo explica', async () => {
      const { fixture, el } = await crear();
      await nuevoRol(fixture, el);
      await pulsar(fixture, boton(roles(el), 'Guardar rol'));
      expect(alerta(roles(el))).toBe('Escribe el nombre del rol.');

      escribir(campo(roles(el), 'nombre'), 'ADMINISTRADOR');
      await pulsar(fixture, boton(roles(el), 'Guardar rol'));
      expect(alerta(roles(el))).toBe('Ya existe otro rol con ese nombre.');
      expect(await repo().roles()).toHaveLength(2);
      expect(roles(el).querySelector('form')).not.toBeNull();
    });

    it('el administrador no puede quitarse gestionar_roles de su rol y ve el mensaje de error', async () => {
      const { fixture, el } = await crear();
      await pulsar(fixture, botonDe(roles(el), 'Editar rol Administrador'));
      await marcar(fixture, casilla(el, 'gestionar_roles'), false);
      await pulsar(fixture, boton(roles(el), 'Guardar rol'));

      expect(alerta(roles(el))).toBe('No puedes quitarte el permiso de gestionar roles de tu propio rol.');
      expect((await repo().roles()).find(r => r.id === 'rol-admin')!.permisos).toContain('gestionar_roles');
      expect(roles(el).querySelector('form')).not.toBeNull();
    });

    it('si el repositorio falla al guardar con otro error, avisa y deja el editor abierto', async () => {
      const { fixture, el } = await crear();
      vi.spyOn(repo(), 'guardarRol').mockRejectedValueOnce(new Error('sin conexión'));
      await nuevoRol(fixture, el);
      escribir(campo(roles(el), 'nombre'), 'Nuevo');
      await pulsar(fixture, boton(roles(el), 'Guardar rol'));
      expect(alerta(roles(el))).toBe('No pudimos guardar el rol. Intenta de nuevo.');
      expect(roles(el).querySelector('form')).not.toBeNull();
    });

    it('al guardar el rol de la sesión, los permisos de quien usa la pantalla se actualizan al instante', async () => {
      const { fixture, el } = await crear();
      expect(sesion().puede('ver_panel')).toBe(true);
      await pulsar(fixture, botonDe(roles(el), 'Editar rol Administrador'));
      await marcar(fixture, casilla(el, 'ver_panel'), false);
      await pulsar(fixture, boton(roles(el), 'Guardar rol'));

      expect(alerta(roles(el))).toBe('');
      expect(sesion().puede('ver_panel')).toBe(false);
      expect(sesion().puede('gestionar_roles')).toBe(true);
    });

    it('la alerta no vuelve al abrir otro rol', async () => {
      const { fixture, el } = await crear();
      await nuevoRol(fixture, el);
      await pulsar(fixture, boton(roles(el), 'Guardar rol'));
      expect(alerta(roles(el))).not.toBe('');
      await pulsar(fixture, boton(roles(el), 'Cancelar'));
      await pulsar(fixture, botonDe(roles(el), 'Editar rol Recepción'));
      expect(alerta(roles(el))).toBe('');
    });
  });

  describe('eliminar', () => {
    async function conRolSinUsuarios() {
      const r = await repo().guardarRol({ id: 'rol-libre', nombre: 'Libre', descripcion: '', permisos: ['ver_citas'] }, 'sistema');
      return r.id;
    }

    it('eliminar un rol con usuarios muestra el aviso y no lo elimina', async () => {
      const { fixture, el } = await crear();
      const eliminar = botonDe(roles(el), 'Eliminar rol Recepción');
      expect(eliminar.getAttribute('aria-disabled')).toBe('true');
      expect(roles(el).querySelector(`#${eliminar.getAttribute('aria-describedby')}`)!.textContent)
        .toContain('tiene usuarios asignados');

      await pulsar(fixture, eliminar);
      expect(alerta(roles(el))).toBe('No se puede eliminar un rol que tiene usuarios asignados.');
      expect(boton(roles(el), 'Sí, eliminar')).toBeUndefined(); // no llega a pedir confirmación
      expect((await repo().roles()).map(r => r.id)).toContain('rol-recepcion');
      expect(fila(roles(el), 'rol-recepcion')).not.toBeNull();
    });

    it('un rol sin usuarios pide confirmación en la misma fila y se elimina con «Sí, eliminar»', async () => {
      const id = await conRolSinUsuarios();
      const { fixture, el } = await crear();
      const eliminar = botonDe(roles(el), 'Eliminar rol Libre');
      expect(eliminar.getAttribute('aria-disabled')).not.toBe('true');

      await pulsar(fixture, eliminar);
      expect(fila(roles(el), id).textContent).toContain('¿Eliminar el rol Libre?');
      expect((await repo().roles()).map(r => r.id)).toContain(id); // todavía no
      await pulsar(fixture, boton(roles(el), 'Sí, eliminar'));

      expect((await repo().roles()).map(r => r.id)).not.toContain(id);
      expect(roles(el).querySelector(`tr[data-fila="${id}"]`)).toBeNull();
      expect(aviso(roles(el))).toBe('Rol eliminado.');
    });

    it('«No, volver» cancela la confirmación sin eliminar y devuelve el foco a «Eliminar»', async () => {
      const id = await conRolSinUsuarios();
      const { fixture, el } = await crear();
      await pulsar(fixture, botonDe(roles(el), 'Eliminar rol Libre'));
      await pulsar(fixture, boton(roles(el), 'No, volver'));

      expect((await repo().roles()).map(r => r.id)).toContain(id);
      expect(fila(roles(el), id).textContent).not.toContain('¿Eliminar');
      expect(document.activeElement).toBe(botonDe(roles(el), 'Eliminar rol Libre'));
    });

    it('si alguien asignó el rol mientras se confirmaba, muestra el aviso y no elimina', async () => {
      const id = await conRolSinUsuarios();
      const { fixture, el } = await crear();
      await pulsar(fixture, botonDe(roles(el), 'Eliminar rol Libre'));
      const andres = (await repo().usuarios()).find(u => u.usuario === 'andres')!;
      await repo().guardarUsuario({ ...andres, rolId: id });
      await esperar(fixture);
      await pulsar(fixture, boton(roles(el), 'Sí, eliminar'));

      expect(alerta(roles(el))).toBe('No se puede eliminar un rol que tiene usuarios asignados.');
      expect((await repo().roles()).map(r => r.id)).toContain(id);
      expect(boton(roles(el), 'Sí, eliminar')).toBeUndefined();
    });
  });

  describe('foco', () => {
    it('al abrir el editor el foco va al título del formulario y al cerrar vuelve al botón que lo abrió', async () => {
      const { fixture, el } = await crear();
      const abre = botonDe(roles(el), 'Editar rol Recepción');
      abre.focus();
      await pulsar(fixture, abre);
      expect(document.activeElement).toBe(roles(el).querySelector('form h4'));
      expect(roles(el).querySelector('form h4')!.textContent).toContain('Editar rol');

      await pulsar(fixture, boton(roles(el), 'Cancelar'));
      expect(document.activeElement).toBe(botonDe(roles(el), 'Editar rol Recepción'));
    });

    it('tras guardar un rol nuevo, el foco vuelve a «Nuevo rol»', async () => {
      const { fixture, el } = await crear();
      const nuevo = boton(roles(el), 'Nuevo rol');
      nuevo.focus();
      await pulsar(fixture, nuevo);
      expect(roles(el).querySelector('form h4')!.textContent).toContain('Nuevo rol');
      escribir(campo(roles(el), 'nombre'), 'Otro');
      await pulsar(fixture, boton(roles(el), 'Guardar rol'));
      expect(document.activeElement).toBe(boton(roles(el), 'Nuevo rol'));
    });
  });

  it('la lista de roles tiene una fila por rol', async () => {
    const { el } = await crear();
    expect(filasDe(roles(el))).toHaveLength(2);
  });
});
