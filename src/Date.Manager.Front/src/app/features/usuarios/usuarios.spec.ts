import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Rol } from '../../core/models';
import { Injectable } from '@angular/core';
import { DemoAgendaRepo } from '../../core/demo-agenda-repo';
import { alerta, celdas, configurar, crear, esperar, fijarPermisos, fila, repo, seccion, sesion } from './usuarios.pruebas';

const TODOS = [
  'ver_panel', 'ver_citas', 'gestionar_citas', 'atender_citas',
  'gestionar_servicios', 'gestionar_clientes', 'gestionar_usuarios', 'gestionar_roles',
] as const;

@Injectable()
class RepoLento extends DemoAgendaRepo {
  /** La primera lectura de roles tarda más y trae datos viejos: debe ignorarse. */
  private lecturas = 0;
  override async roles(): Promise<Rol[]> {
    const numero = ++this.lecturas;
    const datos = await super.roles();
    if (numero === 1) {
      await new Promise(r => setTimeout(r, 40));
      return datos.map(r => ({ ...r, nombre: `${r.nombre} (viejo)` }));
    }
    return datos;
  }
}

describe('UsuariosComponent', () => {
  beforeEach(() => configurar());

  it('con los dos permisos muestra las secciones Usuarios y Roles bajo el título de la pantalla', async () => {
    const { el } = await crear();
    expect(el.querySelector('h2')!.textContent).toContain('Usuarios y roles');
    expect(seccion(el, 'usuarios')).not.toBeNull();
    expect(seccion(el, 'roles')).not.toBeNull();
    expect(seccion(el, 'usuarios').getAttribute('aria-labelledby')).toBeTruthy();
  });

  it('con solo gestionar_usuarios no aparece la sección de roles', async () => {
    const { el } = await crear({
      antes: () => fijarPermisos('rol-admin', TODOS.filter(p => p !== 'gestionar_roles')),
    });
    expect(seccion(el, 'usuarios')).not.toBeNull();
    expect(seccion(el, 'roles')).toBeNull();
    expect(Array.from(el.querySelectorAll('button')).map(b => b.textContent?.trim())).not.toContain('Nuevo rol');
  });

  it('con solo gestionar_roles no aparece la sección de usuarios', async () => {
    const { el } = await crear({
      antes: () => fijarPermisos('rol-admin', TODOS.filter(p => p !== 'gestionar_usuarios')),
    });
    expect(seccion(el, 'roles')).not.toBeNull();
    expect(seccion(el, 'usuarios')).toBeNull();
    expect(Array.from(el.querySelectorAll('button')).map(b => b.textContent?.trim())).not.toContain('Nuevo usuario');
  });

  it('si la lectura falla estando la sesión iniciada, muestra «No se pudo cargar usuarios y roles.»', async () => {
    const { fixture, el } = await crear();
    vi.spyOn(repo(), 'roles').mockRejectedValue(new Error('sin conexión'));
    await repo().guardarRol({ id: 'rol-x', nombre: 'X', descripcion: '', permisos: [] }, 'sistema'); // sube `cambios`
    await esperar(fixture);
    expect(alerta(el)).toBe('No se pudo cargar usuarios y roles.');
  });

  it('se recalcula cuando cambian los datos desde otra parte', async () => {
    const { fixture, el } = await crear();
    await repo().guardarRol({ id: 'rol-nuevo', nombre: 'Nuevo desde fuera', descripcion: '', permisos: [] }, 'sistema');
    await esperar(fixture);
    expect(celdas(fila(seccion(el, 'roles'), 'rol-nuevo'))[0]).toBe('Nuevo desde fuera');
  });

  it('descarta una respuesta vieja que llega después de una más reciente', async () => {
    await configurar(RepoLento);
    const { fixture, el } = await crear();
    await repo().guardarRol({ id: 'rol-n', nombre: 'N', descripcion: '', permisos: [] }, 'sistema');
    await new Promise(r => setTimeout(r, 80));
    fixture.detectChanges();
    await esperar(fixture);
    const nombres = Array.from(seccion(el, 'roles').querySelectorAll('tbody tr')).map(f => celdas(f)[0]);
    expect(nombres.some(n => n.includes('(viejo)'))).toBe(false);
    expect(nombres).toContain('N');
  });

  it('si la persona pierde ambos permisos al guardar, la pantalla la manda a la primera ruta que le corresponde', async () => {
    const { fixture } = await crear();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    expect(navegar).not.toHaveBeenCalled();
    await fijarPermisos('rol-admin', ['ver_citas']);
    await sesion().refrescar();
    await esperar(fixture);
    expect(navegar).toHaveBeenCalledWith('/agenda');
  });
});
