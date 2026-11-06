import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { Rol, Usuario } from '../../core/models';
import { primeraRuta } from '../../core/permisos';
import { SesionService } from '../../core/sesion';
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

  private async cargar(): Promise<void> {
    const numero = ++this.pedido;
    try {
      const [usuarios, roles] = await Promise.all([this.repo.usuarios(), this.repo.roles()]);
      if (numero !== this.pedido) return;
      this.usuarios.set(usuarios);
      this.roles.set(roles);
      this.cargado.set(true);
      this.falloCarga.set(false);
    } catch {
      if (numero === this.pedido) this.falloCarga.set(true);
    }
  }
}
