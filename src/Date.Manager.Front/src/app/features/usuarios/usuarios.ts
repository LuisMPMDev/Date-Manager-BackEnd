import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-usuarios',
  template: '<h2>Usuarios y roles</h2>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsuariosComponent {}
