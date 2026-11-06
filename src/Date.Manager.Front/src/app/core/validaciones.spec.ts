import { Servicio } from './models';
import { validarServicio } from './validaciones';

const valido: Servicio = {
  id: 'srv-x', nombre: 'Masaje', duracionMin: 45, activo: true, valor: 50000,
  ventanas: [{ id: 'v1', dia: 1, inicio: '09:00', fin: '12:00' }],
};

describe('validarServicio', () => {
  it('un servicio válido devuelve null', () => {
    expect(validarServicio(valido)).toBeNull();
  });

  it('un servicio sin ventanas es válido', () => {
    expect(validarServicio({ ...valido, ventanas: [] })).toBeNull();
  });

  it('valor cero es válido', () => {
    expect(validarServicio({ ...valido, valor: 0 })).toBeNull();
  });

  it('rechaza el nombre vacío o en blanco', () => {
    expect(validarServicio({ ...valido, nombre: '' })).toBeTruthy();
    expect(validarServicio({ ...valido, nombre: '   ' })).toBeTruthy();
  });

  it('rechaza duración cero, negativa o no entera', () => {
    expect(validarServicio({ ...valido, duracionMin: 0 })).toBeTruthy();
    expect(validarServicio({ ...valido, duracionMin: -30 })).toBeTruthy();
    expect(validarServicio({ ...valido, duracionMin: 20.5 })).toBeTruthy();
    expect(validarServicio({ ...valido, duracionMin: NaN })).toBeTruthy();
  });

  it('rechaza valor negativo o no numérico', () => {
    expect(validarServicio({ ...valido, valor: -1 })).toBeTruthy();
    expect(validarServicio({ ...valido, valor: NaN })).toBeTruthy();
  });

  it('rechaza una ventana con fin igual o anterior al inicio', () => {
    const con = (inicio: string, fin: string) =>
      validarServicio({ ...valido, ventanas: [{ id: 'v1', dia: 2, inicio, fin }] });
    expect(con('10:00', '10:00')).toBeTruthy();
    expect(con('12:00', '09:00')).toBeTruthy();
  });

  it('rechaza una ventana con horas vacías o mal escritas', () => {
    const con = (inicio: string, fin: string) =>
      validarServicio({ ...valido, ventanas: [{ id: 'v1', dia: 2, inicio, fin }] });
    expect(con('', '10:00')).toBeTruthy();
    expect(con('09:00', '')).toBeTruthy();
    expect(con('9am', '10:00')).toBeTruthy();
    expect(con('25:00', '26:00')).toBeTruthy();
  });

  it('el mensaje de una ventana inválida nombra su día', () => {
    const mensaje = validarServicio({ ...valido, ventanas: [{ id: 'v1', dia: 3, inicio: '10:00', fin: '09:00' }] });
    expect(mensaje).toContain('Miércoles');
  });
});
