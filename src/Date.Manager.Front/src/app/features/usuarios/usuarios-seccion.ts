import {
  afterNextRender, ChangeDetectionStrategy, Component, computed, ElementRef, inject, Injector, input, signal,
} from '@angular/core';
import { AgendaRepo } from '../../core/agenda-repo';
import { nuevoId } from '../../core/ids';
import { Rol, Usuario, UsuarioForm } from '../../core/models';
import { SesionService } from '../../core/sesion';
import { motivoNoCambiarRol, motivoNoEliminarUsuario, validarUsuario } from './usuarios-logica';

type CampoTexto = 'nombre' | 'apellido' | 'usuario' | 'correo' | 'telefono' | 'clave';

@Component({
  selector: 'app-usuarios-seccion',
  templateUrl: './usuarios-seccion.html',
  styleUrl: './usuarios-comun.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsuariosSeccionComponent {
  private readonly repo = inject(AgendaRepo);
  private readonly sesion = inject(SesionService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly usuarios = input.required<Usuario[]>();
  readonly roles = input.required<Rol[]>();

  /** Copia del usuario que se está creando o editando: la tabla no cambia hasta guardar. */
  protected readonly borrador = signal<UsuarioForm | null>(null);
  protected readonly confirmando = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly aviso = signal('');
  protected readonly error = signal('');

  protected readonly esNuevo = computed(() => !this.usuarios().some(u => u.id === this.borrador()?.id));

  protected readonly filas = computed(() => {
    const actorId = this.sesion.usuario()?.id ?? '';
    const usuarios = this.usuarios();
    const roles = this.roles();
    return usuarios.map(usuario => ({
      usuario,
      rol: roles.find(r => r.id === usuario.rolId)?.nombre ?? 'Sin rol',
      nombre: `${usuario.nombre} ${usuario.apellido}`,
      motivo: motivoNoEliminarUsuario({ objetivo: usuario, actorId, usuarios, roles }),
    }));
  });

  private abiertoDesde: HTMLElement | null = null;

  protected nuevo(evento: Event): void {
    const vacio: UsuarioForm = { id: nuevoId(), nombre: '', apellido: '', correo: '', telefono: '', usuario: '', clave: '', rolId: '' };
    this.abrir(vacio, evento);
  }

  protected editar(usuario: Usuario, evento: Event): void {
    this.abrir({ ...usuario, clave: '' }, evento);
  }

  protected escribir(campo: CampoTexto, evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.borrador.update(b => (b ? { ...b, [campo]: valor } : b));
  }

  protected elegirRol(evento: Event): void {
    const rolId = (evento.target as HTMLSelectElement).value;
    this.borrador.update(b => (b ? { ...b, rolId } : b));
  }

  protected cancelar(): void {
    this.cerrar();
  }

  protected async guardar(evento?: Event): Promise<void> {
    evento?.preventDefault();
    const b = this.borrador();
    if (!b || this.guardando()) return;
    const original = this.usuarios().find(u => u.id === b.id);
    const { clave, ...datos } = b;
    const usuario: UsuarioForm = {
      ...datos,
      nombre: b.nombre.trim(), apellido: b.apellido.trim(), usuario: b.usuario.trim(),
      correo: b.correo.trim(), telefono: b.telefono.trim(),
      // vacía al editar = conservar la actual: no se envía
      ...(clave ? { clave } : {}),
    };
    const mensaje =
      validarUsuario(usuario, this.usuarios(), this.roles(), !original) ??
      (original ? motivoNoCambiarRol({ antes: original, despues: usuario, usuarios: this.usuarios(), roles: this.roles() }) : null);
    if (mensaje) {
      this.error.set(mensaje);
      return;
    }
    this.guardando.set(true);
    this.error.set('');
    try {
      await this.repo.guardarUsuario(usuario);
    } catch {
      this.error.set('No pudimos guardar el usuario. Intenta de nuevo.');
      this.guardando.set(false);
      return;
    }
    this.guardando.set(false);
    await this.refrescarSesion();
    this.aviso.set('Usuario guardado.');
    this.cerrar();
  }

  protected pedirEliminar(fila: { usuario: Usuario; motivo: string | null }): void {
    this.aviso.set('');
    if (fila.motivo) {
      this.error.set(fila.motivo);
      return;
    }
    this.error.set('');
    this.confirmando.set(fila.usuario.id);
    this.enfocar(() => this.fila(fila.usuario.id)?.querySelector<HTMLElement>('[data-volver]'));
  }

  protected volver(id: string): void {
    this.confirmando.set(null);
    this.enfocar(() => this.fila(id)?.querySelector<HTMLElement>('[data-eliminar]'));
  }

  protected async confirmarEliminar(usuario: Usuario): Promise<void> {
    const motivo = motivoNoEliminarUsuario({
      objetivo: usuario, actorId: this.sesion.usuario()?.id ?? '', usuarios: this.usuarios(), roles: this.roles(),
    });
    this.confirmando.set(null);
    if (motivo) {
      this.error.set(motivo);
      return;
    }
    try {
      await this.repo.eliminarUsuario(usuario.id);
    } catch {
      this.error.set('No pudimos eliminar el usuario. Intenta de nuevo.');
      return;
    }
    await this.refrescarSesion();
    this.error.set('');
    this.aviso.set('Usuario eliminado.');
    this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>('[data-nuevo]'));
  }

  private fila(id: string): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>(`tr[data-fila="${id}"]`);
  }

  /** El menú y los permisos de quien usa la pantalla se actualizan al guardar; un fallo aquí no deshace lo guardado. */
  private async refrescarSesion(): Promise<void> {
    try {
      await this.sesion.refrescar();
    } catch {
      // la próxima lectura de datos volverá a intentarlo
    }
  }

  private abrir(borrador: UsuarioForm, evento: Event): void {
    this.abiertoDesde = evento.currentTarget as HTMLElement;
    this.aviso.set('');
    this.error.set('');
    this.confirmando.set(null);
    this.borrador.set(borrador);
    this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>('form h4'));
  }

  /** Cierra el formulario y devuelve el foco al botón que lo abrió (o al de «Nuevo usuario»). */
  private cerrar(): void {
    const origen = this.abiertoDesde;
    this.abiertoDesde = null;
    this.borrador.set(null);
    this.error.set('');
    this.enfocar(() => (origen?.isConnected ? origen : this.host.nativeElement.querySelector<HTMLElement>('[data-nuevo]')));
  }

  private enfocar(buscar: () => HTMLElement | null | undefined): void {
    afterNextRender(() => buscar()?.focus(), { injector: this.injector });
  }
}
