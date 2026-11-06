import { Cita, Cliente } from '../../core/models';
import { contarCitas, filtrarClientes, normalizar, validarCliente } from './clientes-logica';

const cliente = (id: string, nombre: string, apellido: string, telefono: string, correo: string): Cliente =>
  ({ id, nombre, apellido, telefono, correo });

const CLIENTES = [
  cliente('1', 'Sebastián', 'Cruz', '300 555 0104', 'sebastian@correo.com'),
  cliente('2', 'Lucía', 'Vargas', '300 555 0107', 'lucia@correo.com'),
  cliente('3', 'Camila', 'Rojas', '311 222 3344', 'Camila.Rojas@Correo.com'),
];

describe('normalizar', () => {
  it('pasa a minúsculas, quita tildes y recorta', () => {
    expect(normalizar('  SebastiÁN  ')).toBe('sebastian');
    expect(normalizar('Peña')).toBe('pena');
  });
});

describe('filtrarClientes', () => {
  it('sin texto devuelve todos', () => {
    expect(filtrarClientes(CLIENTES, '')).toEqual(CLIENTES);
    expect(filtrarClientes(CLIENTES, '   ')).toEqual(CLIENTES);
  });

  it('busca en nombre, apellido, correo y teléfono sin importar mayúsculas ni tildes', () => {
    expect(filtrarClientes(CLIENTES, 'SEBASTIAN').map(c => c.id)).toEqual(['1']);
    expect(filtrarClientes(CLIENTES, 'vargas').map(c => c.id)).toEqual(['2']);
    expect(filtrarClientes(CLIENTES, 'camila.rojas@').map(c => c.id)).toEqual(['3']);
    expect(filtrarClientes(CLIENTES, '0107').map(c => c.id)).toEqual(['2']);
    expect(filtrarClientes(CLIENTES, 'lucía').map(c => c.id)).toEqual(['2']);
  });

  it('recorta el texto buscado y admite nombre y apellido juntos', () => {
    expect(filtrarClientes(CLIENTES, '  camila rojas ').map(c => c.id)).toEqual(['3']);
  });

  it('sin coincidencias devuelve una lista vacía', () => {
    expect(filtrarClientes(CLIENTES, 'zzz')).toEqual([]);
  });
});

describe('validarCliente', () => {
  const valido = cliente('1', 'Sebastián', 'Cruz', '300 555 0104', 'sebastian@correo.com');

  it('acepta un cliente completo', () => {
    expect(validarCliente(valido, CLIENTES)).toBeNull();
  });

  it('pide cada campo obligatorio', () => {
    expect(validarCliente({ ...valido, nombre: ' ' }, CLIENTES)).toBe('Escribe el nombre.');
    expect(validarCliente({ ...valido, apellido: '' }, CLIENTES)).toBe('Escribe el apellido.');
    expect(validarCliente({ ...valido, telefono: '' }, CLIENTES)).toBe('Escribe el teléfono.');
    expect(validarCliente({ ...valido, correo: '' }, CLIENTES)).toBe('Escribe el correo.');
  });

  it('el correo necesita @ y un punto después', () => {
    const mensaje = 'Escribe un correo válido, por ejemplo nombre@correo.com.';
    for (const correo of ['abc', 'a@b', 'a@.com', '@correo.com', 'a b@correo.com', 'a@b.']) {
      expect(validarCliente({ ...valido, correo }, CLIENTES), correo).toBe(mensaje);
    }
    expect(validarCliente({ ...valido, correo: ' otro@correo.co ' }, CLIENTES)).toBeNull();
  });

  it('el correo no puede ser el de otro cliente, sin importar mayúsculas', () => {
    const mensaje = 'Ya existe otro cliente con ese correo.';
    expect(validarCliente({ ...valido, correo: 'LUCIA@correo.com' }, CLIENTES)).toBe(mensaje);
    expect(validarCliente({ ...valido, correo: ' lucia@correo.com ' }, CLIENTES)).toBe(mensaje);
  });

  it('conservar o cambiar las mayúsculas del propio correo está bien', () => {
    expect(validarCliente({ ...valido, correo: 'Sebastian@Correo.com' }, CLIENTES)).toBeNull();
  });
});

describe('contarCitas', () => {
  const cita = (clienteId: string, estado: Cita['estado']): Cita => ({
    id: Math.random().toString(), servicioId: 's', clienteId, fecha: '2026-11-09', inicio: '09:00', fin: '10:00',
    estado, creadaEn: '2026-01-01T00:00:00.000Z',
  });

  it('cuenta por cliente las citas que no están canceladas', () => {
    const cuentas = contarCitas([cita('1', 'Confirmada'), cita('1', 'Atendida'), cita('1', 'Cancelada'), cita('2', 'Cancelada')]);
    expect(cuentas.get('1')).toBe(2);
    expect(cuentas.get('2') ?? 0).toBe(0);
    expect(cuentas.get('3') ?? 0).toBe(0);
  });
});
