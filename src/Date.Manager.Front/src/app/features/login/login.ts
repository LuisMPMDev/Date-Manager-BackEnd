import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { primeraRuta } from '../../core/permisos';
import { SesionService } from '../../core/sesion';

interface CuentaEjemplo { etiqueta: string; usuario: string; clave: string }

@Component({
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly repo = inject(AgendaRepo);
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);

  protected readonly usuario = signal('');
  protected readonly clave = signal('');
  protected readonly error = signal('');
  protected readonly ocupado = signal(false);

  protected readonly cuentas: CuentaEjemplo[] = [
    { etiqueta: 'Marta · Administrador', usuario: 'marta', clave: 'demo123' },
    { etiqueta: 'Andrés · Recepción', usuario: 'andres', clave: 'demo123' },
  ];

  protected escribir(campo: 'usuario' | 'clave', evento: Event): void {
    (campo === 'usuario' ? this.usuario : this.clave).set((evento.target as HTMLInputElement).value);
  }

  protected async enviar(evento?: Event): Promise<void> {
    evento?.preventDefault();
    await this.entrar(this.usuario().trim(), this.clave());
  }

  protected async entrarCon(cuenta: CuentaEjemplo): Promise<void> {
    await this.entrar(cuenta.usuario, cuenta.clave);
  }

  private async entrar(usuario: string, clave: string): Promise<void> {
    this.ocupado.set(true);
    this.error.set('');
    try {
      const encontrado = await this.repo.iniciarSesion(usuario, clave);
      if (!encontrado) {
        this.error.set('Usuario o clave incorrectos.');
        return;
      }
      const roles = await this.repo.roles();
      this.sesion.iniciar(encontrado, roles);
      await this.router.navigateByUrl(primeraRuta(this.sesion.rol()));
    } finally {
      this.ocupado.set(false);
    }
  }
}
