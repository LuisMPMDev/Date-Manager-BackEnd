import { afterNextRender, ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, Injector, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { Cita, Cliente } from '../../core/models';
import { AVISO_SIN_PERMISO, errorDeFormulario, esConflicto, esSinPermiso } from '../../shared/errores';
import { contarCitas, filtrarClientes, validarCliente } from './clientes-logica';

type Campo = 'nombre' | 'apellido' | 'telefono' | 'correo';
const CAMPOS: Campo[] = ['nombre', 'apellido', 'telefono', 'correo'];

@Component({
  selector: 'app-clientes',
  templateUrl: './clientes.html',
  styleUrl: './clientes.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientesComponent {
  private readonly repo = inject(AgendaRepo);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);

  private readonly clientes = signal<Cliente[]>([]);
  private readonly citas = signal<Cita[]>([]);
  private pedido = 0; // descarta respuestas que llegan tarde

  protected readonly consulta = signal('');
  /** Copia del cliente que se está editando: la tabla no cambia hasta guardar. */
  protected readonly borrador = signal<Cliente | null>(null);
  private origenId: string | null = null;
  protected readonly guardando = signal(false);
  protected readonly aviso = signal('');
  protected readonly error = signal('');
  /** Errores por campo que mandó la API (claves ya normalizadas). */
  protected readonly errores = signal<Record<string, string>>({});
  /** Por qué no se ven las citas de cada cliente (sin permiso o falló la carga); vacío si se leyeron bien. */
  protected readonly avisoCitas = signal('');

  protected readonly filas = computed(() => {
    const cuentas = contarCitas(this.citas());
    const sinCitas = this.avisoCitas() !== '';
    return filtrarClientes(this.clientes(), this.consulta())
      .map(cliente => ({ cliente, citas: sinCitas ? null : (cuentas.get(cliente.id) ?? 0) }));
  });

  protected readonly hayClientes = computed(() => this.clientes().length > 0);

  constructor() {
    effect(() => {
      this.repo.cambios();
      void this.cargar();
    });
  }

  protected buscar(evento: Event): void {
    this.consulta.set((evento.target as HTMLInputElement).value);
  }

  protected editar(cliente: Cliente): void {
    this.origenId = cliente.id;
    this.aviso.set('');
    this.error.set('');
    this.errores.set({});
    this.borrador.set({ ...cliente });
    this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>('form h3'));
  }

  protected escribir(campo: Campo, evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.borrador.update(b => (b ? { ...b, [campo]: valor } : b));
  }

  protected cancelar(): void {
    this.error.set('');
    this.errores.set({});
    this.cerrar();
  }

  protected async guardar(evento?: Event): Promise<void> {
    evento?.preventDefault();
    const borrador = this.borrador();
    if (!borrador || this.guardando()) return;
    const cliente: Cliente = {
      id: borrador.id,
      nombre: borrador.nombre.trim(),
      apellido: borrador.apellido.trim(),
      telefono: borrador.telefono.trim(),
      correo: borrador.correo.trim(),
    };
    const mensaje = validarCliente(cliente, this.clientes());
    if (mensaje) {
      this.error.set(mensaje);
      return;
    }
    this.guardando.set(true); // antes del primer await: un segundo clic ya no pasa
    this.error.set('');
    this.errores.set({});
    try {
      await this.repo.guardarCliente(cliente);
      this.aviso.set('Cliente guardado.');
      this.cerrar();
    } catch (e) {
      const { campos, general } = errorDeFormulario(e, CAMPOS, 'No pudimos guardar el cliente. Intenta de nuevo.');
      this.errores.set(campos);
      this.error.set(general);
      if (esConflicto(e)) void this.cargar(); // lo que se ve quedó viejo
    } finally {
      this.guardando.set(false);
    }
  }

  /** Cierra el formulario y devuelve el foco al botón «Editar» de esa fila. */
  private cerrar(): void {
    const id = this.origenId;
    this.origenId = null;
    this.borrador.set(null);
    this.enfocar(
      () => this.host.nativeElement.querySelector<HTMLElement>(`tr[data-cliente="${id}"] button`) ?? this.host.nativeElement.querySelector<HTMLElement>('h2'),
    );
  }

  private enfocar(buscar: () => HTMLElement | null): void {
    afterNextRender(() => buscar()?.focus(), { injector: this.injector });
  }

  /** Clientes es la llamada principal; las citas son auxiliares (solo cuentan las citas de cada cliente). */
  private async cargar(): Promise<void> {
    const numero = ++this.pedido;
    const [clientes, citas] = await Promise.allSettled([this.repo.clientes(), this.repo.citas()]);
    if (numero !== this.pedido) return;
    if (clientes.status === 'fulfilled') {
      this.clientes.set(clientes.value);
    } else if (esSinPermiso(clientes.reason)) {
      void this.router.navigateByUrl('/sin-acceso');
      return;
    } else {
      this.error.set('No se pudieron cargar los clientes.');
    }
    if (citas.status === 'fulfilled') {
      this.citas.set(citas.value);
      this.avisoCitas.set('');
    } else {
      this.citas.set([]);
      this.avisoCitas.set(esSinPermiso(citas.reason) ? AVISO_SIN_PERMISO : 'No se pudo cargar el historial de citas.');
    }
  }
}
