import { Injectable } from '@angular/core';

/**
 * Aviso de «la sesión venció» que los interceptores disparan al recibir un 401 en una ruta con Bearer.
 * Avisa una sola vez por ráfaga de 401 (varias peticiones en vuelo fallan juntas); se rearma al
 * guardar un token nuevo. La sesión se suscribe con alExpirar.
 */
@Injectable({ providedIn: 'root' })
export class SesionExpirada {
  private readonly oyentes = new Set<() => void>();
  private avisada = false;

  /** Registra un oyente; devuelve la función que lo da de baja. */
  alExpirar(oyente: () => void): () => void {
    this.oyentes.add(oyente);
    return () => this.oyentes.delete(oyente);
  }

  emitir(): void {
    if (this.avisada) return;
    this.avisada = true;
    for (const oyente of [...this.oyentes]) oyente();
  }

  rearmar(): void {
    this.avisada = false;
  }
}
