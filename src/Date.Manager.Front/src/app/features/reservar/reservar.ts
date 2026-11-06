import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-reservar',
  template: '<h1>Reserva tu cita</h1>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservarComponent {}
