import { HttpErrorResponse } from '@angular/common/http';

const MENSAJE_RED = 'No se pudo contactar al servidor. Revisa tu conexión e inténtalo de nuevo.';
const MENSAJE_INTERNO = 'No se pudo contactar al servidor o este respondió algo inesperado. Inténtalo de nuevo más tarde.';

/** Error de la API con la forma {codigo, mensaje, campos?}, o un fallo de red / respuesta inesperada. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly codigo: string,
    mensaje: string,
    readonly campos: Record<string, string> = {},
  ) {
    super(mensaje);
    this.name = 'ApiError';
  }

  static desde(e: HttpErrorResponse): ApiError {
    const cuerpo = comoObjeto(e.error);
    if (cuerpo && typeof cuerpo['codigo'] === 'string') {
      const mensaje = typeof cuerpo['mensaje'] === 'string' ? cuerpo['mensaje'] : MENSAJE_INTERNO;
      return new ApiError(e.status, cuerpo['codigo'], mensaje, soloTextos(cuerpo['campos']));
    }
    if (e.status === 0) return new ApiError(0, 'ERROR_RED', MENSAJE_RED);
    return new ApiError(e.status, 'ERROR_INTERNO', MENSAJE_INTERNO);
  }
}

/** El cuerpo puede llegar ya como objeto o como texto JSON; cualquier otra cosa no es de la API. */
function comoObjeto(cuerpo: unknown): Record<string, unknown> | null {
  if (typeof cuerpo === 'string') {
    try {
      cuerpo = JSON.parse(cuerpo);
    } catch {
      return null;
    }
  }
  return typeof cuerpo === 'object' && cuerpo !== null && !Array.isArray(cuerpo)
    ? (cuerpo as Record<string, unknown>)
    : null;
}

function soloTextos(valor: unknown): Record<string, string> {
  const salida: Record<string, string> = {};
  if (typeof valor === 'object' && valor !== null && !Array.isArray(valor)) {
    for (const [clave, texto] of Object.entries(valor)) {
      if (typeof texto === 'string') salida[clave] = texto;
    }
  }
  return salida;
}
