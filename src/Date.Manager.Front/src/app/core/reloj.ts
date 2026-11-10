import { InjectionToken } from '@angular/core';

/** Reloj inyectable para poder fijar la hora en las pruebas. */
export const RELOJ = new InjectionToken<() => Date>('RELOJ', {
  providedIn: 'root',
  factory: () => () => new Date(),
});
