import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { HorarioNoDisponibleError, RELOJ } from '../../core/errores';
import { aHHmm, isoDia, MAX_DIAS_ANTICIPACION, sumarMinutos } from '../../core/horarios';
import { Cita, Servicio } from '../../core/models';
import { aFechaISO, fechaLarga, moneda, sumarDias } from '../../shared/formato';
import { construirRegla } from '../../shared/regla-bloques';
import { ReglaTiempoComponent } from '../../shared/regla-tiempo';

type Paso = 1 | 2 | 3;
type Campo = 'nombre' | 'apellido' | 'telefono' | 'correo';

const CAMPOS: { campo: Campo; etiqueta: string; tipo: string; auto: string; error: string }[] = [
  { campo: 'nombre', etiqueta: 'Nombre', tipo: 'text', auto: 'given-name', error: 'Escribe tu nombre.' },
  { campo: 'apellido', etiqueta: 'Apellido', tipo: 'text', auto: 'family-name', error: 'Escribe tu apellido.' },
  { campo: 'telefono', etiqueta: 'Teléfono', tipo: 'tel', auto: 'tel', error: 'Escribe tu teléfono.' },
  { campo: 'correo', etiqueta: 'Correo', tipo: 'email', auto: 'email', error: 'Escribe un correo válido.' },
];

const CORREO_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'app-reservar',
  imports: [ReglaTiempoComponent, RouterLink],
  templateUrl: './reservar.html',
  styleUrl: './reservar.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservarComponent implements OnInit {
  private readonly repo = inject(AgendaRepo);
  private readonly reloj = inject(RELOJ);

  protected readonly campos = CAMPOS;
  protected readonly moneda = moneda;
  protected readonly fechaLarga = fechaLarga;

  protected readonly paso = signal<Paso>(1);
  protected readonly servicios = signal<Servicio[]>([]);
  protected readonly servicioId = signal<string | null>(null);
  protected readonly hoy = signal(aFechaISO(this.reloj()));
  protected readonly fecha = signal(this.hoy());
  protected readonly libres = signal<string[]>([]);
  protected readonly elegido = signal<string | null>(null);
  protected readonly error = signal('');
  protected readonly enviando = signal(false);
  protected readonly confirmada = signal<Cita | null>(null);

  protected readonly datos = signal<Record<Campo, string>>({ nombre: '', apellido: '', telefono: '', correo: '' });
  protected readonly intentado = signal(false);

  private pedido = 0; // descarta respuestas de horarios que llegan tarde

  protected readonly ultimoDia = computed(() => sumarDias(this.hoy(), MAX_DIAS_ANTICIPACION));
  protected readonly servicio = computed(() => this.servicios().find(s => s.id === this.servicioId()) ?? null);

  protected readonly regla = computed(() => {
    const servicio = this.servicio();
    if (!servicio) return null;
    const dia = isoDia(this.fecha());
    return construirRegla({
      ventanas: servicio.ventanas.filter(v => v.dia === dia),
      duracionMin: servicio.duracionMin,
      libres: this.libres(),
      elegido: this.elegido(),
    });
  });

  protected readonly ahoraMin = computed(() => {
    if (this.fecha() !== this.hoy()) return null;
    const ahora = this.reloj();
    return ahora.getHours() * 60 + ahora.getMinutes();
  });

  protected readonly errores = computed(() => {
    const d = this.datos();
    const errores: Partial<Record<Campo, string>> = {};
    for (const c of CAMPOS) {
      const valor = d[c.campo].trim();
      const valido = c.campo === 'correo' ? CORREO_VALIDO.test(valor) : valor.length > 0;
      if (!valido) errores[c.campo] = c.error;
    }
    return errores;
  });

  protected readonly horaFin = computed(() => {
    const servicio = this.servicio();
    const inicio = this.elegido();
    return servicio && inicio ? sumarMinutos(inicio, servicio.duracionMin) : '';
  });

  async ngOnInit(): Promise<void> {
    this.servicios.set((await this.repo.servicios()).filter(s => s.activo));
  }

  protected async elegirServicio(s: Servicio): Promise<void> {
    this.servicioId.set(s.id);
    this.libres.set([]);
    this.elegido.set(null);
    this.error.set('');
    await this.cargarLibres();
  }

  protected async cambiarDia(delta: number): Promise<void> {
    this.fecha.set(sumarDias(this.fecha(), delta));
    this.libres.set([]);
    this.elegido.set(null);
    this.error.set('');
    await this.cargarLibres();
  }

  protected elegirHora(minutos: number): void {
    this.elegido.set(aHHmm(minutos));
    this.error.set('');
  }

  protected escribir(campo: Campo, evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.datos.update(d => ({ ...d, [campo]: valor }));
  }

  protected continuar(): void {
    if (this.elegido()) this.paso.set(2);
  }

  protected cambiarHorario(): void {
    this.paso.set(1);
  }

  protected async enviar(evento?: Event): Promise<void> {
    evento?.preventDefault();
    this.intentado.set(true);
    const servicio = this.servicio();
    const inicio = this.elegido();
    if (Object.keys(this.errores()).length || !servicio || !inicio || this.enviando()) return;

    this.enviando.set(true);
    try {
      const d = this.datos();
      const cita = await this.repo.reservar({
        servicioId: servicio.id,
        fecha: this.fecha(),
        inicio,
        cliente: {
          nombre: d.nombre.trim(),
          apellido: d.apellido.trim(),
          telefono: d.telefono.trim(),
          correo: d.correo.trim(),
        },
      });
      this.confirmada.set(cita);
      this.paso.set(3);
    } catch (e) {
      this.error.set(
        e instanceof HorarioNoDisponibleError ? e.message : 'No pudimos reservar tu cita. Intenta de nuevo.',
      );
      this.elegido.set(null);
      this.paso.set(1);
      await this.cargarLibres();
    } finally {
      this.enviando.set(false);
    }
  }

  protected reservarOtra(): void {
    this.confirmada.set(null);
    this.servicioId.set(null);
    this.elegido.set(null);
    this.libres.set([]);
    this.error.set('');
    this.fecha.set(this.hoy());
    this.datos.set({ nombre: '', apellido: '', telefono: '', correo: '' });
    this.intentado.set(false);
    this.paso.set(1);
  }

  private async cargarLibres(): Promise<void> {
    const id = this.servicioId();
    const numero = ++this.pedido;
    if (!id) {
      this.libres.set([]);
      return;
    }
    const libres = await this.repo.horariosLibres(id, this.fecha());
    if (numero === this.pedido) this.libres.set(libres);
  }
}
