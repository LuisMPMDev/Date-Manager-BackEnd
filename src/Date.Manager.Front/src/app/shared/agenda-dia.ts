import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { AgendaRepo } from '../core/agenda-repo';
import { AhoraService } from '../core/ahora';
import { Cita, Cliente, Servicio } from '../core/models';
import { construirAgendaDia } from './agenda-dia-columnas';
import { aFechaISO } from './formato';
import { ReglaTiempoComponent } from './regla-tiempo';

/**
 * Agenda de un día: una columna por servicio activo, todas con el mismo rango de horas.
 * Se carga sola desde el repositorio y se recalcula cuando cambia la fecha o cualquier dato.
 */
@Component({
  selector: 'app-agenda-dia',
  imports: [ReglaTiempoComponent],
  templateUrl: './agenda-dia.html',
  styleUrl: './agenda-dia.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgendaDiaComponent {
  private readonly repo = inject(AgendaRepo);
  private readonly ahoraActual = inject(AhoraService).ahora;

  readonly fecha = input.required<string>();

  private readonly servicios = signal<Servicio[]>([]);
  private readonly citas = signal<Cita[]>([]);
  private readonly clientes = signal<Cliente[]>([]);
  protected readonly cargado = signal(false);

  private pedido = 0; // descarta respuestas que llegan tarde

  protected readonly agenda = computed(() =>
    construirAgendaDia({
      fecha: this.fecha(),
      servicios: this.servicios(),
      citas: this.citas(),
      clientes: this.clientes(),
      ahora: this.ahoraActual(),
    }),
  );

  protected readonly ahoraMin = computed(() => {
    const ahora = this.ahoraActual();
    return this.fecha() === aFechaISO(ahora) ? ahora.getHours() * 60 + ahora.getMinutes() : null;
  });

  constructor() {
    effect(() => {
      this.repo.cambios();
      void this.cargar();
    });
  }

  private async cargar(): Promise<void> {
    const numero = ++this.pedido;
    const [servicios, citas, clientes] = await Promise.all([
      this.repo.servicios(),
      this.repo.citas(),
      this.repo.clientes(),
    ]);
    if (numero !== this.pedido) return;
    this.servicios.set(servicios);
    this.citas.set(citas);
    this.clientes.set(clientes);
    this.cargado.set(true);
  }
}
