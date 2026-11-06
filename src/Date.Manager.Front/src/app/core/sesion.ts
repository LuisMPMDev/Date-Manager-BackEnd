import { computed, inject, Injectable, signal } from '@angular/core';
import { AgendaRepo } from './agenda-repo';
import { Permiso, Rol, Usuario } from './models';
import { puede } from './permisos';

const CLAVE_SESION = 'datemanager.sesion.v1';

function tieneForma(valor: unknown): valor is Usuario {
  if (typeof valor !== 'object' || valor === null || Array.isArray(valor)) return false;
  const u = valor as Record<string, unknown>;
  return typeof u['id'] === 'string' && typeof u['rolId'] === 'string';
}

/** Devuelve la sesión guardada si tiene la forma esperada; si no, la descarta. */
function leerGuardado(): Usuario | null {
  try {
    const crudo = sessionStorage.getItem(CLAVE_SESION);
    if (!crudo) return null;
    const valor: unknown = JSON.parse(crudo);
    if (tieneForma(valor)) return valor;
    sessionStorage.removeItem(CLAVE_SESION);
  } catch {
    try {
      sessionStorage.removeItem(CLAVE_SESION);
    } catch {
      // sin almacenamiento disponible
    }
  }
  return null;
}

function escribirGuardado(usuario: Usuario | null): void {
  try {
    // la clave nunca se guarda en el navegador
    if (usuario) sessionStorage.setItem(CLAVE_SESION, JSON.stringify({ ...usuario, clave: '' }));
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

  /**
   * Vuelve a leer roles y usuarios: si el rol o el usuario de la sesión cambiaron,
   * el menú y los permisos se actualizan al instante. Si el usuario ya no existe, cierra la sesión.
   */
  async refrescar(): Promise<void> {
    const actual = this.usuario();
    if (!actual) return;
    const [roles, usuarios] = await Promise.all([this.repo.roles(), this.repo.usuarios()]);
    if (this.usuario()?.id !== actual.id) return; // la sesión cambió mientras esperábamos
    const vigente = usuarios.find(u => u.id === actual.id);
    if (!vigente) {
      this.cerrar();
      return;
    }
    this.roles.set(roles);
    this.usuario.set(vigente);
    escribirGuardado(vigente);
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
