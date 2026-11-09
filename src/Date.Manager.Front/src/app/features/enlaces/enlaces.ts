import { afterNextRender, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, Injector, signal } from '@angular/core';
import { ApiError } from '../../core/api-error';
import { Enlace, EnlacesApi, PeticionEnlace } from '../../core/enlaces-api';
import { fechaHora } from './enlaces-formato';

const DURACION_MIN = 60;
const DURACION_MAX = 43200;
const DURACION_POR_DEFECTO = '1440';
/** El servidor guarda `maxUsos` en un Integer de Java. */
const USOS_MAX = 2147483647;
const AVISO_MS = 5000;

const SIN_PERMISO = 'No tienes permiso para ver esta información.';
const NO_CARGO = 'No se pudo cargar la lista de enlaces.';
const NO_CREO = 'No se pudo crear el enlace. Inténtalo de nuevo.';
const NO_DESACTIVO = 'No se pudo desactivar el enlace. Inténtalo de nuevo.';
const MENSAJE_DURACION = `La duración debe ser un número entero de minutos entre ${DURACION_MIN} y ${DURACION_MAX}.`;
const MENSAJE_USOS = `El máximo de usos debe ser un número entero entre 1 y ${USOS_MAX}, o quedar vacío para no limitarlo.`;

/** El texto del error de la API (ya en español); un 403 siempre dice lo mismo en esta pantalla. */
function mensajeDe(e: unknown, respaldo: string): string {
  if (e instanceof ApiError) return e.status === 403 ? SIN_PERMISO : e.message;
  return respaldo;
}

/** Entero escrito en un campo de texto (se recortan espacios); null si no es un entero representable con exactitud. */
function entero(texto: string): number | null {
  const limpio = texto.trim();
  if (!/^-?\d+$/.test(limpio)) return null;
  const valor = Number(limpio);
  return Number.isSafeInteger(valor) ? valor : null;
}

/** Valida los campos antes de enviar: devuelve la petición o el mensaje que debe verse. */
function armarPeticion(duracion: string, usos: string): PeticionEnlace | string {
  const minutos = entero(duracion);
  if (minutos === null || minutos < DURACION_MIN || minutos > DURACION_MAX) return MENSAJE_DURACION;
  if (usos.trim() === '') return { duracionMinutos: minutos };
  const maxUsos = entero(usos);
  if (maxUsos === null || maxUsos < 1 || maxUsos > USOS_MAX) return MENSAJE_USOS;
  return { duracionMinutos: minutos, maxUsos };
}

/** URL completa que se le entrega al cliente: la ruta lógica va tras el `#` por el enrutado con hash. */
function urlCompleta(ruta: string): string {
  return `${location.origin}${location.pathname}#${ruta}`;
}

@Component({
  selector: 'app-enlaces',
  templateUrl: './enlaces.html',
  styleUrl: './enlaces.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnlacesComponent {
  private readonly api = inject(EnlacesApi);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private temporizadorAviso: ReturnType<typeof setTimeout> | undefined;
  private destruido = false;

  protected readonly fechaHora = fechaHora;
  protected readonly urlCompleta = urlCompleta;

  protected readonly enlaces = signal<Enlace[]>([]);
  protected readonly cargando = signal(true);
  /** Error de la lista o de desactivar. */
  protected readonly errorLista = signal('');

  protected readonly duracion = signal(DURACION_POR_DEFECTO);
  protected readonly maxUsos = signal('');
  protected readonly errorForm = signal('');
  protected readonly creando = signal(false);
  /** URL del último enlace creado, lista para copiar. */
  protected readonly urlCreada = signal('');
  protected readonly aviso = signal('');

  protected readonly confirmando = signal<string | null>(null);
  protected readonly desactivando = signal(false);

  private pedido = 0; // descarta respuestas de la lista que llegan tarde

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.destruido = true;
      clearTimeout(this.temporizadorAviso);
    });
    void this.cargar();
  }

  protected escribirDuracion(evento: Event): void {
    this.duracion.set((evento.target as HTMLInputElement).value);
  }

  protected escribirUsos(evento: Event): void {
    this.maxUsos.set((evento.target as HTMLInputElement).value);
  }

  protected async crear(evento?: Event): Promise<void> {
    evento?.preventDefault();
    if (this.creando()) return;
    this.anunciar('');
    this.errorForm.set('');
    const peticion = armarPeticion(this.duracion(), this.maxUsos());
    if (typeof peticion === 'string') {
      this.errorForm.set(peticion);
      return;
    }
    this.creando.set(true);
    try {
      const creado = await this.api.crear(peticion);
      this.urlCreada.set(urlCompleta(creado.ruta));
      this.duracion.set(DURACION_POR_DEFECTO);
      this.maxUsos.set('');
      await this.cargar();
    } catch (e) {
      this.errorForm.set(mensajeDe(e, NO_CREO));
    } finally {
      this.creando.set(false);
    }
  }

  /** Copia al portapapeles; si no se puede, deja el texto seleccionado para copiarlo a mano. */
  protected async copiar(url: string): Promise<void> {
    try {
      if (!navigator.clipboard) throw new Error('Sin portapapeles');
      await navigator.clipboard.writeText(url);
      this.anunciar('Enlace copiado.');
    } catch {
      // Sin temporizador: hay que dejar tiempo para seleccionar y copiar el texto a mano.
      this.anunciar('No se pudo copiar automáticamente. Selecciona el texto y cópialo.', false);
      this.urlCreada.set(url);
      afterNextRender(
        () => this.host.nativeElement.querySelector<HTMLInputElement>('input[readonly]')?.select(),
        { injector: this.injector },
      );
    }
  }

  protected seleccionar(evento: Event): void {
    (evento.target as HTMLInputElement).select();
  }

  protected pedirDesactivar(id: string): void {
    this.anunciar('');
    this.errorLista.set('');
    this.confirmando.set(id);
    this.enfocar(`[data-enlace="${id}"] [data-volver]`);
  }

  protected volver(id: string): void {
    this.confirmando.set(null);
    this.enfocar(`[data-enlace="${id}"] [data-desactivar]`);
  }

  protected async confirmarDesactivar(id: string): Promise<void> {
    if (this.desactivando()) return;
    this.desactivando.set(true);
    try {
      await this.api.desactivar(id);
      this.confirmando.set(null);
      await this.cargar();
    } catch (e) {
      this.confirmando.set(null);
      this.errorLista.set(mensajeDe(e, NO_DESACTIVO));
    } finally {
      this.desactivando.set(false);
      // La confirmación desaparece con el botón enfocado: el foco pasa al encabezado de la lista.
      this.enfocar('[data-lista]');
    }
  }

  /** Muestra un aviso y, si `temporal`, lo borra solo a los pocos segundos; '' lo quita ya. */
  private anunciar(texto: string, temporal = true): void {
    clearTimeout(this.temporizadorAviso);
    this.aviso.set(texto);
    if (texto && temporal) this.temporizadorAviso = setTimeout(() => this.aviso.set(''), AVISO_MS);
  }

  /** Mueve el foco a `selector` cuando la vista ya se actualizó. */
  private enfocar(selector: string): void {
    if (this.destruido) return; // tras un await la pantalla puede haberse cerrado: afterNextRender lanzaría NG0911
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(),
      { injector: this.injector },
    );
  }

  private async cargar(): Promise<void> {
    const numero = ++this.pedido;
    try {
      const lista = await this.api.listar();
      if (numero !== this.pedido) return;
      this.enlaces.set(lista);
      this.errorLista.set('');
    } catch (e) {
      if (numero !== this.pedido) return;
      this.errorLista.set(mensajeDe(e, NO_CARGO));
    } finally {
      if (numero === this.pedido) this.cargando.set(false);
    }
  }
}
