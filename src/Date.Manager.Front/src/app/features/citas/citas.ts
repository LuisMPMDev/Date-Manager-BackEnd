import { afterNextRender, ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, Injector, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { AhoraService } from '../../core/ahora';
import { RELOJ } from '../../core/reloj';
import { aHHmm } from '../../core/horarios';
import { Cita, Cliente, Servicio } from '../../core/models';
import { SesionService } from '../../core/sesion';
import { esConflicto, esSinPermiso, mensajeAccion } from '../../shared/errores';
import { aFechaISO, fechaLarga } from '../../shared/formato';
import { ESTADOS, FilaCita, filtrarCitas, FiltrosCitas, nombreCompleto, SIN_FILTROS } from './citas-filtros';
import { MoverCitaComponent } from './mover-cita';

type CampoFiltro = 'fecha' | 'servicioId' | 'estado';

@Component({
  selector: 'app-citas',
  imports: [MoverCitaComponent],
  templateUrl: './citas.html',
  styleUrl: './citas.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CitasComponent {
  private readonly repo = inject(AgendaRepo);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  private readonly hoy = aFechaISO(inject(RELOJ)());
  private readonly ahora = inject(AhoraService).ahora;
  protected readonly sesion = inject(SesionService);

  protected readonly fechaLarga = fechaLarga;
  protected readonly nombreCompleto = nombreCompleto;
  protected readonly estados = ESTADOS;

  private readonly citas = signal<Cita[]>([]);
  protected readonly servicios = signal<Servicio[]>([]);
  private readonly clientes = signal<Cliente[]>([]);

  protected readonly filtros = signal<FiltrosCitas>(SIN_FILTROS);
  protected readonly confirmando = signal<string | null>(null); // cita que pide confirmar su cancelación
  protected readonly moviendoId = signal<string | null>(null);
  protected readonly aviso = signal('');
  protected readonly error = signal('');
  /** Hay una escritura en curso (cancelar o atender): bloquea los botones de acción. */
  protected readonly ocupada = signal(false);
  private pedido = 0; // descarta respuestas que llegan tarde

  protected readonly mostrarAcciones = computed(
    () => this.sesion.puede('gestionar_citas') || this.sesion.puede('atender_citas'),
  );

  protected readonly filas = computed<FilaCita[]>(() =>
    filtrarCitas(this.citas(), this.filtros(), this.hoy).map(cita => ({
      cita,
      servicio: this.servicios().find(s => s.id === cita.servicioId),
      cliente: this.clientes().find(c => c.id === cita.clienteId),
    })),
  );

  protected readonly citaMoviendo = computed(() => {
    const fila = this.filas().find(f => f.cita.id === this.moviendoId());
    return fila && fila.servicio ? { ...fila, servicio: fila.servicio } : null;
  });

  /** Lista de 0 o 1 diálogos: se rastrea por cita para que cada cita tenga su propio diálogo nuevo. */
  protected readonly dialogosMover = computed(() => {
    const m = this.citaMoviendo();
    return m ? [m] : [];
  });

  constructor() {
    effect(() => {
      this.repo.cambios();
      void this.cargar();
    });
  }

  protected filtrar(campo: CampoFiltro, evento: Event): void {
    const valor = (evento.target as HTMLInputElement | HTMLSelectElement).value;
    this.filtros.update(f => ({ ...f, [campo]: valor }));
  }

  protected alternarAnteriores(evento: Event): void {
    const anteriores = (evento.target as HTMLInputElement).checked;
    this.filtros.update(f => ({ ...f, anteriores }));
  }

  // --- Cancelar ------------------------------------------------------------

  protected pedirCancelacion(id: string): void {
    this.confirmando.set(id);
    this.enfocar(() => this.filaEl(id)?.querySelector<HTMLElement>('[data-volver]'));
  }

  protected volver(id: string): void {
    this.confirmando.set(null);
    this.devolverFoco(id, 'cancelar');
  }

  protected async cancelar(id: string): Promise<void> {
    await this.ejecutar(id, () => this.repo.cancelarCita(id), 'Cita cancelada.');
  }

  // --- Marcar como atendida ------------------------------------------------

  /** Solo se puede atender una cita que ya empezó (su inicio no está en el futuro). */
  protected yaEmpezo(cita: Cita): boolean {
    const ahora = this.ahora();
    const hoy = aFechaISO(ahora);
    return cita.fecha < hoy || (cita.fecha === hoy && cita.inicio <= aHHmm(ahora.getHours() * 60 + ahora.getMinutes()));
  }

  protected async atender(id: string): Promise<void> {
    await this.ejecutar(id, () => this.repo.marcarAtendida(id), 'Cita marcada como atendida.');
  }

  // --- Mover ---------------------------------------------------------------

  protected abrirMover(id: string): void {
    this.aviso.set('');
    this.error.set('');
    this.moviendoId.set(id);
  }

  protected cerrarMover(): void {
    const id = this.moviendoId();
    this.moviendoId.set(null);
    if (id) this.devolverFoco(id, 'mover');
  }

  /** El diálogo de mover vio un conflicto (409): esta lista también pudo quedar vieja. */
  protected recargar(): void {
    void this.cargar();
  }

  protected movida(cita: Cita): void {
    this.aviso.set(`Cita movida al ${fechaLarga(cita.fecha)}, ${cita.inicio}.`);
    this.cerrarMover();
  }

  // --- Ayudas --------------------------------------------------------------

  private async ejecutar(id: string, accion: () => Promise<Cita>, mensaje: string): Promise<void> {
    if (this.ocupada()) return;
    this.ocupada.set(true); // antes del primer await: un segundo clic ya no pasa
    this.error.set('');
    try {
      await accion();
      this.aviso.set(mensaje);
    } catch (e) {
      this.error.set(mensajeAccion(e, 'No pudimos completar la acción. Intenta de nuevo.'));
      if (esConflicto(e)) void this.cargar(); // la cita cambió desde otro lado: se muestra como está ahora
    } finally {
      this.ocupada.set(false);
    }
    this.confirmando.set(null);
    this.enfocar(() => this.filaEl(id) ?? this.titulo());
  }

  private filaEl(id: string): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>(`tr[data-cita="${id}"]`);
  }

  private titulo(): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>('h2');
  }

  /** Al cerrar un paso, el foco vuelve al botón que lo abrió; si la fila ya no se ve, al título. */
  private devolverFoco(id: string, accion: 'mover' | 'cancelar'): void {
    this.enfocar(
      () => this.filaEl(id)?.querySelector<HTMLElement>(`[data-accion="${accion}"]`) ?? this.filaEl(id) ?? this.titulo(),
    );
  }

  private enfocar(buscar: () => HTMLElement | null | undefined): void {
    afterNextRender(() => buscar()?.focus(), { injector: this.injector });
  }

  private async cargar(): Promise<void> {
    const numero = ++this.pedido;
    try {
      const [citas, servicios, clientes] = await Promise.all([
        this.repo.citas(),
        this.repo.servicios(),
        this.repo.clientes(),
      ]);
      if (numero !== this.pedido) return;
      this.citas.set(citas);
      this.servicios.set(servicios);
      this.clientes.set(clientes);
    } catch (e) {
      if (numero !== this.pedido) return;
      if (esSinPermiso(e)) void this.router.navigateByUrl('/sin-acceso');
      else this.error.set('No se pudieron cargar las citas.');
    }
  }
}
