import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiError } from './api-error';
import { AuthApi, SesionActual } from './auth-api';
import { Permiso, Rol, Usuario } from './models';
import { puede } from './permisos';
import { SesionExpirada } from './sesion-expirada';
import { TokenStore } from './token-store';

const SESION_VENCIDA = 'Tu sesión venció. Inicia sesión de nuevo.';
const USUARIO_AUSENTE = 'Tu usuario ya no existe.';

/**
 * Sesión del negocio: quién es el usuario y qué rol (y permisos) tiene. Lo único que sobrevive a una
 * recarga es el token (TokenStore); al cargar se restaura preguntando a la API con GET /auth/yo.
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly auth = inject(AuthApi);
  private readonly tokens = inject(TokenStore);
  private readonly router = inject(Router);

  private readonly _usuario = signal<Usuario | null>(null);
  private readonly _rol = signal<Rol | undefined>(undefined);
  private readonly _aviso = signal<string | null>(null);

  readonly usuario = this._usuario.asReadonly();
  readonly rol = this._rol.asReadonly();
  /** Motivo de un cierre forzado (sesión vencida, usuario borrado); lo muestra la pantalla de login. */
  readonly aviso = this._aviso.asReadonly();

  /** Se resuelve cuando la sesión guardada en la pestaña ya se restauró (o no había ninguna). */
  readonly listo: Promise<void>;

  /** Mientras se refresca, un 401 significa que el usuario de un token aún válido ya no existe. */
  private refrescando = false;

  constructor() {
    // Se suscribe al construirse: un 401 antes de este punto se perdería.
    const baja = inject(SesionExpirada).alExpirar(() => this.vencer(this.refrescando ? USUARIO_AUSENTE : SESION_VENCIDA));
    inject(DestroyRef).onDestroy(baja);
    this.listo = this.tokens.leer() ? this.restaurar() : Promise.resolve();
  }

  iniciar(usuario: Usuario, rol: Rol): void {
    this._usuario.set(usuario);
    this._rol.set(rol);
    this._aviso.set(null);
  }

  /**
   * Vuelve a leer usuario, rol y permisos vigentes (GET /auth/yo): si cambiaron, el menú y los permisos
   * se actualizan al instante. Si la API ya no reconoce al usuario, cierra la sesión.
   */
  async refrescar(): Promise<void> {
    const actual = this.usuario();
    if (!actual) return;
    this.refrescando = true;
    try {
      const vigente = await this.auth.yo();
      if (this.usuario()?.id === actual.id) this.fijar(vigente); // si la sesión cambió mientras esperábamos, no manda
    } catch (e) {
      if (e instanceof ApiError && e.status === 401 && this.usuario()?.id === actual.id) this.vencer(USUARIO_AUSENTE);
      throw e;
    } finally {
      this.refrescando = false;
    }
  }

  /** Cierre voluntario: borra el token y la sesión, sin aviso. */
  cerrar(): void {
    this.vaciar();
    this._aviso.set(null);
  }

  puede(p: Permiso): boolean {
    return puede(this.rol(), p);
  }

  private async restaurar(): Promise<void> {
    try {
      const vigente = await this.auth.yo();
      if (!this.usuario()) this.fijar(vigente); // si el usuario ya inició sesión de nuevo, esa sesión manda
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        if (!this._aviso()) this.vencer(SESION_VENCIDA); // normalmente el interceptor ya avisó
      } else if (!this.usuario()) {
        // red caída o error del servidor: sin sesión por ahora, pero el token se conserva para reintentar
        this._aviso.set(e instanceof ApiError ? e.message : null);
      }
    }
  }

  private fijar(s: SesionActual): void {
    this._usuario.set(s.usuario);
    this._rol.set(s.rol);
  }

  private vaciar(): void {
    this.tokens.borrar();
    this._usuario.set(null);
    this._rol.set(undefined);
  }

  /** Cierre forzado: limpia, explica el motivo y manda a /login (una sola vez por vencimiento). */
  private vencer(motivo: string): void {
    this.vaciar();
    this._aviso.set(motivo);
    void this.router.navigateByUrl('/login');
  }
}
