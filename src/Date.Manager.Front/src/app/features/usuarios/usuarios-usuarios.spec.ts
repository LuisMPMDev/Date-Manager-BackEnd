import {
  alerta, aviso, boton, botonDe, campo, celdas, configurar, crear, elegir, escribir, fijarPermisos, fila, filasDe,
  pulsar, repo, seccion, sesion,
} from './usuarios.pruebas';
import { UsuarioForm } from '../../core/models';

const usuarios = (el: HTMLElement) => seccion(el, 'usuarios');

async function llenar(s: HTMLElement, datos: Partial<Record<string, string>>) {
  for (const [nombre, valor] of Object.entries(datos)) {
    const c = campo<HTMLInputElement | HTMLSelectElement>(s, nombre);
    if (c instanceof HTMLSelectElement) elegir(c, valor!);
    else escribir(c, valor!);
  }
}

const NUEVO = {
  nombre: 'Lucía', apellido: 'Mora', usuario: 'lucia', correo: 'lucia@datemanager.demo',
  telefono: '300 555 0300', clave: 'clave1', rol: 'rol-recepcion',
};

describe('Usuarios y roles: sección Usuarios', () => {
  beforeEach(() => configurar());

  it('lista los usuarios con nombre, apellido, usuario, correo, teléfono y el nombre de su rol', async () => {
    const { el } = await crear();
    const s = usuarios(el);
    expect(s.querySelector('h3')!.textContent).toContain('Usuarios');
    expect(Array.from(s.querySelectorAll('thead th')).map(t => t.textContent?.trim()))
      .toEqual(['Nombre', 'Apellido', 'Usuario', 'Correo', 'Teléfono', 'Rol', 'Acciones']);
    expect(celdas(fila(s, 'usr-marta')).slice(0, 6))
      .toEqual(['Marta', 'Rivas', 'marta', 'marta@datemanager.demo', '300 555 0201', 'Administrador']);
    expect(celdas(fila(s, 'usr-andres'))[5]).toBe('Recepción');
    expect(JSON.stringify(celdas(fila(s, 'usr-marta')))).not.toContain('demo123'); // nunca se muestra la clave
  });

  it('los botones de fila incluyen al usuario en su nombre accesible y empiezan por el texto visible', async () => {
    const { el } = await crear();
    expect(botonDe(usuarios(el), 'Editar usuario Andrés Pardo').textContent?.trim()).toBe('Editar');
    expect(botonDe(usuarios(el), 'Eliminar usuario Andrés Pardo').textContent?.trim()).toBe('Eliminar');
  });

  describe('crear y editar', () => {
    it('«Nuevo usuario» abre el formulario con todos los campos rotulados y el selector de roles', async () => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      expect(s.querySelector('form')).toBeNull();
      await pulsar(fixture, boton(s, 'Nuevo usuario'));

      for (const n of ['nombre', 'apellido', 'usuario', 'correo', 'telefono', 'clave', 'rol']) {
        expect(s.querySelector(`label[for="${campo(s, n).id}"]`), n).not.toBeNull();
      }
      expect(campo(s, 'clave').getAttribute('type')).toBe('password');
      const opciones = Array.from(campo<HTMLSelectElement>(s, 'rol').options).map(o => o.textContent?.trim());
      expect(opciones).toEqual(expect.arrayContaining(['Administrador', 'Recepción']));
      expect(boton(s, 'Guardar usuario')).toBeTruthy();
      expect(boton(s, 'Cancelar')).toBeTruthy();
    });

    it('«Guardar usuario» crea el usuario, cierra el formulario y avisa', async () => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      await pulsar(fixture, boton(s, 'Nuevo usuario'));
      await llenar(s, { ...NUEVO, nombre: '  Lucía ' });
      await pulsar(fixture, boton(s, 'Guardar usuario'));

      const guardado = (await repo().usuarios()).find(u => u.usuario === 'lucia')!;
      expect(guardado).toMatchObject({ nombre: 'Lucía', apellido: 'Mora', correo: 'lucia@datemanager.demo', telefono: '300 555 0300', clave: 'clave1', rolId: 'rol-recepcion' });
      expect(s.querySelector('form')).toBeNull();
      expect(filasDe(s)).toHaveLength(3);
      expect(aviso(s)).toBe('Usuario guardado.');
    });

    it('el teléfono es opcional', async () => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      await pulsar(fixture, boton(s, 'Nuevo usuario'));
      await llenar(s, { ...NUEVO, telefono: '' });
      await pulsar(fixture, boton(s, 'Guardar usuario'));
      expect(alerta(s)).toBe('');
      expect((await repo().usuarios()).some(u => u.usuario === 'lucia')).toBe(true);
    });

    it.each([
      ['nombre', '', 'Escribe el nombre.'],
      ['apellido', '', 'Escribe el apellido.'],
      ['usuario', '', 'Escribe el nombre de usuario.'],
      ['correo', 'lucia@correo', 'Escribe un correo válido, por ejemplo nombre@correo.com.'],
      ['clave', '', 'Escribe la clave.'],
      ['rol', '', 'Elige un rol.'],
      ['usuario', 'ANDRES', 'Ya existe otro usuario con ese nombre de usuario.'],
    ])('no guarda si %s = «%s»: muestra «%s»', async (nombre, valor, mensaje) => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      await pulsar(fixture, boton(s, 'Nuevo usuario'));
      await llenar(s, { ...NUEVO, [nombre]: valor });
      await pulsar(fixture, boton(s, 'Guardar usuario'));

      expect(alerta(s)).toBe(mensaje);
      expect(await repo().usuarios()).toHaveLength(2);
      expect(s.querySelector('form')).not.toBeNull();
    });

    it('«Editar» muestra los datos del usuario, con la clave vacía y el rol elegido', async () => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      await pulsar(fixture, botonDe(s, 'Editar usuario Andrés Pardo'));
      expect(s.querySelector('form h4')!.textContent).toContain('Editar usuario');
      expect(campo(s, 'nombre').value).toBe('Andrés');
      expect(campo(s, 'usuario').value).toBe('andres');
      expect(campo(s, 'telefono').value).toBe('300 555 0202');
      expect(campo(s, 'clave').value).toBe('');
      expect(campo<HTMLSelectElement>(s, 'rol').value).toBe('rol-recepcion');
    });

    it('al editar, la clave vacía conserva la actual y una clave nueva la reemplaza', async () => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      await pulsar(fixture, botonDe(s, 'Editar usuario Andrés Pardo'));
      await llenar(s, { nombre: 'Andrés Felipe' });
      await pulsar(fixture, boton(s, 'Guardar usuario'));
      let andres = (await repo().usuarios()).find(u => u.id === 'usr-andres')! as UsuarioForm;
      expect(andres).toMatchObject({ nombre: 'Andrés Felipe', clave: 'demo123' });
      expect(celdas(fila(s, 'usr-andres'))[0]).toBe('Andrés Felipe');

      await pulsar(fixture, botonDe(s, 'Editar usuario Andrés Felipe Pardo'));
      await llenar(s, { clave: 'otra-clave' });
      await pulsar(fixture, boton(s, 'Guardar usuario'));
      andres = (await repo().usuarios()).find(u => u.id === 'usr-andres')! as UsuarioForm;
      expect(andres.clave).toBe('otra-clave');
    });

    it('al editar, conservar el propio usuario (aunque cambien las mayúsculas) no cuenta como repetido', async () => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      await pulsar(fixture, botonDe(s, 'Editar usuario Andrés Pardo'));
      await llenar(s, { usuario: 'Andres' });
      await pulsar(fixture, boton(s, 'Guardar usuario'));
      expect(alerta(s)).toBe('');
      expect((await repo().usuarios()).find(u => u.id === 'usr-andres')!.usuario).toBe('Andres');
    });

    it('«Cancelar» descarta lo escrito: se edita una copia', async () => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      await pulsar(fixture, botonDe(s, 'Editar usuario Andrés Pardo'));
      await llenar(s, { nombre: 'Otro' });
      expect(celdas(fila(s, 'usr-andres'))[0]).toBe('Andrés');
      await pulsar(fixture, boton(s, 'Cancelar'));
      expect(s.querySelector('form')).toBeNull();
      expect((await repo().usuarios()).find(u => u.id === 'usr-andres')!.nombre).toBe('Andrés');
      await pulsar(fixture, botonDe(s, 'Editar usuario Andrés Pardo'));
      expect(campo(s, 'nombre').value).toBe('Andrés');
    });

    it('si el repositorio falla al guardar, avisa y deja el formulario abierto', async () => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      vi.spyOn(repo(), 'guardarUsuario').mockRejectedValueOnce(new Error('sin conexión'));
      await pulsar(fixture, boton(s, 'Nuevo usuario'));
      await llenar(s, NUEVO);
      await pulsar(fixture, boton(s, 'Guardar usuario'));
      expect(alerta(s)).toBe('No pudimos guardar el usuario. Intenta de nuevo.');
      expect(s.querySelector('form')).not.toBeNull();
      expect(campo(s, 'nombre').value).toBe('Lucía');
    });

    it('editar a la persona que tiene la sesión actualiza la sesión (nombre y permisos) sin guardar la clave', async () => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      await pulsar(fixture, botonDe(s, 'Editar usuario Marta Rivas'));
      await llenar(s, { nombre: 'Marta Elena' });
      await pulsar(fixture, boton(s, 'Guardar usuario'));
      expect(sesion().usuario()?.nombre).toBe('Marta Elena');
      expect(JSON.stringify({ ...sessionStorage })).not.toContain('demo123'); // la sesión solo guarda el token
    });
  });

  describe('protecciones', () => {
    it('nadie puede eliminarse a sí mismo: el botón avisa y no pide confirmación', async () => {
      // andres gestiona usuarios (no roles) y marta sigue siendo la gestora de roles
      const { fixture, el } = await crear({
        usuario: 'andres',
        antes: () => fijarPermisos('rol-recepcion', ['gestionar_usuarios']),
      });
      const s = usuarios(el);
      const eliminar = botonDe(s, 'Eliminar usuario Andrés Pardo');
      expect(eliminar.getAttribute('aria-disabled')).toBe('true');
      expect(s.querySelector(`#${eliminar.getAttribute('aria-describedby')}`)!.textContent).toContain('tu propio usuario');

      await pulsar(fixture, eliminar);
      expect(alerta(s)).toBe('No puedes eliminar tu propio usuario.');
      expect(boton(s, 'Sí, eliminar')).toBeUndefined();
      expect(await repo().usuarios()).toHaveLength(2);
    });

    it('no se puede eliminar al último usuario que gestiona roles', async () => {
      const { fixture, el } = await crear({
        usuario: 'andres',
        antes: () => fijarPermisos('rol-recepcion', ['gestionar_usuarios']),
      });
      const s = usuarios(el);
      await pulsar(fixture, botonDe(s, 'Eliminar usuario Marta Rivas'));
      expect(alerta(s)).toBe('Debe quedar al menos un usuario que pueda gestionar roles.');
      expect((await repo().usuarios()).map(u => u.id)).toContain('usr-marta');
    });

    it('no se puede cambiar el rol del último gestor de roles a uno sin gestionar_roles', async () => {
      const { fixture, el } = await crear({
        usuario: 'andres',
        antes: () => fijarPermisos('rol-recepcion', ['gestionar_usuarios']),
      });
      const s = usuarios(el);
      await pulsar(fixture, botonDe(s, 'Editar usuario Marta Rivas'));
      await llenar(s, { rol: 'rol-recepcion' });
      await pulsar(fixture, boton(s, 'Guardar usuario'));

      expect(alerta(s)).toBe('Debe quedar al menos un usuario que pueda gestionar roles.');
      expect((await repo().usuarios()).find(u => u.id === 'usr-marta')!.rolId).toBe('rol-admin');
      expect(s.querySelector('form')).not.toBeNull();
    });

    it('si queda otro gestor de roles, sí se puede eliminar a Marta', async () => {
      const { fixture, el } = await crear({
        usuario: 'andres',
        antes: async () => {
          await fijarPermisos('rol-recepcion', ['gestionar_usuarios', 'gestionar_roles']);
        },
      });
      const s = usuarios(el);
      await pulsar(fixture, botonDe(s, 'Eliminar usuario Marta Rivas'));
      expect(alerta(s)).toBe('');
      await pulsar(fixture, boton(s, 'Sí, eliminar'));
      expect((await repo().usuarios()).map(u => u.id)).not.toContain('usr-marta');
    });
  });

  describe('eliminar', () => {
    async function conUsuarioExtra() {
      await repo().guardarUsuario({
        id: 'usr-extra', nombre: 'Pablo', apellido: 'Núñez', correo: 'pablo@datemanager.demo',
        telefono: '', usuario: 'pablo', clave: 'x', rolId: 'rol-recepcion',
      });
    }

    it('pide confirmación en la misma fila y elimina con «Sí, eliminar»', async () => {
      const { fixture, el } = await crear({ antes: conUsuarioExtra });
      const s = usuarios(el);
      await pulsar(fixture, botonDe(s, 'Eliminar usuario Pablo Núñez'));
      expect(fila(s, 'usr-extra').textContent).toContain('¿Eliminar a Pablo Núñez?');
      expect((await repo().usuarios()).map(u => u.id)).toContain('usr-extra');

      await pulsar(fixture, boton(s, 'Sí, eliminar'));
      expect((await repo().usuarios()).map(u => u.id)).not.toContain('usr-extra');
      expect(s.querySelector('tr[data-fila="usr-extra"]')).toBeNull();
      expect(aviso(s)).toBe('Usuario eliminado.');
    });

    it('«No, volver» cancela, no elimina y devuelve el foco a «Eliminar»', async () => {
      const { fixture, el } = await crear({ antes: conUsuarioExtra });
      const s = usuarios(el);
      await pulsar(fixture, botonDe(s, 'Eliminar usuario Pablo Núñez'));
      await pulsar(fixture, boton(s, 'No, volver'));
      expect((await repo().usuarios()).map(u => u.id)).toContain('usr-extra');
      expect(fila(s, 'usr-extra').textContent).not.toContain('¿Eliminar');
      expect(document.activeElement).toBe(botonDe(s, 'Eliminar usuario Pablo Núñez'));
    });

    it('eliminar al usuario de otra sesión no afecta a la sesión actual', async () => {
      const { fixture, el } = await crear({ antes: conUsuarioExtra });
      const s = usuarios(el);
      await pulsar(fixture, botonDe(s, 'Eliminar usuario Pablo Núñez'));
      await pulsar(fixture, boton(s, 'Sí, eliminar'));
      expect(sesion().usuario()?.usuario).toBe('marta');
    });
  });

  describe('foco', () => {
    it('al abrir el formulario el foco va a su título y al cerrar vuelve al botón que lo abrió', async () => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      const abre = botonDe(s, 'Editar usuario Andrés Pardo');
      abre.focus();
      await pulsar(fixture, abre);
      expect(document.activeElement).toBe(s.querySelector('form h4'));
      await pulsar(fixture, boton(s, 'Cancelar'));
      expect(document.activeElement).toBe(botonDe(s, 'Editar usuario Andrés Pardo'));
    });

    it('tras guardar un usuario nuevo, el foco vuelve a «Nuevo usuario»', async () => {
      const { fixture, el } = await crear();
      const s = usuarios(el);
      await pulsar(fixture, boton(s, 'Nuevo usuario'));
      expect(document.activeElement).toBe(s.querySelector('form h4'));
      await llenar(s, NUEVO);
      await pulsar(fixture, boton(s, 'Guardar usuario'));
      expect(document.activeElement).toBe(boton(s, 'Nuevo usuario'));
    });
  });
});
