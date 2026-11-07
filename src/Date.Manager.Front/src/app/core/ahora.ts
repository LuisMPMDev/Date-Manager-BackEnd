import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { RELOJ } from './errores';

export const INTERVALO_AHORA_MS = 60_000;

/**
 * La hora actual como señal que se renueva cada minuto, para que las pantallas abiertas
 * muevan la línea roja de «ahora» y pasen a gris lo que ya terminó sin recargar.
 */
@Injectable({ providedIn: 'root' })
export class AhoraService {
  private readonly reloj = inject(RELOJ);
  private readonly actual = signal(this.reloj());

  readonly ahora = this.actual.asReadonly();

  /** Vuelve a leer el reloj sin esperar al siguiente minuto (por ejemplo, al recargar datos). */
  actualizar(): void {
    this.actual.set(this.reloj());
  }

  constructor() {
    const temporizador = setInterval(() => this.actualizar(), INTERVALO_AHORA_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(temporizador));
  }
}
