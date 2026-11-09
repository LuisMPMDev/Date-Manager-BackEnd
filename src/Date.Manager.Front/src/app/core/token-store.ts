import { inject, Injectable } from '@angular/core';
import { SesionExpirada } from './sesion-expirada';

const CLAVE_TOKEN = 'datemanager.sesion.v2';

/** Guarda solo el token (nunca la clave) en sessionStorage: vive por pestaña. */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private readonly expirada = inject(SesionExpirada);

  leer(): string | null {
    try {
      const crudo = sessionStorage.getItem(CLAVE_TOKEN);
      if (!crudo) return null;
      const valor: unknown = JSON.parse(crudo);
      const token = typeof valor === 'object' && valor !== null ? (valor as Record<string, unknown>)['token'] : null;
      if (typeof token === 'string' && token) return token;
      sessionStorage.removeItem(CLAVE_TOKEN);
    } catch {
      this.borrar();
    }
    return null;
  }

  /** Un token nuevo es una sesión nueva: vuelve a permitir el aviso de sesión expirada. */
  guardar(token: string): void {
    this.expirada.rearmar();
    try {
      sessionStorage.setItem(CLAVE_TOKEN, JSON.stringify({ token }));
    } catch {
      // sin almacenamiento disponible: la sesión no sobrevive a una recarga
    }
  }

  borrar(): void {
    try {
      sessionStorage.removeItem(CLAVE_TOKEN);
    } catch {
      // sin almacenamiento disponible
    }
  }
}
