import { ApiError } from '../core/api-error';
import { camposDe, mensajeDe } from './errores';

describe('mensajeDe', () => {
  it('usa el mensaje de la API (ya en español)', () => {
    expect(mensajeDe(new ApiError(409, 'HORARIO_NO_DISPONIBLE', 'Ese horario ya no está disponible.'), 'respaldo'))
      .toBe('Ese horario ya no está disponible.');
  });

  it('un 403 siempre dice lo mismo, venga el texto que venga', () => {
    expect(mensajeDe(new ApiError(403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción.'), 'respaldo'))
      .toBe('No tienes permiso para ver esta información.');
  });

  it('cualquier otra cosa (Error, texto, nada) usa el respaldo', () => {
    expect(mensajeDe(new Error('boom'), 'respaldo')).toBe('respaldo');
    expect(mensajeDe('boom', 'respaldo')).toBe('respaldo');
    expect(mensajeDe(undefined, 'respaldo')).toBe('respaldo');
  });
});

describe('camposDe', () => {
  it('devuelve los campos de la API con la clave normalizada: cliente.correo y correo son el mismo campo', () => {
    const e = new ApiError(400, 'VALIDACION', 'Hay datos inválidos en la petición.', {
      'cliente.correo': 'Escribe un correo válido.',
      nombre: 'Escribe tu nombre.',
    });
    expect(camposDe(e)).toEqual({ correo: 'Escribe un correo válido.', nombre: 'Escribe tu nombre.' });
  });

  it('si llegan cliente.correo y correo a la vez, gana el primero', () => {
    const e = new ApiError(400, 'VALIDACION', 'x', { 'cliente.correo': 'uno', correo: 'dos' });
    expect(camposDe(e)).toEqual({ correo: 'uno' });
  });

  it('deja intactas las claves que no son del cliente', () => {
    const e = new ApiError(400, 'VALIDACION', 'x', { fecha: 'Elige la fecha.', servicioId: 'Elige un servicio.' });
    expect(camposDe(e)).toEqual({ fecha: 'Elige la fecha.', servicioId: 'Elige un servicio.' });
  });

  it('sin campos, o con un error que no es de la API, devuelve un mapa vacío', () => {
    expect(camposDe(new ApiError(500, 'ERROR_INTERNO', 'x'))).toEqual({});
    expect(camposDe(new Error('boom'))).toEqual({});
    expect(camposDe(null)).toEqual({});
  });
});
