import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MENU } from '../core/permisos';
import { SesionService } from '../core/sesion';

@Component({
  selector: 'app-panel-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './panel-shell.html',
  styleUrl: './panel-shell.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelShell {
  private readonly router = inject(Router);
  protected readonly sesion = inject(SesionService);

  protected readonly menu = computed(() =>
    MENU.filter(m => m.permisos.some(p => this.sesion.puede(p))),
  );

  protected async cerrarSesion(): Promise<void> {
    this.sesion.cerrar();
    await this.router.navigateByUrl('/login');
  }
}
