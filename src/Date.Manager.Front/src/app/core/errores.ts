import { InjectionToken } from '@angular/core';

export class HorarioNoDisponibleError extends Error {
  constructor(mensaje = 'Ese horario ya no está disponible, elige otro.') {
    super(mensaje);
    this.name = 'HorarioNoDisponibleError';
  }
}

export class RolEnUsoError extends Error {
  constructor(mensaje = 'No se puede eliminar un rol que tiene usuarios asignados.') {
    super(mensaje);
    this.name = 'RolEnUsoError';
  }
}

export class CambioNoPermitidoError extends Error {
  constructor(mensaje: string) {
    super(mensaje);
    this.name = 'CambioNoPermitidoError';
  }
}

/** Reloj inyectable para poder fijar la hora en las pruebas. */
export const RELOJ = new InjectionToken<() => Date>('RELOJ', {
  providedIn: 'root',
  factory: () => () => new Date(),
});
