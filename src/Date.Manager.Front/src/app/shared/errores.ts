import { ApiError } from '../core/api-error';

/** Aviso de una sección cuyos datos el rol no puede leer (403 en una llamada secundaria). */
export const AVISO_SIN_PERMISO = 'No tienes permiso para ver esta información.';

/** La API respondió 403: el rol no puede leer o hacer eso. */
export function esSinPermiso(e: unknown): boolean {
  return e instanceof ApiError && e.status === 403;
}

/** La API respondió 409 (CONFLICTO, ESTADO_INVALIDO, CITA_NO_HA_INICIADO, HORARIO_NO_DISPONIBLE): lo que se ve en pantalla quedó viejo. */
export function esConflicto(e: unknown): boolean {
  return e instanceof ApiError && e.status === 409;
}

/** El texto del error de la API (ya en español); un 403 siempre dice lo mismo. Cualquier otro fallo usa el respaldo. */
export function mensajeDe(e: unknown, respaldo: string): string {
  if (esSinPermiso(e)) return AVISO_SIN_PERMISO;
  return e instanceof ApiError ? e.message : respaldo;
}

/** Para una acción (guardar, cancelar, mover…): el texto de la API tal cual, también en un 403. Cualquier otro fallo usa el respaldo. */
export function mensajeAccion(e: unknown, respaldo: string): string {
  return e instanceof ApiError ? e.message : respaldo;
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

/** Lo que un formulario muestra tras un error al guardar: el texto junto a cada campo y un error general. */
export interface ErrorFormulario {
  campos: Record<string, string>;
  general: string;
}

/**
 * Reparte el error de una escritura: los `campos` que el formulario tiene (`conocidos`, con la clave ya normalizada)
 * van junto a su campo; los que no tiene se unen en el error general para que no se pierdan; sin `campos`, el
 * mensaje de la API (o el `respaldo`) es el error general.
 */
export function errorDeFormulario(e: unknown, conocidos: readonly string[], respaldo: string): ErrorFormulario {
  const todos = camposDe(e);
  const campos: Record<string, string> = {};
  const ajenos: string[] = [];
  for (const [clave, texto] of Object.entries(todos)) {
    if (conocidos.includes(clave)) campos[clave] = texto;
    else ajenos.push(texto);
  }
  if (ajenos.length) return { campos, general: ajenos.join(' ') };
  return { campos, general: Object.keys(todos).length ? '' : mensajeAccion(e, respaldo) };
}
