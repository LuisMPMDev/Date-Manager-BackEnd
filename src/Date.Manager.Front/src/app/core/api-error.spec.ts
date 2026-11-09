import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './api-error';

function respuesta(status: number, error: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ status, error, url: 'http://localhost:8080/api/panel/citas' });
}

describe('ApiError.desde', () => {
  it('convierte el cuerpo {codigo, mensaje} de la API conservando el status', () => {
    const e = ApiError.desde(respuesta(409, { codigo: 'HORARIO_NO_DISPONIBLE', mensaje: 'Ese horario ya no está disponible.' }));
    expect(e).toBeInstanceOf(ApiError);
    expect(e).toBeInstanceOf(Error);
    expect(e.status).toBe(409);
    expect(e.codigo).toBe('HORARIO_NO_DISPONIBLE');
    expect(e.message).toBe('Ese horario ya no está disponible.');
    expect(e.campos).toEqual({});
  });

  it('conserva los campos de un 400 de validación', () => {
    const e = ApiError.desde(respuesta(400, {
      codigo: 'VALIDACION', mensaje: 'Datos inválidos', campos: { correo: 'Correo inválido', 'cliente.telefono': 'Obligatorio' },
    }));
    expect(e.status).toBe(400);
    expect(e.codigo).toBe('VALIDACION');
    expect(e.campos).toEqual({ correo: 'Correo inválido', 'cliente.telefono': 'Obligatorio' });
  });

  it('un status 0 (sin respuesta) es ERROR_RED con mensaje en español', () => {
    const e = ApiError.desde(respuesta(0, new ProgressEvent('error')));
    expect(e.status).toBe(0);
    expect(e.codigo).toBe('ERROR_RED');
    expect(e.message).toContain('No se pudo contactar al servidor');
    expect(e.campos).toEqual({});
  });

  it('un cuerpo que no es JSON de la API es ERROR_INTERNO con mensaje en español', () => {
    const e = ApiError.desde(respuesta(502, '<html>Bad gateway</html>'));
    expect(e.status).toBe(502);
    expect(e.codigo).toBe('ERROR_INTERNO');
    expect(e.message).toContain('servidor');
    expect(e.message).not.toContain('<html>');
  });

  it('un cuerpo nulo con status de error es ERROR_INTERNO', () => {
    const e = ApiError.desde(respuesta(500, null));
    expect(e.codigo).toBe('ERROR_INTERNO');
  });

  it('ignora valores de campos que no son texto', () => {
    const e = ApiError.desde(respuesta(400, { codigo: 'VALIDACION', mensaje: 'x', campos: { a: 'ok', b: 3 } }));
    expect(e.campos).toEqual({ a: 'ok' });
  });
});
