import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';

import { routes } from './app.routes';
import { AgendaRepo } from './core/agenda-repo';
import { RELOJ } from './core/errores';
import { HttpAgendaRepo } from './core/http-agenda-repo';
import { authInterceptor, errorInterceptor } from './core/interceptores';
import { SesionService } from './core/sesion';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    { provide: RELOJ, useValue: () => new Date() },
    { provide: AgendaRepo, useClass: HttpAgendaRepo },
    // La sesión se construye al arrancar (sin esperar `listo`): así ya escucha el aviso de sesión vencida
    // aunque el primer 401 llegue antes de que alguna guarda la pida.
    provideAppInitializer(() => {
      inject(SesionService);
    }),
  ]
};
