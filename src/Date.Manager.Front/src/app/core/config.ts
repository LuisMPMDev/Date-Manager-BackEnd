import { InjectionToken } from '@angular/core';

/** Dirección de la API por defecto; el único lugar donde se escribe. */
export const API_URL_POR_DEFECTO = 'http://localhost:8080/api';

/** Dirección base de la API (sin barra final). Se puede reemplazar en app.config o en pruebas. */
export const API_URL = new InjectionToken<string>('API_URL', {
  providedIn: 'root',
  factory: () => API_URL_POR_DEFECTO,
});
