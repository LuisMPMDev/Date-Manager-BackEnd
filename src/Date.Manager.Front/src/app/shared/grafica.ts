import {
  afterNextRender, ChangeDetectionStrategy, Component, DestroyRef, effect, ElementRef, inject, InjectionToken, input,
  viewChild,
} from '@angular/core';
import type { ChartConfiguration, ChartData } from 'chart.js';

/** Lo mínimo que esta pantalla necesita de un gráfico de Chart.js (y de su sustituto en las pruebas). */
export interface GraficaMotor {
  data: ChartData<'bar'>;
  update(): void;
  destroy(): void;
}

export type FabricaGrafica = (canvas: HTMLCanvasElement, config: ChartConfiguration<'bar'>) => GraficaMotor | Promise<GraficaMotor>;

/** Crea el gráfico real; Chart.js se descarga solo cuando hace falta la primera gráfica. */
export const FABRICA_GRAFICA = new InjectionToken<FabricaGrafica>('FABRICA_GRAFICA', {
  providedIn: 'root',
  factory: () => async (canvas, config) => {
    const { default: Chart } = await import('chart.js/auto');
    return new Chart(canvas, config);
  },
});

/**
 * Gráfica de barras (verticales u horizontales) sobre un `<canvas>`. El dibujo no es accesible por sí solo:
 * el `resumen` y una tabla con los valores van como texto oculto a la vista.
 */
@Component({
  selector: 'app-grafica',
  templateUrl: './grafica.html',
  styleUrl: './grafica.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraficaComponent {
  readonly tipo = input.required<'barras' | 'barras-h'>();
  readonly etiquetas = input.required<string[]>();
  readonly valores = input.required<number[]>();
  readonly resumen = input.required<string>();
  /** Se escribe tras cada valor en el texto alternativo y en el eje de valores (por ejemplo «%»). */
  readonly unidad = input('');
  /** Tope del eje de valores; sin él, Chart.js lo calcula. */
  readonly maximo = input<number | undefined>(undefined);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly fabrica = inject(FABRICA_GRAFICA);
  private readonly lienzo = viewChild.required<ElementRef<HTMLCanvasElement>>('lienzo');

  private grafica: GraficaMotor | null = null;
  private tipoDibujado: string | null = null;
  private generacion = 0; // invalida creaciones en curso cuando se vuelve a crear o se destruye
  private destruido = false;

  constructor() {
    afterNextRender(() => void this.crear());

    effect(() => {
      // Solo reacciona a los datos; el cambio de tipo vuelve a crear el gráfico.
      const tipo = this.tipo();
      this.etiquetas();
      this.valores();
      this.maximo();
      this.unidad();
      if (this.grafica || this.tipoDibujado !== null) {
        if (tipo !== this.tipoDibujado) void this.crear();
        else this.sincronizar();
      }
    });

    inject(DestroyRef).onDestroy(() => {
      this.destruido = true;
      this.generacion++;
      this.grafica?.destroy();
      this.grafica = null;
    });
  }

  private sincronizar(): void {
    const grafica = this.grafica;
    if (!grafica) return;
    grafica.data.labels = [...this.etiquetas()];
    grafica.data.datasets[0].data = [...this.valores()];
    grafica.update();
  }

  private color(variable: string): string | undefined {
    return getComputedStyle(this.host.nativeElement).getPropertyValue(variable).trim() || undefined;
  }

  private config(): ChartConfiguration<'bar'> {
    const horizontal = this.tipo() === 'barras-h';
    const unidad = this.unidad();
    const reducirMovimiento = typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const apoyo = this.color('--apoyo');
    const rayado = this.color('--rayado');

    const categorias = { grid: { display: false }, ticks: { color: apoyo, maxRotation: 0, autoSkip: true } };
    const valores = {
      beginAtZero: true,
      max: this.maximo(),
      grid: { color: rayado },
      ticks: { color: apoyo, precision: 0, callback: (v: string | number) => `${v}${unidad}` },
    };

    return {
      type: 'bar',
      data: {
        labels: [...this.etiquetas()],
        datasets: [{ data: [...this.valores()], backgroundColor: this.color('--cobalto'), borderWidth: 0 }],
      },
      options: {
        indexAxis: horizontal ? 'y' : 'x',
        responsive: true,
        maintainAspectRatio: false,
        ...(reducirMovimiento ? { animation: false as const } : {}),
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: ctx => `${ctx.parsed[horizontal ? 'x' : 'y']}${unidad}` } },
        },
        scales: horizontal ? { y: categorias, x: valores } : { x: categorias, y: valores },
      },
    };
  }

  private async crear(): Promise<void> {
    const numero = ++this.generacion;
    this.grafica?.destroy();
    this.grafica = null;
    this.tipoDibujado = this.tipo();
    const grafica = await this.fabrica(this.lienzo().nativeElement, this.config());
    if (numero !== this.generacion || this.destruido) {
      grafica.destroy();
      return;
    }
    this.grafica = grafica;
    this.sincronizar(); // por si los datos cambiaron mientras se creaba
  }
}
