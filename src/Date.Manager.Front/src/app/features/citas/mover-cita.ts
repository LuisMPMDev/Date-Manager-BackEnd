import { afterNextRender, ChangeDetectionStrategy, Component, computed, ElementRef, inject, Injector, input, OnInit, output, signal } from '@angular/core';
import { AgendaRepo } from '../../core/agenda-repo';
import { HorarioNoDisponibleError, RELOJ } from '../../core/errores';
import { aHHmm, isoDia, MAX_DIAS_ANTICIPACION, sumarMinutos } from '../../core/horarios';
import { Cita, Servicio } from '../../core/models';
import { aFechaISO, fechaLarga, sumarDias } from '../../shared/formato';
import { construirRegla } from '../../shared/regla-bloques';
import { ReglaTiempoComponent } from '../../shared/regla-tiempo';

/**
 * Diálogo en línea para mover una cita a otro horario libre del mismo servicio.
 * La cita puede volver a elegir su propio horario (los libres excluyen la cita misma).
 */
@Component({
  selector: 'app-mover-cita',
  imports: [ReglaTiempoComponent],
  templateUrl: './mover-cita.html',
  styleUrl: './mover-cita.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MoverCitaComponent implements OnInit {
  private readonly repo = inject(AgendaRepo);
  private readonly reloj = inject(RELOJ);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly cita = input.required<Cita>();
  readonly servicio = input.required<Servicio>();
  readonly cliente = input('');
  readonly movida = output<Cita>();
  readonly cerrar = output<void>();

  protected readonly fechaLarga = fechaLarga;
  protected readonly hoy = aFechaISO(this.reloj());
  protected readonly ultimoDia = sumarDias(this.hoy, MAX_DIAS_ANTICIPACION);

  protected readonly fecha = signal(this.hoy);
  protected readonly libres = signal<string[]>([]);
  protected readonly elegido = signal<string | null>(null);
  protected readonly error = signal('');
  protected readonly moviendo = signal(false);

  private pedido = 0; // descarta respuestas de horarios que llegan tarde

  protected readonly regla = computed(() => {
    const servicio = this.servicio();
    const dia = isoDia(this.fecha());
    return construirRegla({
      ventanas: servicio.ventanas.filter(v => v.dia === dia),
      duracionMin: servicio.duracionMin,
      libres: this.libres(),
      elegido: this.elegido(),
    });
  });

  protected readonly ahoraMin = computed(() => {
    if (this.fecha() !== this.hoy) return null;
    const ahora = this.reloj();
    return ahora.getHours() * 60 + ahora.getMinutes();
  });

  protected readonly horaFin = computed(() => {
    const inicio = this.elegido();
    return inicio ? sumarMinutos(inicio, this.servicio().duracionMin) : '';
  });

  async ngOnInit(): Promise<void> {
    const propia = this.cita().fecha;
    this.fecha.set(propia < this.hoy ? this.hoy : propia > this.ultimoDia ? this.ultimoDia : propia);
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('h3')?.focus(), { injector: this.injector });
    await this.cargarLibres();
  }

  protected async cambiarDia(delta: number): Promise<void> {
    const nueva = sumarDias(this.fecha(), delta);
    if (nueva < this.hoy || nueva > this.ultimoDia) return;
    this.fecha.set(nueva);
    this.libres.set([]);
    this.elegido.set(null);
    this.error.set('');
    await this.cargarLibres();
  }

  protected elegirHora(minutos: number): void {
    this.elegido.set(aHHmm(minutos));
    this.error.set('');
  }

  protected async mover(): Promise<void> {
    const inicio = this.elegido();
    if (!inicio || this.moviendo()) return;
    this.moviendo.set(true);
    try {
      this.movida.emit(await this.repo.moverCita(this.cita().id, this.fecha(), inicio));
    } catch (e) {
      this.error.set(
        e instanceof HorarioNoDisponibleError ? e.message : 'No pudimos mover la cita. Intenta de nuevo.',
      );
      this.elegido.set(null);
      await this.cargarLibres();
    } finally {
      this.moviendo.set(false);
    }
  }

  private async cargarLibres(): Promise<void> {
    const numero = ++this.pedido;
    const libres = await this.repo.horariosLibres(this.servicio().id, this.fecha(), this.cita().id);
    if (numero === this.pedido) this.libres.set(libres);
  }
}
