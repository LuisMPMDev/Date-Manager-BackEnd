import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { aHHmm } from '../core/horarios';

export interface BloqueRegla { desdeMin: number; hastaMin: number; tipo: 'ocupado' | 'elegido' | 'cita' | 'atendida' | 'fuera'; etiqueta?: string }

/** Horario libre que se puede pulsar sobre la regla (solo lo usa la página pública). */
export interface HuecoRegla { desdeMin: number; hastaMin: number; etiqueta: string }

/** Escala fija de la regla: 1 minuto = PX_POR_MIN píxeles, igual para todos los bloques. */
export const PX_POR_MIN = 1.2;

/**
 * Regla vertical de horas: una marca por hora desde `desdeMin` y bloques posicionados y medidos en minutos.
 * - `bloques` no son interactivos: 'ocupado' y 'fuera' van rayados; 'elegido' en cobalto; 'atendida' en gris.
 * - `huecos` (opcional) son botones pulsables; `(elegir)` emite el `desdeMin` del hueco pulsado.
 * - `ahoraMin` (opcional) dibuja la línea roja de «ahora» si cae dentro del rango.
 */
@Component({
  selector: 'app-regla-tiempo',
  templateUrl: './regla-tiempo.html',
  styleUrl: './regla-tiempo.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReglaTiempoComponent {
  readonly desdeMin = input.required<number>();
  readonly hastaMin = input.required<number>();
  readonly bloques = input<BloqueRegla[]>([]);
  readonly huecos = input<HuecoRegla[]>([]);
  readonly ahoraMin = input<number | null>(null);
  readonly elegir = output<number>();

  protected readonly px = PX_POR_MIN;
  protected readonly alto = computed(() => (this.hastaMin() - this.desdeMin()) * PX_POR_MIN);
  protected readonly marcas = computed(() => {
    const marcas: number[] = [];
    for (let m = this.desdeMin(); m <= this.hastaMin(); m += 60) marcas.push(m);
    return marcas;
  });
  protected readonly lineaAhora = computed(() => {
    const ahora = this.ahoraMin();
    return ahora !== null && ahora >= this.desdeMin() && ahora <= this.hastaMin()
      ? (ahora - this.desdeMin()) * PX_POR_MIN
      : null;
  });

  protected hhmm(min: number): string {
    return aHHmm(min);
  }

  protected clases(b: BloqueRegla): string {
    return b.tipo === 'ocupado' || b.tipo === 'fuera' ? `bloque ${b.tipo} rayado` : `bloque ${b.tipo}`;
  }

  protected esElegido(h: HuecoRegla): boolean {
    return this.bloques().some(b => b.tipo === 'elegido' && b.desdeMin === h.desdeMin);
  }
}
