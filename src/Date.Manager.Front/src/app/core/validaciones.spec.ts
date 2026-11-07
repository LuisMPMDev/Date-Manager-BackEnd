import { Servicio } from './models';
import { sembrar } from './seed';
import { esCorreoValido, validarServicio } from './validaciones';

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

  describe('ventanas solapadas', () => {
    const con = (...tramos: [number, string, string][]) =>
      validarServicio({
        ...valido,
        ventanas: tramos.map(([dia, inicio, fin], i) => ({ id: `v${i}`, dia: dia as 1, inicio, fin })),
      });

    it('rechaza dos ventanas que se cruzan el mismo día, nombrando el día y las horas', () => {
      const mensaje = con([2, '09:00', '12:00'], [2, '11:00', '14:00']);
      expect(mensaje).toContain('Martes');
      expect(mensaje).toContain('09:00');
      expect(mensaje).toContain('14:00');
      expect(mensaje).toMatch(/se cruzan|solap/);
    });

    it('rechaza una ventana contenida dentro de otra, aunque estén en otro orden', () => {
      expect(con([3, '10:00', '11:00'], [3, '08:00', '13:00'])).toBeTruthy();
    });

    it('rechaza ventanas idénticas repetidas', () => {
      expect(con([1, '09:00', '12:00'], [1, '09:00', '12:00'])).toBeTruthy();
    });

    it('permite ventanas que se tocan (fin = inicio) o que están en días distintos', () => {
      expect(con([2, '09:00', '12:00'], [2, '12:00', '15:00'])).toBeNull();
      expect(con([2, '09:00', '12:00'], [3, '10:00', '13:00'])).toBeNull();
      expect(con([2, '15:00', '18:00'], [2, '09:00', '13:00'])).toBeNull();
    });

    it('los datos de ejemplo pasan la validación', () => {
      for (const s of sembrar(new Date(2026, 10, 9, 8, 0)).servicios) expect(validarServicio(s), s.nombre).toBeNull();
    });
  });

  it('el mensaje de una ventana inválida nombra su día', () => {
    const mensaje = validarServicio({ ...valido, ventanas: [{ id: 'v1', dia: 3, inicio: '10:00', fin: '09:00' }] });
    expect(mensaje).toContain('Miércoles');
  });
});

describe('esCorreoValido', () => {
  it('acepta direcciones normales, con espacios alrededor', () => {
    for (const c of ['nombre@correo.com', 'a.b+c@sub.dominio.co', ' ana@correo.com ']) expect(esCorreoValido(c), c).toBe(true);
  });

  it('rechaza lo que no tiene forma de correo', () => {
    for (const c of ['', 'sin-arroba', 'a@b', 'a@b.', 'a@.com', 'a@b..c', 'a b@c.com', 'a@@c.com', '@c.com']) {
      expect(esCorreoValido(c), c).toBe(false);
    }
  });
});
