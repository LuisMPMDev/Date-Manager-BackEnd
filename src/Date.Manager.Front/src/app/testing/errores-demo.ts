import { ApiError } from '../core/api-error';
import { MOTIVO_ROL_EN_USO } from '../core/permisos';

/**
 * Errores del doble en memoria (solo pruebas). Son ApiError con el código que mandaría la API, así las
 * pantallas los tratan igual que a los reales: por `.status` / `.codigo` y mostrando `.message`.
 */
export class HorarioNoDisponibleError extends ApiError {
  constructor(mensaje = 'Ese horario ya no está disponible, elige otro.') {
    super(409, 'HORARIO_NO_DISPONIBLE', mensaje);
    this.name = 'HorarioNoDisponibleError';
  }
}

export class RolEnUsoError extends ApiError {
  constructor(mensaje = MOTIVO_ROL_EN_USO) {
    super(409, 'CONFLICTO', mensaje);
    this.name = 'RolEnUsoError';
  }
}
