import { afterNextRender, ChangeDetectionStrategy, Component, computed, ElementRef, inject, Injector, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AgendaRepo } from '../core/agenda-repo';
import { MENU, primeraRuta } from '../core/permisos';
import { SesionService } from '../core/sesion';

@Component({
  selector: 'app-panel-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './panel-shell.html',
  styleUrl: './panel-shell.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelShell {
  private readonly repo = inject(AgendaRepo);
  private readonly router = inject(Router);
  protected readonly sesion = inject(SesionService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** «Reiniciar demo» borra los cambios: antes se pide confirmación. */
  protected readonly confirmandoReinicio = signal(false);

  protected readonly menu = computed(() =>
    MENU.filter(m => m.permisos.some(p => this.sesion.puede(p))),
  );

  protected async cerrarSesion(): Promise<void> {
    this.sesion.cerrar();
    await this.router.navigateByUrl('/login');
  }

  protected pedirReinicio(): void {
    this.confirmandoReinicio.set(true);
    this.enfocar('[data-volver]');
  }

  protected cancelarReinicio(): void {
    this.confirmandoReinicio.set(false);
    this.enfocar('[data-reiniciar]');
  }

  private enfocar(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }

  /** Vuelve a los datos de ejemplo; conserva la sesión si el usuario sigue existiendo. */
  protected async reiniciarDemo(): Promise<void> {
    this.confirmandoReinicio.set(false);
    const actual = this.sesion.usuario();
    await this.repo.reiniciar();
    const [usuarios, roles] = await Promise.all([this.repo.usuarios(), this.repo.roles()]);
    const vigente = usuarios.find(u => u.id === actual?.id);
    if (!vigente) {
      await this.cerrarSesion();
      return;
    }
    this.sesion.iniciar(vigente, roles);
    await this.router.navigateByUrl(primeraRuta(this.sesion.rol()));
  }
}
