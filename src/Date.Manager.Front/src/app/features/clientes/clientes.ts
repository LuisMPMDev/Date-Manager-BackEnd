import { afterNextRender, ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, Injector, signal } from '@angular/core';
import { AgendaRepo } from '../../core/agenda-repo';
import { Cita, Cliente } from '../../core/models';
import { contarCitas, filtrarClientes, validarCliente } from './clientes-logica';

type Campo = 'nombre' | 'apellido' | 'telefono' | 'correo';

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

  protected readonly filas = computed(() => {
    const cuentas = contarCitas(this.citas());
    return filtrarClientes(this.clientes(), this.consulta()).map(cliente => ({ cliente, citas: cuentas.get(cliente.id) ?? 0 }));
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
    this.borrador.set({ ...cliente });
    this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>('form h3'));
  }

  protected escribir(campo: Campo, evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.borrador.update(b => (b ? { ...b, [campo]: valor } : b));
  }

  protected cancelar(): void {
    this.error.set('');
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
    this.guardando.set(true);
    this.error.set('');
    try {
      await this.repo.guardarCliente(cliente);
      this.aviso.set('Cliente guardado.');
      this.cerrar();
    } catch {
      this.error.set('No pudimos guardar el cliente. Intenta de nuevo.');
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

  private async cargar(): Promise<void> {
    const numero = ++this.pedido;
    try {
      const [clientes, citas] = await Promise.all([this.repo.clientes(), this.repo.citas()]);
      if (numero !== this.pedido) return;
      this.clientes.set(clientes);
      this.citas.set(citas);
    } catch {
      if (numero === this.pedido) this.error.set('No se pudieron cargar los clientes.');
    }
  }
}
