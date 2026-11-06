import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { RELOJ } from '../../core/errores';
import { Alerta, alertas } from '../../core/metricas';
import { Cita, Cliente, Servicio } from '../../core/models';
import { SesionService } from '../../core/sesion';
import { AgendaDiaComponent } from '../../shared/agenda-dia';
import { aFechaISO, fechaLarga } from '../../shared/formato';
import { GraficaComponent } from '../../shared/grafica';
import { nombreCompleto } from '../citas/citas-filtros';
import { limitarAlertas, ocupacionSemana, serieCitasPorDia } from './panel-datos';
import { calcularKpis, proximasCitas } from './panel-kpis';

interface Datos {
  servicios: Servicio[];
  citas: Cita[];
  clientes: Cliente[];
  ahora: Date;
}

@Component({
  selector: 'app-panel',
  imports: [AgendaDiaComponent, GraficaComponent],
  templateUrl: './panel.html',
  styleUrl: './panel.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelComponent {
  private readonly repo = inject(AgendaRepo);
  private readonly reloj = inject(RELOJ);
  private readonly router = inject(Router);
  private readonly sesion = inject(SesionService);

  protected readonly fechaLarga = fechaLarga;
  protected readonly nombreCompleto = nombreCompleto;

  private readonly datos = signal<Datos | null>(null);
  protected readonly error = signal('');
  private pedido = 0; // descarta respuestas que llegan tarde

  protected readonly saludo = computed(() => {
    const nombre = this.sesion.usuario()?.nombre;
    return nombre ? `Hola, ${nombre}. Así va el día` : 'Hola. Así va el día';
  });

  protected readonly hoy = computed(() => aFechaISO(this.datos()?.ahora ?? this.reloj()));

  protected readonly vista = computed(() => {
    const d = this.datos();
    if (!d) return null;
    const hoy = aFechaISO(d.ahora);
    const { visibles, extra } = limitarAlertas(alertas(d.servicios, d.citas, d.ahora));
    return {
      kpis: calcularKpis(d),
      alertas: visibles,
      alertasExtra: extra,
      proximas: proximasCitas(d.citas, d.ahora, 5).map(cita => ({
        cita,
        hoy: cita.fecha === hoy,
        servicio: d.servicios.find(s => s.id === cita.servicioId),
        cliente: d.clientes.find(c => c.id === cita.clienteId),
      })),
      citasPorDia: serieCitasPorDia(d.citas, hoy),
      ocupacion: ocupacionSemana(d.servicios, d.citas, hoy),
    };
  });

  constructor() {
    effect(() => {
      this.repo.cambios();
      void this.cargar();
    });
  }

  protected abrir(alerta: Alerta): void {
    void this.router.navigateByUrl(alerta.tipo === 'servicio_sin_horario' ? '/servicios' : '/citas');
  }

  protected abrirCitas(): void {
    void this.router.navigateByUrl('/citas');
  }

  private async cargar(): Promise<void> {
    const numero = ++this.pedido;
    try {
      const [servicios, citas, clientes] = await Promise.all([
        this.repo.servicios(),
        this.repo.citas(),
        this.repo.clientes(),
      ]);
      if (numero !== this.pedido) return;
      this.datos.set({ servicios, citas, clientes, ahora: this.reloj() });
      this.error.set('');
    } catch {
      if (numero !== this.pedido) return;
      this.datos.set(null);
      this.error.set('No se pudo cargar el panel.');
    }
  }
}
