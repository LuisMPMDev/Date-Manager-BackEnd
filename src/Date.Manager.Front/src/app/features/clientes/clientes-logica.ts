import { Cita, Cliente } from '../../core/models';
import { esCorreoValido } from '../../core/validaciones';

/** Minúsculas, sin tildes y sin espacios sobrantes: para comparar texto escrito por personas. */
export function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim().replace(/\s+/g, ' ');
}

export function filtrarClientes(clientes: Cliente[], consulta: string): Cliente[] {
  const buscado = normalizar(consulta);
  if (!buscado) return clientes;
  return clientes.filter(c => normalizar(`${c.nombre} ${c.apellido} ${c.correo} ${c.telefono}`).includes(buscado));
}


/** Mensaje de error en español si el cliente no se puede guardar; null si es válido. */
export function validarCliente(c: Cliente, todos: Cliente[]): string | null {
  if (!c.nombre.trim()) return 'Escribe el nombre.';
  if (!c.apellido.trim()) return 'Escribe el apellido.';
  if (!c.telefono.trim()) return 'Escribe el teléfono.';
  const correo = c.correo.trim();
  if (!correo) return 'Escribe el correo.';
  if (!esCorreoValido(correo)) return 'Escribe un correo válido, por ejemplo nombre@correo.com.';
  const repetido = todos.some(o => o.id !== c.id && o.correo.trim().toLowerCase() === correo.toLowerCase());
  return repetido ? 'Ya existe otro cliente con ese correo.' : null;
}

/** Citas no canceladas por cliente. */
export function contarCitas(citas: Cita[]): Map<string, number> {
  const cuentas = new Map<string, number>();
  for (const c of citas) {
    if (c.estado !== 'Cancelada') cuentas.set(c.clienteId, (cuentas.get(c.clienteId) ?? 0) + 1);
  }
  return cuentas;
}
