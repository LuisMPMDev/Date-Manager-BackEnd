import {
  afterNextRender, ChangeDetectionStrategy, Component, computed, ElementRef, inject, Injector, input, signal,
} from '@angular/core';
import { AgendaRepo } from '../../core/agenda-repo';
import { CambioNoPermitidoError, RolEnUsoError } from '../../core/errores';
import { Permiso, Rol, Usuario } from '../../core/models';
import { CATALOGO_PERMISOS, puedeEliminarRol } from '../../core/permisos';
import { SesionService } from '../../core/sesion';
import { nuevoId } from '../servicios/servicios-borrador';
import { alternarPermiso, validarRolEditado, verCitasObligatorio } from './usuarios-logica';

@Component({
  selector: 'app-roles-seccion',
  templateUrl: './roles-seccion.html',
  styleUrl: './usuarios-comun.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RolesSeccionComponent {
  private readonly repo = inject(AgendaRepo);
  private readonly sesion = inject(SesionService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly usuarios = input.required<Usuario[]>();
  readonly roles = input.required<Rol[]>();

  protected readonly catalogo = CATALOGO_PERMISOS;
  protected readonly textoVerCitas = 'Necesario para cancelar, mover o atender citas.';

  /** Copia del rol que se está creando o editando: la tabla no cambia hasta guardar. */
  protected readonly borrador = signal<Rol | null>(null);
  protected readonly confirmando = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly aviso = signal('');
  protected readonly error = signal('');

  protected readonly esNuevo = computed(() => !this.roles().some(r => r.id === this.borrador()?.id));
  protected readonly verCitasFijo = computed(() => verCitasObligatorio(this.borrador()?.permisos ?? []));

  protected readonly filas = computed(() => {
    const usuarios = this.usuarios();
    return this.roles().map(rol => ({
      rol,
      usuarios: usuarios.filter(u => u.rolId === rol.id).length,
      motivo: puedeEliminarRol(rol.id, usuarios) ? null : new RolEnUsoError().message,
    }));
  });

  private abiertoDesde: HTMLElement | null = null;

  protected nuevo(evento: Event): void {
    this.abrir({ id: nuevoId('rol'), nombre: '', descripcion: '', permisos: [] }, evento);
  }

  protected editar(rol: Rol, evento: Event): void {
    this.abrir({ ...rol, permisos: [...rol.permisos] }, evento);
  }

  protected escribir(campo: 'nombre' | 'descripcion', evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.borrador.update(b => (b ? { ...b, [campo]: valor } : b));
  }

  protected marcar(id: Permiso, evento: Event): void {
    const marcado = (evento.target as HTMLInputElement).checked;
    this.borrador.update(b => (b ? { ...b, permisos: alternarPermiso(b.permisos, id, marcado) } : b));
  }

  protected cancelar(): void {
    this.cerrar();
  }

  protected async guardar(evento?: Event): Promise<void> {
    evento?.preventDefault();
    const b = this.borrador();
    const actor = this.sesion.usuario();
    if (!b || !actor || this.guardando()) return;
    const rol: Rol = {
      id: b.id,
      nombre: b.nombre.trim(),
      descripcion: b.descripcion.trim(),
      permisos: CATALOGO_PERMISOS.map(p => p.id).filter(id => b.permisos.includes(id)),
    };
    const mensaje = validarRolEditado(rol, this.roles());
    if (mensaje) {
      this.error.set(mensaje);
      return;
    }
    this.guardando.set(true);
    this.error.set('');
    try {
      await this.repo.guardarRol(rol, actor.id);
    } catch (e) {
      this.error.set(e instanceof CambioNoPermitidoError ? e.message : 'No pudimos guardar el rol. Intenta de nuevo.');
      this.guardando.set(false);
      return;
    }
    this.guardando.set(false);
    await this.refrescarSesion();
    this.aviso.set('Rol guardado.');
    this.cerrar();
  }

  protected pedirEliminar(fila: { rol: Rol; motivo: string | null }): void {
    this.aviso.set('');
    if (fila.motivo) {
      this.error.set(fila.motivo);
      return;
    }
    this.error.set('');
    this.confirmando.set(fila.rol.id);
    this.enfocar(() => this.fila(fila.rol.id)?.querySelector<HTMLElement>('[data-volver]'));
  }

  protected volver(id: string): void {
    this.confirmando.set(null);
    this.enfocar(() => this.fila(id)?.querySelector<HTMLElement>('[data-eliminar]'));
  }

  protected async confirmarEliminar(rol: Rol): Promise<void> {
    this.confirmando.set(null);
    try {
      await this.repo.eliminarRol(rol.id);
    } catch (e) {
      this.error.set(e instanceof RolEnUsoError ? e.message : 'No pudimos eliminar el rol. Intenta de nuevo.');
      return;
    }
    await this.refrescarSesion();
    this.error.set('');
    this.aviso.set('Rol eliminado.');
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

  private abrir(borrador: Rol, evento: Event): void {
    this.abiertoDesde = evento.currentTarget as HTMLElement;
    this.aviso.set('');
    this.error.set('');
    this.confirmando.set(null);
    this.borrador.set(borrador);
    this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>('form h4'));
  }

  /** Cierra el editor y devuelve el foco al botón que lo abrió (o al de «Nuevo rol»). */
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
