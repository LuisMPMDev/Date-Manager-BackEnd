import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RELOJ } from '../../core/reloj';
import { AgendaDiaComponent } from '../../shared/agenda-dia';
import { aFechaISO, fechaLarga, sumarDias } from '../../shared/formato';

@Component({
  selector: 'app-agenda',
  imports: [AgendaDiaComponent],
  templateUrl: './agenda.html',
  styleUrl: './agenda.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgendaComponent {
  protected readonly hoy = aFechaISO(inject(RELOJ)());

  protected readonly fecha = signal(this.hoy);
  protected readonly fechaLarga = fechaLarga;

  protected cambiarDia(delta: number): void {
    this.fecha.update(f => sumarDias(f, delta));
  }

  protected irAHoy(): void {
    this.fecha.set(this.hoy);
  }

  protected elegirFecha(evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    if (valor) this.fecha.set(valor);
  }
}
