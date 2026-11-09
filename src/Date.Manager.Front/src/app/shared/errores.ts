import { ApiError } from '../core/api-error';

const SIN_PERMISO = 'No tienes permiso para ver esta información.';

/** El texto del error de la API (ya en español); un 403 siempre dice lo mismo. Cualquier otro fallo usa el respaldo. */
export function mensajeDe(e: unknown, respaldo: string): string {
  if (e instanceof ApiError) return e.status === 403 ? SIN_PERMISO : e.message;
  return respaldo;
}

/**
 * Errores por campo de una respuesta 400 con las claves normalizadas: la API manda `cliente.correo` o `correo`
 * según la capa que rechaza, y la pantalla solo conoce `correo`. Si dos claves coinciden, gana la primera.
 */
export function camposDe(e: unknown): Record<string, string> {
  const salida: Record<string, string> = {};
  if (!(e instanceof ApiError)) return salida;
  for (const [clave, texto] of Object.entries(e.campos)) {
    const normal = clave.replace(/^cliente\./, '');
    if (!(normal in salida)) salida[normal] = texto;
  }
  return salida;
}
