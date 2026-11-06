import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-agenda',
  template: '<h2>Agenda</h2>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgendaComponent {}
