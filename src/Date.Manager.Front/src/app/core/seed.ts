import { aMinutos, horariosLibres, sumarMinutos } from './horarios';
import { Cita, Cliente, Datos, DiaSemana, EstadoCita, Rol, Servicio, Usuario, VentanaDisponibilidad } from './models';

const HORA_MS = 60 * 60 * 1000;
const DIA_MS = 24 * HORA_MS;

function fechaISO(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

function medianoche(fecha: string): Date {
  const [y, m, d] = fecha.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function desplazar(ahora: Date, dias: number): string {
  return fechaISO(new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate() + dias));
}

function ventanas(servicioId: string, dias: DiaSemana[], tramos: [string, string][]): VentanaDisponibilidad[] {
  const resultado: VentanaDisponibilidad[] = [];
  for (const dia of dias) {
    tramos.forEach(([inicio, fin], i) => resultado.push({ id: `${servicioId}-d${dia}-${i + 1}`, dia, inicio, fin }));
  }
  return resultado;
}

function crearServicios(): Servicio[] {
  return [
    {
      id: 'srv-consulta', nombre: 'Consulta inicial', duracionMin: 30, activo: true, valor: 40000,
      ventanas: ventanas('srv-consulta', [1, 2, 3, 4, 5], [['09:00', '13:00'], ['15:00', '18:00']]),
    },
    {
      id: 'srv-estandar', nombre: 'Sesión estándar', duracionMin: 60, activo: true, valor: 80000,
      ventanas: ventanas('srv-estandar', [1, 2, 3, 4, 5, 6], [['08:00', '12:00'], ['14:00', '17:00']]),
    },
    {
      id: 'srv-extendida', nombre: 'Sesión extendida', duracionMin: 90, activo: true, valor: 110000,
      ventanas: [
        ...ventanas('srv-extendida', [2, 4], [['09:00', '13:00']]),
        ...ventanas('srv-extendida', [5], [['14:00', '18:00']]),
      ],
    },
  ];
}

const CLIENTES: Cliente[] = [
  { id: 'cli-1', nombre: 'Camila', apellido: 'Rojas', telefono: '300 555 0101', correo: 'camila@correo.com' },
  { id: 'cli-2', nombre: 'Daniel', apellido: 'Ortiz', telefono: '300 555 0102', correo: 'daniel@correo.com' },
  { id: 'cli-3', nombre: 'Valentina', apellido: 'Mejía', telefono: '300 555 0103', correo: 'valentina@correo.com' },
  { id: 'cli-4', nombre: 'Sebastián', apellido: 'Cruz', telefono: '300 555 0104', correo: 'sebastian@correo.com' },
  { id: 'cli-5', nombre: 'Isabela', apellido: 'Torres', telefono: '300 555 0105', correo: 'isabela@correo.com' },
  { id: 'cli-6', nombre: 'Mateo', apellido: 'Herrera', telefono: '300 555 0106', correo: 'mateo@correo.com' },
  { id: 'cli-7', nombre: 'Lucía', apellido: 'Vargas', telefono: '300 555 0107', correo: 'lucia@correo.com' },
  { id: 'cli-8', nombre: 'Julián', apellido: 'Peña', telefono: '300 555 0108', correo: 'julian@correo.com' },
];

const ROLES: Rol[] = [
  {
    id: 'rol-admin', nombre: 'Administrador', descripcion: 'Acceso completo al sistema',
    permisos: [
      'ver_panel', 'ver_citas', 'gestionar_citas', 'atender_citas',
      'gestionar_servicios', 'gestionar_clientes', 'gestionar_usuarios', 'gestionar_roles',
    ],
  },
  {
    id: 'rol-recepcion', nombre: 'Recepción', descripcion: 'Atiende la agenda y a los clientes',
    permisos: ['ver_citas', 'gestionar_citas', 'atender_citas', 'gestionar_clientes'],
  },
];

const USUARIOS: Usuario[] = [
  {
    id: 'usr-marta', nombre: 'Marta', apellido: 'Rivas', correo: 'marta@datemanager.demo',
    telefono: '300 555 0201', usuario: 'marta', clave: 'demo123', rolId: 'rol-admin',
  },
  {
    id: 'usr-andres', nombre: 'Andrés', apellido: 'Pardo', correo: 'andres@datemanager.demo',
    telefono: '300 555 0202', usuario: 'andres', clave: 'demo123', rolId: 'rol-recepcion',
  },
];

interface PlanCita {
  dia: number; // desplazamiento en días respecto a "hoy"
  servicio: number; // índice en la lista de servicios
  hueco: number; // n-ésimo horario libre de ese día
  cliente: number; // índice en CLIENTES
  estado: EstadoCita;
  creadaHaceH?: number; // horas antes de "ahora"
  canceladaHaceH?: number;
  /** Solo para «hoy»: ya terminada a la hora actual, o por venir. */
  momento?: 'pasada' | 'proxima';
}

const PLAN: PlanCita[] = [
  // Pasado reciente (últimas tres semanas)
  { dia: -19, servicio: 1, hueco: 0, cliente: 0, estado: 'Atendida' },
  { dia: -18, servicio: 0, hueco: 2, cliente: 1, estado: 'Atendida' },
  { dia: -15, servicio: 2, hueco: 0, cliente: 2, estado: 'Atendida' },
  { dia: -13, servicio: 1, hueco: 1, cliente: 3, estado: 'Atendida' },
  { dia: -10, servicio: 0, hueco: 0, cliente: 4, estado: 'Atendida' },
  { dia: -8, servicio: 1, hueco: 2, cliente: 5, estado: 'Atendida' },
  { dia: -6, servicio: 0, hueco: 1, cliente: 6, estado: 'Cancelada' },
  { dia: -4, servicio: 2, hueco: 1, cliente: 7, estado: 'Atendida' },
  { dia: -3, servicio: 0, hueco: 3, cliente: 0, estado: 'Atendida' },
  { dia: -2, servicio: 1, hueco: 0, cliente: 1, estado: 'Atendida' },
  { dia: -1, servicio: 0, hueco: 1, cliente: 2, estado: 'Atendida' },
  // Hoy
  // Se ubican respecto a la hora actual: una ya atendida (si hoy ya pasó algún horario) y otra por venir.
  { dia: 0, servicio: 1, hueco: 2, cliente: 4, estado: 'Atendida', momento: 'pasada' },
  { dia: 0, servicio: 0, hueco: 1, cliente: 3, estado: 'Confirmada', momento: 'proxima' },
  // Próximos diez días
  { dia: 1, servicio: 0, hueco: 0, cliente: 5, estado: 'Confirmada' },
  { dia: 1, servicio: 1, hueco: 4, cliente: 6, estado: 'Cancelada', creadaHaceH: 72, canceladaHaceH: 5 },
  { dia: 2, servicio: 2, hueco: 0, cliente: 7, estado: 'Confirmada', creadaHaceH: 2 },
  { dia: 3, servicio: 0, hueco: 2, cliente: 0, estado: 'Confirmada', creadaHaceH: 20 },
  { dia: 4, servicio: 1, hueco: 1, cliente: 1, estado: 'Confirmada' },
  { dia: 5, servicio: 0, hueco: 5, cliente: 3, estado: 'Confirmada' },
  { dia: 6, servicio: 2, hueco: 1, cliente: 2, estado: 'Confirmada' },
  { dia: 8, servicio: 1, hueco: 0, cliente: 4, estado: 'Confirmada' },
  { dia: 9, servicio: 0, hueco: 1, cliente: 5, estado: 'Confirmada' },
  { dia: 10, servicio: 1, hueco: 2, cliente: 6, estado: 'Confirmada' },
];

/**
 * Busca el n-ésimo horario libre del día indicado; si ese día el servicio no atiende,
 * avanza (o retrocede, en el pasado) hasta encontrar uno. Todas las citas cuentan como
 * ocupadas, incluso las canceladas, para que ninguna se cruce con otra.
 * Para «hoy» se tiene en cuenta la hora actual: `pasada` busca un horario que ya terminó
 * (devuelve null si todavía no hay ninguno) y `proxima` uno que aún no empezó; si hoy ya no
 * queda ninguno, pasa al siguiente día hábil en vez de quedar en el pasado.
 */
function ubicar(
  servicio: Servicio, dia: number, hueco: number, existentes: Cita[], ahora: Date, momento?: 'pasada' | 'proxima',
): { fecha: string; inicio: string } | null {
  const paso = dia < 0 ? -1 : 1;
  const ocupadas = existentes.map(c => ({ ...c, estado: 'Confirmada' as EstadoCita }));
  const minutosAhora = ahora.getHours() * 60 + ahora.getMinutes();
  for (let i = 0, d = dia; i < 8; i++, d += paso) {
    const fecha = desplazar(ahora, d);
    const esHoy = d === 0 && momento !== undefined;
    if (esHoy && momento === 'pasada') {
      const terminados = horariosLibres({ servicio, fecha, citas: ocupadas, ahora: medianoche(fecha) }).filter(
        h => aMinutos(h) + servicio.duracionMin <= minutosAhora,
      );
      // el más reciente: «se atendió hace un rato»
      return terminados.length ? { fecha, inicio: terminados[terminados.length - 1] } : null;
    }
    const libres = horariosLibres({ servicio, fecha, citas: ocupadas, ahora: esHoy ? ahora : medianoche(fecha) });
    if (libres.length) return { fecha, inicio: libres[hueco % libres.length] };
  }
  throw new Error(`Sin horarios de ejemplo para ${servicio.nombre}`);
}

/** Datos de ejemplo, relativos a `ahora`, listos para la demostración. */
export function sembrar(ahora: Date): Datos {
  const servicios = crearServicios();
  const citas: Cita[] = [];

  for (const planBase of PLAN) {
    const servicio = servicios[planBase.servicio];
    let plan = planBase;
    let sitio = ubicar(servicio, plan.dia, plan.hueco, citas, ahora, plan.momento);
    if (!sitio) {
      // Todavía no ha pasado ningún horario hoy: en vez de «ya atendida» queda por venir.
      plan = { ...plan, estado: 'Confirmada', momento: 'proxima' };
      sitio = ubicar(servicio, plan.dia, plan.hueco, citas, ahora, plan.momento)!;
    }
    const { fecha, inicio } = sitio;
    // Si hoy ya no hay horarios y la cita pasó a otro día, no puede figurar como atendida.
    if (plan.estado === 'Atendida' && fecha > fechaISO(ahora)) plan = { ...plan, estado: 'Confirmada' };
    const creada =
      plan.creadaHaceH !== undefined
        ? ahora.getTime() - plan.creadaHaceH * HORA_MS
        : Math.min(medianoche(fecha).getTime() - 3 * DIA_MS, ahora.getTime() - 2 * DIA_MS);
    const cita: Cita = {
      id: `cita-${String(citas.length + 1).padStart(2, '0')}`,
      servicioId: servicio.id,
      clienteId: CLIENTES[plan.cliente].id,
      fecha,
      inicio,
      fin: sumarMinutos(inicio, servicio.duracionMin),
      estado: plan.estado,
      creadaEn: new Date(creada).toISOString(),
    };
    if (plan.estado === 'Cancelada') {
      const cancelada =
        plan.canceladaHaceH !== undefined ? ahora.getTime() - plan.canceladaHaceH * HORA_MS : creada + HORA_MS;
      cita.canceladaEn = new Date(cancelada).toISOString();
    }
    citas.push(cita);
  }

  return {
    servicios,
    clientes: CLIENTES.map(c => ({ ...c })),
    citas,
    roles: ROLES.map(r => ({ ...r, permisos: [...r.permisos] })),
    usuarios: USUARIOS.map(u => ({ ...u })),
  };
}
