export type DiaSemana = 1 | 2 | 3 | 4 | 5 | 6 | 7; // ISO: 1 = lunes … 7 = domingo
export type EstadoCita = 'Confirmada' | 'Atendida' | 'Cancelada';
export type Permiso = 'ver_panel' | 'ver_citas' | 'gestionar_citas' | 'atender_citas'
  | 'gestionar_servicios' | 'gestionar_clientes' | 'gestionar_usuarios' | 'gestionar_roles' | 'gestionar_enlaces';
export interface VentanaDisponibilidad { id: string; dia: DiaSemana; inicio: string; fin: string } // 'HH:mm'
export interface Servicio { id: string; nombre: string; duracionMin: number; activo: boolean; valor: number; ventanas: VentanaDisponibilidad[] }
export interface Cliente { id: string; nombre: string; apellido: string; telefono: string; correo: string }
export interface Cita { id: string; servicioId: string; clienteId: string; fecha: string /* 'YYYY-MM-DD' */; inicio: string; fin: string; estado: EstadoCita; creadaEn: string /* ISO */; canceladaEn?: string }
export interface Rol { id: string; nombre: string; descripcion: string; permisos: Permiso[] }
export interface Usuario { id: string; nombre: string; apellido: string; correo: string; telefono: string; usuario: string; rolId: string }
/** Usuario tal como se llena en la pantalla de alta o edición: solo aquí viaja la clave (vacía al editar = conservar). */
export type UsuarioForm = Usuario & { clave?: string };
export interface Datos { servicios: Servicio[]; clientes: Cliente[]; citas: Cita[]; roles: Rol[]; usuarios: Usuario[] }
