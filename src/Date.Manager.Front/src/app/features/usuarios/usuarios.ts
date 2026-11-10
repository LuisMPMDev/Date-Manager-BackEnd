import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { Rol, Usuario } from '../../core/models';
import { primeraRuta } from '../../core/permisos';
import { SesionService } from '../../core/sesion';
import { AVISO_SIN_PERMISO, esSinPermiso } from '../../shared/errores';
import { RolesSeccionComponent } from './roles-seccion';
import { UsuariosSeccionComponent } from './usuarios-seccion';

@Component({
  selector: 'app-usuarios',
  imports: [UsuariosSeccionComponent, RolesSeccionComponent],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsuariosComponent {
  private readonly repo = inject(AgendaRepo);
  private readonly router = inject(Router);
  private readonly sesion = inject(SesionService);

  protected readonly usuarios = signal<Usuario[]>([]);
  protected readonly roles = signal<Rol[]>([]);
  protected readonly cargado = signal(false);
  protected readonly falloCarga = signal(false);
  /** Un 403 en una de las dos lecturas: esa sección avisa y el resto de la pantalla sigue funcionando. */
  protected readonly avisoUsuarios = signal('');
  protected readonly avisoRoles = signal('');
  private pedido = 0; // descarta respuestas que llegan tarde

  protected readonly verUsuarios = computed(() => this.sesion.puede('gestionar_usuarios'));
  protected readonly verRoles = computed(() => this.sesion.puede('gestionar_roles'));

  constructor() {
    effect(() => {
      this.repo.cambios();
      void this.cargar();
    });
    // Si un cambio guardado aquí le quita a la persona el acceso a esta pantalla, la sacamos de ella.
    effect(() => {
      if (!this.sesion.usuario() || this.verUsuarios() || this.verRoles()) return;
      this.router.navigateByUrl(primeraRuta(this.sesion.rol())).catch(() => undefined);
    });
  }

  /** Las secciones piden recargar cuando la API dice que lo que se ve quedó viejo (un 409). */
  protected recargar(): void {
    void this.cargar();
  }

  /** Cada lectura es auxiliar de la otra: un 403 en una avisa en su sección; en las dos, la pantalla no es para este rol. */
  private async cargar(): Promise<void> {
    const numero = ++this.pedido;
    const [usuarios, roles] = await Promise.allSettled([this.repo.usuarios(), this.repo.roles()]);
    if (numero !== this.pedido) return;
    const sinUsuarios = usuarios.status === 'rejected' && esSinPermiso(usuarios.reason);
    const sinRoles = roles.status === 'rejected' && esSinPermiso(roles.reason);
    if (sinUsuarios && sinRoles) {
      void this.router.navigateByUrl('/sin-acceso');
      return;
    }
    if ((usuarios.status === 'rejected' && !sinUsuarios) || (roles.status === 'rejected' && !sinRoles)) {
      this.falloCarga.set(true);
      return;
    }
    this.usuarios.set(usuarios.status === 'fulfilled' ? usuarios.value : []);
    this.roles.set(roles.status === 'fulfilled' ? roles.value : []);
    this.avisoUsuarios.set(sinUsuarios ? AVISO_SIN_PERMISO : '');
    this.avisoRoles.set(sinRoles ? AVISO_SIN_PERMISO : '');
    this.cargado.set(true);
    this.falloCarga.set(false);
  }
}
