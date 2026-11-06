import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-sin-acceso',
  template: '<h2>Sin acceso</h2><p>Tu rol no tiene pantallas disponibles. Pide a un administrador que te asigne permisos.</p>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SinAccesoComponent {}
