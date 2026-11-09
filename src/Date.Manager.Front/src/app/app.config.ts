import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';

import { routes } from './app.routes';
import { AgendaRepo } from './core/agenda-repo';
import { DemoAgendaRepo } from './core/demo-agenda-repo';
import { RELOJ } from './core/errores';
import { authInterceptor, errorInterceptor } from './core/interceptores';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    { provide: RELOJ, useValue: () => new Date() },
    { provide: AgendaRepo, useClass: DemoAgendaRepo }
  ]
};
