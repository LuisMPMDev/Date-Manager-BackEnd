import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-panel',
  template: '<h2>Panel</h2>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelComponent {}
