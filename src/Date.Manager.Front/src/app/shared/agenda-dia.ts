import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgendaRepo } from '../core/agenda-repo';
import { AhoraService } from '../core/ahora';
import { Cita, Cliente, Servicio } from '../core/models';
import { construirAgendaDia } from './agenda-dia-columnas';
import { AVISO_SIN_PERMISO, esSinPermiso } from './errores';
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
  private readonly router = inject(Router);

  readonly fecha = input.required<string>();

  private readonly servicios = signal<Servicio[]>([]);
  private readonly citas = signal<Cita[]>([]);
  private readonly clientes = signal<Cliente[]>([]);
  protected readonly cargado = signal(false);
  protected readonly error = signal('');
  /** Por qué la agenda no trae nombres de cliente (sin permiso o falló la carga); vacío si se leyeron bien. */
  protected readonly avisoClientes = signal('');

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

  /** Servicios y citas son la llamada principal; los clientes son auxiliares (solo ponen el nombre en cada bloque). */
  private async cargar(): Promise<void> {
    const numero = ++this.pedido;
    const [servicios, citas, clientes] = await Promise.allSettled([
      this.repo.servicios(),
      this.repo.citas(),
      this.repo.clientes(),
    ]);
    if (numero !== this.pedido) return;
    if (servicios.status === 'rejected' || citas.status === 'rejected') {
      const causa = servicios.status === 'rejected' ? servicios.reason : (citas as PromiseRejectedResult).reason;
      if (esSinPermiso(causa)) void this.router.navigateByUrl('/sin-acceso');
      else this.error.set('No se pudo cargar la agenda.'); // lo que ya se veía se conserva
      return;
    }
    this.servicios.set(servicios.value);
    this.citas.set(citas.value);
    this.error.set('');
    if (clientes.status === 'fulfilled') {
      this.clientes.set(clientes.value);
      this.avisoClientes.set('');
    } else {
      this.clientes.set([]); // sin nombres: cada bloque dice «Cliente»
      this.avisoClientes.set(
        esSinPermiso(clientes.reason) ? AVISO_SIN_PERMISO : 'No se pudieron cargar los nombres de los clientes.',
      );
    }
    this.cargado.set(true);
  }
}
