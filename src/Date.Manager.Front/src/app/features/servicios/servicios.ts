import { afterNextRender, ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, Injector, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { DiaSemana, Servicio, VentanaDisponibilidad } from '../../core/models';
import { validarServicio } from '../../core/validaciones';
import { errorDeFormulario, esConflicto, esSinPermiso, mensajeAccion } from '../../shared/errores';
import { moneda } from '../../shared/formato';
import {
  BorradorServicio, borradorDe, borradorVacio, OPCIONES_DIA, resumenVentanas, servicioDe, ventanaNueva,
} from './servicios-borrador';

type CampoTexto = 'nombre' | 'duracion' | 'valor';
type CampoVentana = 'inicio' | 'fin';
/** Claves de `campos` que el formulario muestra junto a su campo (la API llama `duracionMin` a la duración). */
const CAMPOS = ['nombre', 'duracionMin', 'valor'];

@Component({
  selector: 'app-servicios',
  templateUrl: './servicios.html',
  styleUrl: './servicios.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServiciosComponent {
  private readonly repo = inject(AgendaRepo);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);

  protected readonly moneda = moneda;
  protected readonly resumenVentanas = resumenVentanas;
  protected readonly dias = OPCIONES_DIA;

  protected readonly servicios = signal<Servicio[]>([]);
  protected readonly borrador = signal<BorradorServicio | null>(null);
  protected readonly error = signal('');
  /** Errores por campo que mandó la API (claves ya normalizadas). */
  protected readonly errores = signal<Record<string, string>>({});
  /** Error de la lista (la carga o activar/desactivar), fuera del formulario. */
  protected readonly errorLista = signal('');
  /** Hay una escritura en curso (guardar o activar/desactivar): bloquea los botones que escriben. */
  protected readonly guardando = signal(false);

  protected readonly titulo = computed(() =>
    this.servicios().some(s => s.id === this.borrador()?.id) ? 'Editar servicio' : 'Nuevo servicio',
  );

  private abiertoDesde: HTMLElement | null = null;
  private pedido = 0; // descarta respuestas que llegan tarde

  constructor() {
    effect(() => {
      this.repo.cambios();
      void this.cargar();
    });
  }

  protected nuevo(evento: Event): void {
    this.abrir(borradorVacio(), evento);
  }

  protected editar(s: Servicio, evento: Event): void {
    this.abrir(borradorDe(s), evento);
  }

  protected async alternarActivo(s: Servicio): Promise<void> {
    if (this.guardando()) return;
    this.guardando.set(true); // antes del primer await
    this.errorLista.set('');
    try {
      await this.repo.guardarServicio({ ...s, activo: !s.activo });
    } catch (e) {
      this.errorLista.set(mensajeAccion(e, 'No pudimos cambiar el estado del servicio. Intenta de nuevo.'));
      if (esConflicto(e)) void this.cargar();
    } finally {
      this.guardando.set(false);
    }
  }

  protected cancelar(): void {
    this.cerrar();
  }

  protected escribir(campo: CampoTexto, evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.cambiar(b => ({ ...b, [campo]: valor }));
  }

  protected marcarActivo(evento: Event): void {
    const activo = (evento.target as HTMLInputElement).checked;
    this.cambiar(b => ({ ...b, activo }));
  }

  protected agregarVentana(): void {
    this.cambiar(b => ({ ...b, ventanas: [...b.ventanas, ventanaNueva()] }));
  }

  protected quitarVentana(indice: number): void {
    this.cambiar(b => ({ ...b, ventanas: b.ventanas.filter((_, i) => i !== indice) }));
  }

  protected elegirDia(indice: number, evento: Event): void {
    const dia = Number((evento.target as HTMLSelectElement).value) as DiaSemana;
    this.cambiarVentana(indice, v => ({ ...v, dia }));
  }

  protected escribirHora(indice: number, campo: CampoVentana, evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.cambiarVentana(indice, v => ({ ...v, [campo]: valor }));
  }

  protected async guardar(evento?: Event): Promise<void> {
    evento?.preventDefault();
    const borrador = this.borrador();
    if (!borrador || this.guardando()) return;
    const servicio = servicioDe(borrador);
    const mensaje = validarServicio(servicio);
    if (mensaje) {
      this.error.set(mensaje);
      return;
    }
    this.guardando.set(true); // antes del primer await: un segundo clic ya no pasa
    this.error.set('');
    this.errores.set({});
    try {
      await this.repo.guardarServicio(servicio);
      this.cerrar();
    } catch (e) {
      const { campos, general } = errorDeFormulario(e, CAMPOS, 'No pudimos guardar el servicio. Intenta de nuevo.');
      this.errores.set(campos);
      this.error.set(general);
      if (esConflicto(e)) void this.cargar(); // lo que se ve quedó viejo
    } finally {
      this.guardando.set(false);
    }
  }

  private abrir(borrador: BorradorServicio, evento: Event): void {
    this.abiertoDesde = evento.currentTarget as HTMLElement;
    this.error.set('');
    this.errores.set({});
    this.errorLista.set('');
    this.borrador.set(borrador);
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('form h3')?.focus(), {
      injector: this.injector,
    });
  }

  /** Cierra el formulario y devuelve el foco al botón que lo abrió (o al de «Nuevo servicio»). */
  private cerrar(): void {
    const origen = this.abiertoDesde;
    this.abiertoDesde = null;
    this.borrador.set(null);
    this.error.set('');
    this.errores.set({});
    afterNextRender(
      () => {
        const visible = origen?.isConnected ? origen : this.host.nativeElement.querySelector<HTMLElement>('[data-nuevo]');
        visible?.focus();
      },
      { injector: this.injector },
    );
  }

  private cambiar(cambio: (b: BorradorServicio) => BorradorServicio): void {
    this.borrador.update(b => (b ? cambio(b) : b));
  }

  private cambiarVentana(indice: number, cambio: (v: VentanaDisponibilidad) => VentanaDisponibilidad): void {
    this.cambiar(b => ({ ...b, ventanas: b.ventanas.map((v, i) => (i === indice ? cambio(v) : v)) }));
  }

  private async cargar(): Promise<void> {
    const numero = ++this.pedido;
    try {
      const servicios = await this.repo.servicios();
      if (numero !== this.pedido) return;
      this.servicios.set(servicios);
      this.errorLista.set('');
    } catch (e) {
      if (numero !== this.pedido) return;
      if (esSinPermiso(e)) void this.router.navigateByUrl('/sin-acceso');
      else this.errorLista.set('No se pudieron cargar los servicios.');
    }
  }
}
