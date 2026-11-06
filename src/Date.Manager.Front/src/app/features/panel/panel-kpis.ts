import { aMinutos, isoDia } from '../../core/horarios';
import { ingresosEstimados, ocupacion, variacion } from '../../core/metricas';
import { Cita, Servicio } from '../../core/models';
import { aFechaISO, DIAS, fechaLarga, moneda, sumarDias } from '../../shared/formato';

export type Tono = 'ok' | 'mal' | 'neutro';

export interface Kpi {
  id: 'citas-hoy' | 'proxima' | 'ocupacion' | 'nuevas' | 'canceladas' | 'ingresos';
  etiqueta: string;
  valor: string;
  detalle: string;
  tono: Tono;
}

const DIA_MS = 24 * 60 * 60 * 1000;

function minutosDelDia(ahora: Date): number {
  return ahora.getHours() * 60 + ahora.getMinutes();
}

/** Citas confirmadas que aún no empiezan (una que empieza ahora mismo cuenta), de la más cercana a la más lejana. */
export function proximasCitas(citas: Cita[], ahora: Date, limite = Infinity): Cita[] {
  const desde = `${aFechaISO(ahora)} ${String(ahora.getHours()).padStart(2, '0')}:${String(ahora.getMinutes()).padStart(2, '0')}`;
  return citas
    .filter(c => c.estado === 'Confirmada' && `${c.fecha} ${c.inicio}` >= desde)
    .sort((a, b) => a.fecha.localeCompare(b.fecha) || a.inicio.localeCompare(b.inicio))
    .slice(0, limite);
}

/**
 * Texto de comparación con ▲/▼. `subeEsBueno` decide el tono: más ingresos es bueno, más cancelaciones es malo.
 */
function comparar(actual: number, previo: number, contra: string, subeEsBueno: boolean): Pick<Kpi, 'detalle' | 'tono'> {
  const v = variacion(actual, previo);
  if (v === null) return { detalle: 'Sin dato previo', tono: 'neutro' };
  const pct = Math.round(Math.abs(v) * 100);
  if (pct === 0) return { detalle: `Sin cambios vs. ${contra}`, tono: 'neutro' };
  const sube = v > 0;
  return { detalle: `${sube ? '▲' : '▼'} ${pct}% vs. ${contra}`, tono: sube === subeEsBueno ? 'ok' : 'mal' };
}

/** Cuántas citas tienen la marca de tiempo en (ahora - hasta, ahora - desde] días. */
function contarEnVentana(marcas: (string | undefined)[], ahora: Date, desdeDias: number, hastaDias: number): number {
  const fin = ahora.getTime() - desdeDias * DIA_MS;
  const inicio = ahora.getTime() - hastaDias * DIA_MS;
  return marcas.filter(m => {
    if (!m) return false;
    const t = new Date(m).getTime();
    return t > inicio && t <= fin;
  }).length;
}

function kpiProxima(servicios: Servicio[], citas: Cita[], ahora: Date): Kpi {
  const base = { id: 'proxima', etiqueta: 'Próxima cita' } as const;
  const [proxima] = proximasCitas(citas, ahora, 1);
  if (!proxima) return { ...base, valor: '—', detalle: 'Sin citas próximas', tono: 'neutro' };

  const nombre = servicios.find(s => s.id === proxima.servicioId)?.nombre ?? 'Servicio';
  const cuando = proxima.fecha === aFechaISO(ahora)
    ? `en ${aMinutos(proxima.inicio) - minutosDelDia(ahora)} min`
    : fechaLarga(proxima.fecha);
  return { ...base, valor: proxima.inicio, detalle: `${cuando} · ${nombre}`, tono: 'neutro' };
}

export function calcularKpis(entrada: { servicios: Servicio[]; citas: Cita[]; ahora: Date }): Kpi[] {
  const { servicios, citas, ahora } = entrada;
  const hoy = aFechaISO(ahora);

  const deHoy = citas.filter(c => c.fecha === hoy && c.estado !== 'Cancelada');
  const atendidas = deHoy.filter(c => c.estado === 'Atendida').length;

  const ocupacionHoy = ocupacion(servicios, citas, hoy);
  const ocupacionAntes = ocupacion(servicios, citas, sumarDias(hoy, -7));
  const diaPasado = `${DIAS[isoDia(hoy) - 1].toLowerCase()} pasado`;

  const nuevas = contarEnVentana(citas.map(c => c.creadaEn), ahora, 0, 30);
  const nuevasAntes = contarEnVentana(citas.map(c => c.creadaEn), ahora, 30, 60);
  const canceladas = contarEnVentana(citas.map(c => c.canceladaEn), ahora, 0, 30);
  const canceladasAntes = contarEnVentana(citas.map(c => c.canceladaEn), ahora, 30, 60);

  const ingresos = ingresosEstimados(servicios, citas, sumarDias(hoy, -29), hoy);
  const ingresosAntes = ingresosEstimados(servicios, citas, sumarDias(hoy, -59), sumarDias(hoy, -30));

  return [
    {
      id: 'citas-hoy', etiqueta: 'Citas hoy', valor: String(deHoy.length), tono: 'neutro',
      detalle: `${atendidas} ya ${atendidas === 1 ? 'atendida' : 'atendidas'}`,
    },
    kpiProxima(servicios, citas, ahora),
    {
      id: 'ocupacion', etiqueta: 'Ocupación de hoy', valor: `${Math.round(ocupacionHoy * 100)}%`,
      ...comparar(ocupacionHoy, ocupacionAntes, diaPasado, true),
    },
    {
      id: 'nuevas', etiqueta: 'Citas nuevas (30 días)', valor: String(nuevas),
      ...comparar(nuevas, nuevasAntes, '30 días antes', true),
    },
    {
      id: 'canceladas', etiqueta: 'Canceladas (30 días)', valor: String(canceladas),
      ...comparar(canceladas, canceladasAntes, '30 días antes', false),
    },
    {
      id: 'ingresos', etiqueta: 'Ingresos estimados', valor: moneda(ingresos),
      ...comparar(ingresos, ingresosAntes, '30 días antes', true),
    },
  ];
}
