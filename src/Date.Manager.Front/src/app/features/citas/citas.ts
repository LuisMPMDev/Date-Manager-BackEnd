import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-citas',
  template: '<h2>Citas</h2>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CitasComponent {}
