import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-clientes',
  template: '<h2>Clientes</h2>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientesComponent {}
