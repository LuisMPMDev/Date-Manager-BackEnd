import { ApiError } from '../core/api-error';
import { camposDe, errorDeFormulario, esConflicto, esSinPermiso, mensajeAccion, mensajeDe } from './errores';

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

describe('esSinPermiso y esConflicto', () => {
  it('esSinPermiso solo es verdad para un ApiError 403', () => {
    expect(esSinPermiso(new ApiError(403, 'SIN_PERMISO', 'x'))).toBe(true);
    expect(esSinPermiso(new ApiError(401, 'NO_AUTENTICADO', 'x'))).toBe(false);
    expect(esSinPermiso(new Error('403'))).toBe(false);
    expect(esSinPermiso(undefined)).toBe(false);
  });

  it('esConflicto es verdad para cualquier 409 de la API (CONFLICTO, ESTADO_INVALIDO, CITA_NO_HA_INICIADO, HORARIO_NO_DISPONIBLE)', () => {
    for (const codigo of ['CONFLICTO', 'ESTADO_INVALIDO', 'CITA_NO_HA_INICIADO', 'HORARIO_NO_DISPONIBLE']) {
      expect(esConflicto(new ApiError(409, codigo, 'x'))).toBe(true);
    }
    expect(esConflicto(new ApiError(400, 'VALIDACION', 'x'))).toBe(false);
    expect(esConflicto(new Error('409'))).toBe(false);
  });
});

describe('mensajeAccion', () => {
  it('usa el texto de la API tal cual, también en un 403 (es una acción, no una lectura)', () => {
    expect(mensajeAccion(new ApiError(403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción.'), 'respaldo'))
      .toBe('No tienes permiso para realizar esta acción.');
    expect(mensajeAccion(new ApiError(409, 'CONFLICTO', 'Ya existe un cliente con ese correo.'), 'respaldo'))
      .toBe('Ya existe un cliente con ese correo.');
  });

  it('cualquier otra cosa usa el respaldo', () => {
    expect(mensajeAccion(new Error('boom'), 'respaldo')).toBe('respaldo');
  });
});

describe('errorDeFormulario', () => {
  const CONOCIDOS = ['nombre', 'correo'];

  it('reparte los campos conocidos (con la clave normalizada) y no repite el mensaje general', () => {
    const e = new ApiError(400, 'VALIDACION', 'Hay datos inválidos en la petición.', { 'cliente.correo': 'Correo inválido.' });
    expect(errorDeFormulario(e, CONOCIDOS, 'respaldo')).toEqual({ campos: { correo: 'Correo inválido.' }, general: '' });
  });

  it('sin campos, el mensaje de la API es el error general', () => {
    const e = new ApiError(400, 'VALIDACION', 'La clave es demasiado corta.');
    expect(errorDeFormulario(e, CONOCIDOS, 'respaldo')).toEqual({ campos: {}, general: 'La clave es demasiado corta.' });
  });

  it('los campos que el formulario no tiene van al error general para que no se pierdan', () => {
    const e = new ApiError(400, 'VALIDACION', 'x', { nombre: 'Escribe el nombre.', 'ventanas[0].fin': 'La hora final debe ser posterior.' });
    expect(errorDeFormulario(e, CONOCIDOS, 'respaldo')).toEqual({
      campos: { nombre: 'Escribe el nombre.' },
      general: 'La hora final debe ser posterior.',
    });
  });

  it('un fallo que no es de la API usa el respaldo como error general', () => {
    expect(errorDeFormulario(new Error('boom'), CONOCIDOS, 'respaldo')).toEqual({ campos: {}, general: 'respaldo' });
  });
});
