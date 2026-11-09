import { afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, ElementRef, inject, Injector, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiError } from '../../core/api-error';
import { AhoraService } from '../../core/ahora';
import { aHHmm, isoDia, MAX_DIAS_ANTICIPACION, sumarMinutos } from '../../core/horarios';
import { Servicio } from '../../core/models';
import { CitaPublica, PublicoApi } from '../../core/publico-api';
import { esCorreoValido } from '../../core/validaciones';
import { camposDe, mensajeDe } from '../../shared/errores';
import { aFechaISO, fechaLarga, moneda, sumarDias } from '../../shared/formato';
import { construirRegla } from '../../shared/regla-bloques';
import { ReglaTiempoComponent } from '../../shared/regla-tiempo';

type Paso = 1 | 2 | 3;
type Campo = 'nombre' | 'apellido' | 'telefono' | 'correo';

/** Lo que ve el cliente: el flujo (`activo`), un enlace que ya no sirve, o la carga / un fallo al abrirlo. */
type Vista = 'cargando' | 'activo' | 'falla' | 'Vencido' | 'Agotado' | 'Desactivado' | 'noEncontrado';
type VistaCerrada = 'Vencido' | 'Agotado' | 'Desactivado' | 'noEncontrado';

const CAMPOS: { campo: Campo; etiqueta: string; tipo: string; auto: string; error: string }[] = [
  { campo: 'nombre', etiqueta: 'Nombre', tipo: 'text', auto: 'given-name', error: 'Escribe tu nombre.' },
  { campo: 'apellido', etiqueta: 'Apellido', tipo: 'text', auto: 'family-name', error: 'Escribe tu apellido.' },
  { campo: 'telefono', etiqueta: 'Teléfono', tipo: 'tel', auto: 'tel', error: 'Escribe tu teléfono.' },
  { campo: 'correo', etiqueta: 'Correo', tipo: 'email', auto: 'email', error: 'Escribe un correo válido.' },
];

const CERRADAS: Record<VistaCerrada, { titulo: string; texto: string }> = {
  Vencido: { titulo: 'Este enlace venció', texto: 'Pide un enlace nuevo al negocio para reservar tu cita.' },
  Agotado: { titulo: 'Este enlace ya no tiene reservas disponibles', texto: 'Pide un enlace nuevo al negocio para reservar tu cita.' },
  Desactivado: { titulo: 'Este enlace fue desactivado', texto: 'Pide un enlace nuevo al negocio para reservar tu cita.' },
  noEncontrado: { titulo: 'Enlace no encontrado', texto: 'Revisa que la dirección esté completa o pide un enlace nuevo al negocio.' },
};

/** Los códigos de error de la API que significan «este enlace ya no sirve» (o nunca existió). */
const VISTA_DEL_CODIGO: Record<string, VistaCerrada> = {
  ENLACE_VENCIDO: 'Vencido',
  ENLACE_AGOTADO: 'Agotado',
  ENLACE_DESACTIVADO: 'Desactivado',
  ENLACE_NO_ENCONTRADO: 'noEncontrado',
};

const NO_ABRIO = 'No pudimos abrir el enlace. Inténtalo de nuevo.';
const NO_HORARIOS = 'No pudimos cargar los horarios. Inténtalo de nuevo.';
const NO_RESERVO = 'No pudimos reservar tu cita. Intenta de nuevo.';

function cerradaPorError(e: unknown): VistaCerrada | null {
  return e instanceof ApiError ? (VISTA_DEL_CODIGO[e.codigo] ?? null) : null;
}

const DATOS_VACIOS = (): Record<Campo, string> => ({ nombre: '', apellido: '', telefono: '', correo: '' });

@Component({
  selector: 'app-agendar',
  imports: [ReglaTiempoComponent, RouterLink],
  templateUrl: './agendar.html',
  styleUrl: './agendar.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgendarComponent implements OnInit {
  private readonly api = inject(PublicoApi);
  private readonly ruta = inject(ActivatedRoute);
  private readonly ahora = inject(AhoraService).ahora;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly destruccion = inject(DestroyRef);

  protected readonly campos = CAMPOS;
  protected readonly moneda = moneda;
  protected readonly fechaLarga = fechaLarga;

  protected readonly vista = signal<Vista>('cargando');
  protected readonly paso = signal<Paso>(1);
  protected readonly servicios = signal<Servicio[]>([]);
  protected readonly servicioId = signal<string | null>(null);
  protected readonly hoy = computed(() => aFechaISO(this.ahora()));
  protected readonly fecha = signal(this.hoy());
  protected readonly libres = signal<string[]>([]);
  protected readonly elegido = signal<string | null>(null);
  protected readonly error = signal('');
  protected readonly errorApertura = signal('');
  protected readonly enviando = signal(false);
  protected readonly confirmada = signal<CitaPublica | null>(null);

  protected readonly datos = signal<Record<Campo, string>>(DATOS_VACIOS());
  protected readonly intentado = signal(false);
  /** Errores por campo que devolvió la API (un 400); se van al corregir el campo. */
  private readonly erroresApi = signal<Partial<Record<Campo, string>>>({});

  private codigo = '';
  private pedido = 0; // descarta respuestas de horarios que llegan tarde
  private apertura = 0; // descarta respuestas del enlace anterior si cambia el código de la ruta

  protected readonly cerrada = computed(() => {
    const vista = this.vista();
    return vista in CERRADAS ? CERRADAS[vista as VistaCerrada] : null;
  });

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
    const ahora = this.ahora();
    return ahora.getHours() * 60 + ahora.getMinutes();
  });

  /** Lo que falta en cada campo según las reglas locales. */
  private readonly erroresLocales = computed(() => {
    const d = this.datos();
    const errores: Partial<Record<Campo, string>> = {};
    for (const c of CAMPOS) {
      const valor = d[c.campo].trim();
      const valido = c.campo === 'correo' ? esCorreoValido(valor) : valor.length > 0;
      if (!valido) errores[c.campo] = c.error;
    }
    return errores;
  });

  /** Lo que se muestra junto a cada campo: lo local una vez intentado el envío, o lo que dijo la API. */
  protected readonly errores = computed(() => {
    const locales = this.intentado() ? this.erroresLocales() : {};
    return { ...this.erroresApi(), ...locales };
  });

  protected readonly horaFin = computed(() => {
    const servicio = this.servicio();
    const inicio = this.elegido();
    return servicio && inicio ? sumarMinutos(inicio, servicio.duracionMin) : '';
  });

  ngOnInit(): void {
    // El código viene de la ruta; si cambia con el componente abierto (otra dirección en el navegador) se reinicia todo.
    this.ruta.paramMap.pipe(takeUntilDestroyed(this.destruccion)).subscribe(params => {
      this.codigo = params.get('codigo') ?? '';
      void this.abrir();
    });
  }

  protected async abrir(): Promise<void> {
    const numero = ++this.apertura;
    this.reiniciar();
    this.servicios.set([]);
    this.vista.set('cargando');
    this.errorApertura.set('');
    try {
      const { estado } = await this.api.estado(this.codigo);
      if (numero !== this.apertura) return;
      if (estado !== 'Activo') {
        this.cerrar(estado);
        return;
      }
      const servicios = await this.api.servicios(this.codigo);
      if (numero !== this.apertura) return;
      this.servicios.set(servicios.filter(s => s.activo));
      this.vista.set('activo');
    } catch (e) {
      if (numero !== this.apertura) return;
      const cerrada = cerradaPorError(e);
      if (cerrada) {
        this.cerrar(cerrada);
      } else {
        this.errorApertura.set(mensajeDe(e, NO_ABRIO));
        this.vista.set('falla');
      }
    }
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
    if (this.erroresApi()[campo]) {
      this.erroresApi.update(errores => {
        const resto = { ...errores };
        delete resto[campo];
        return resto;
      });
    }
  }

  protected continuar(): void {
    if (!this.elegido()) return;
    this.paso.set(2);
    this.enfocarTitulo();
  }

  protected cambiarHorario(): void {
    this.paso.set(1);
    this.enfocarTitulo();
  }

  protected async enviar(evento?: Event): Promise<void> {
    evento?.preventDefault();
    this.intentado.set(true);
    const servicio = this.servicio();
    const inicio = this.elegido();
    const locales = this.erroresLocales();
    const primero = CAMPOS.find(c => locales[c.campo]);
    if (primero) {
      this.enfocarCampo(primero.campo);
      return;
    }
    if (!servicio || !inicio || this.enviando()) return;

    this.enviando.set(true);
    this.error.set('');
    this.erroresApi.set({});
    try {
      const d = this.datos();
      const cita = await this.api.reservar(this.codigo, {
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
      this.enfocarTitulo();
    } catch (e) {
      await this.alFallarElEnvio(e);
    } finally {
      this.enviando.set(false);
    }
  }

  protected reservarOtra(): void {
    this.reiniciar();
    this.enfocarTitulo();
  }

  /** Cada fallo del envío tiene su salida; lo escrito por el cliente se conserva en todas menos en el cierre. */
  private async alFallarElEnvio(e: unknown): Promise<void> {
    const cerrada = cerradaPorError(e);
    if (cerrada) {
      this.cerrar(cerrada); // el enlace dejó de servir a mitad del flujo
      return;
    }
    if (e instanceof ApiError && e.codigo === 'HORARIO_NO_DISPONIBLE') {
      this.error.set(e.message);
      this.elegido.set(null);
      this.paso.set(1);
      this.enfocarTitulo();
      await this.cargarLibres();
      return;
    }
    const delFormulario: Partial<Record<Campo, string>> = {};
    const ajenos: string[] = [];
    for (const [clave, texto] of Object.entries(camposDe(e))) {
      const campo = CAMPOS.find(c => c.campo === clave);
      if (campo) delFormulario[campo.campo] = texto;
      else ajenos.push(texto);
    }
    this.erroresApi.set(delFormulario);
    const primero = CAMPOS.find(c => delFormulario[c.campo]);
    if (primero) this.enfocarCampo(primero.campo);
    // Sin campos del formulario (o con claves que no son suyas) el mensaje va como error general.
    this.error.set(ajenos.length ? ajenos.join(' ') : primero ? '' : mensajeDe(e, NO_RESERVO));
  }

  /** El enlace ya no sirve: se cierra el flujo y el título del mensaje recibe el foco. */
  private cerrar(vista: VistaCerrada): void {
    this.vista.set(vista);
    this.enfocarTitulo();
  }

  /** Vuelve al paso 1 sin servicio, hora ni datos (también al cambiar de enlace). */
  private reiniciar(): void {
    this.confirmada.set(null);
    this.servicioId.set(null);
    this.elegido.set(null);
    this.libres.set([]);
    this.error.set('');
    this.erroresApi.set({});
    this.fecha.set(this.hoy());
    this.datos.set(DATOS_VACIOS());
    this.intentado.set(false);
    this.paso.set(1);
  }

  /** Al cambiar de paso se destruye el botón pulsado: el foco pasa al título del paso nuevo. */
  private enfocarTitulo(): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('h1')?.focus(), { injector: this.injector });
  }

  private enfocarCampo(campo: Campo): void {
    this.host.nativeElement.querySelector<HTMLElement>(`#campo-${campo}`)?.focus();
  }

  private async cargarLibres(): Promise<void> {
    const id = this.servicioId();
    const numero = ++this.pedido;
    if (!id) {
      this.libres.set([]);
      return;
    }
    try {
      const libres = await this.api.horarios(this.codigo, id, this.fecha());
      if (numero === this.pedido) this.libres.set(libres);
    } catch (e) {
      if (numero !== this.pedido) return;
      const cerrada = cerradaPorError(e);
      if (cerrada) this.cerrar(cerrada);
      else this.error.set(mensajeDe(e, NO_HORARIOS));
    }
  }
}
