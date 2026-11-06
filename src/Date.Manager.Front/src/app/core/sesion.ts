import { computed, inject, Injectable, signal } from '@angular/core';
import { AgendaRepo } from './agenda-repo';
import { Permiso, Rol, Usuario } from './models';
import { puede } from './permisos';

const CLAVE_SESION = 'datemanager.sesion.v1';

function leerGuardado(): Usuario | null {
  try {
    const crudo = sessionStorage.getItem(CLAVE_SESION);
    return crudo ? (JSON.parse(crudo) as Usuario) : null;
  } catch {
    return null;
  }
}

function escribirGuardado(usuario: Usuario | null): void {
  try {
    if (usuario) sessionStorage.setItem(CLAVE_SESION, JSON.stringify(usuario));
    else sessionStorage.removeItem(CLAVE_SESION);
  } catch {
    // sin almacenamiento disponible: la sesión vive solo en memoria
  }
}

@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly repo = inject(AgendaRepo);
  private readonly roles = signal<Rol[]>([]);

  readonly usuario = signal<Usuario | null>(null);
  readonly rol = computed(() => this.roles().find(r => r.id === this.usuario()?.rolId));

  /** Se resuelve cuando la sesión guardada en la pestaña ya se restauró (o no había ninguna). */
  readonly listo: Promise<void>;

  constructor() {
    const guardado = leerGuardado();
    if (!guardado) {
      this.listo = Promise.resolve();
      return;
    }
    this.usuario.set(guardado);
    this.listo = this.repo
      .roles()
      .then(roles => {
        // Si el usuario ya inició sesión de nuevo mientras tanto, esa sesión manda.
        if (this.usuario()?.id === guardado.id) this.roles.set(roles);
      })
      .catch(() => this.cerrar());
  }

  iniciar(u: Usuario, roles: Rol[]): void {
    this.roles.set(roles);
    this.usuario.set(u);
    escribirGuardado(u);
  }

  cerrar(): void {
    this.usuario.set(null);
    this.roles.set([]);
    escribirGuardado(null);
  }

  puede(p: Permiso): boolean {
    return puede(this.rol(), p);
  }
}
