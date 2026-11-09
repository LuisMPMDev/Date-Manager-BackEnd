import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { AhoraService } from '../../core/ahora';
import { ApiError } from '../../core/api-error';
import { Alerta, Inicio } from '../../core/inicio';
import { InicioApi } from '../../core/inicio-api';
import { SesionService } from '../../core/sesion';
import { AgendaDiaComponent } from '../../shared/agenda-dia';
import { fechaLarga } from '../../shared/formato';
import { GraficaComponent } from '../../shared/grafica';
import { construirKpis, serieCitasPorDia, serieOcupacion } from './panel-presentacion';

const SIN_PERMISO = 'No tienes permiso para ver esta información.';
const NO_CARGO = 'No se pudo cargar el panel.';
const NO_ACTUALIZO = 'No se pudo actualizar el panel.';

@Component({
  selector: 'app-panel',
  imports: [AgendaDiaComponent, GraficaComponent],
  templateUrl: './panel.html',
  styleUrl: './panel.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelComponent {
  private readonly repo = inject(AgendaRepo);
  private readonly api = inject(InicioApi);
  private readonly ahora = inject(AhoraService).ahora;
  private readonly router = inject(Router);
  private readonly sesion = inject(SesionService);

  protected readonly fechaLarga = fechaLarga;

  private readonly datos = signal<Inicio | null>(null);
  protected readonly error = signal('');
  /** Aviso discreto: una recarga falló pero siguen a la vista los datos de la última carga buena. */
  protected readonly aviso = signal('');
  private pedido = 0; // descarta respuestas que llegan tarde

  /** «Abrir» lleva a Citas: sin ese permiso el botón solo rebotaría. */
  protected readonly puedeVerCitas = computed(() => this.sesion.puede('ver_citas'));

  protected readonly saludo = computed(() => {
    const nombre = this.sesion.usuario()?.nombre;
    return nombre ? `Hola, ${nombre}. Así va el día` : 'Hola. Así va el día';
  });

  protected readonly vista = computed(() => {
    const inicio = this.datos();
    if (!inicio) return null;
    return {
      fecha: inicio.fecha,
      kpis: construirKpis(inicio),
      alertas: inicio.alertas,
      alertasExtra: inicio.alertasExtra,
      proximas: inicio.proximas.map(cita => ({ cita, cliente: `${cita.clienteNombre} ${cita.clienteApellido}` })),
      citasPorDia: serieCitasPorDia(inicio.citasPorDia),
      ocupacion: serieOcupacion(inicio.ocupacionSemana),
    };
  });

  constructor() {
    // Recarga al cambiar los datos del negocio y cada minuto, para que «en N min» y las próximas no se queden viejas.
    effect(() => {
      this.repo.cambios();
      this.ahora();
      void this.cargar();
    });
  }

  protected abrir(alerta: Alerta): void {
    void this.router.navigateByUrl(alerta.tipo === 'servicio_sin_horario' ? '/servicios' : '/citas');
  }

  protected abrirCitas(): void {
    void this.router.navigateByUrl('/citas');
  }

  /** Sin `fecha`: el servidor decide cuál es «hoy» con la zona horaria del negocio. */
  private async cargar(): Promise<void> {
    const numero = ++this.pedido;
    try {
      const inicio = await this.api.inicio();
      if (numero !== this.pedido) return;
      this.datos.set(inicio);
      this.error.set('');
      this.aviso.set('');
    } catch (e) {
      if (numero !== this.pedido) return;
      const sinPermiso = e instanceof ApiError && e.status === 403;
      if (this.datos() && !sinPermiso) {
        // Una recarga fallida no borra un tablero que estaba bien: se deja una nota hasta la siguiente carga correcta.
        this.aviso.set(NO_ACTUALIZO);
        return;
      }
      this.datos.set(null);
      this.aviso.set('');
      this.error.set(sinPermiso ? SIN_PERMISO : NO_CARGO);
    }
  }
}
