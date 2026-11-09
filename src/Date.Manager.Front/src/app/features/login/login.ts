import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiError } from '../../core/api-error';
import { AuthApi } from '../../core/auth-api';
import { primeraRuta } from '../../core/permisos';
import { SesionService } from '../../core/sesion';

@Component({
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly auth = inject(AuthApi);
  private readonly router = inject(Router);
  protected readonly sesion = inject(SesionService);

  protected readonly usuario = signal('');
  protected readonly clave = signal('');
  protected readonly error = signal('');
  protected readonly ocupado = signal(false);

  protected escribir(campo: 'usuario' | 'clave', evento: Event): void {
    (campo === 'usuario' ? this.usuario : this.clave).set((evento.target as HTMLInputElement).value);
  }

  protected async enviar(evento?: Event): Promise<void> {
    evento?.preventDefault();
    if (this.ocupado()) return; // una sola petición a la vez
    this.ocupado.set(true);
    this.error.set('');
    try {
      const { usuario, rol } = await this.auth.login(this.usuario().trim(), this.clave());
      this.sesion.iniciar(usuario, rol);
      await this.router.navigateByUrl(primeraRuta(this.sesion.rol()));
    } catch (e) {
      this.error.set(e instanceof ApiError ? e.message : 'No pudimos iniciar sesión. Inténtalo de nuevo.');
    } finally {
      this.ocupado.set(false);
    }
  }
}
