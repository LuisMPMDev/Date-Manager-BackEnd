import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgendaRepo } from '../core/agenda-repo';
import { DemoAgendaRepo } from '../core/demo-agenda-repo';
import { RELOJ } from '../core/errores';
import { AgendaDiaComponent } from './agenda-dia';

const LUNES = '2026-11-09';
const MARTES = '2026-11-10';
const SABADO = '2026-11-14';
const DOMINGO = '2026-11-15';

describe('AgendaDiaComponent', () => {
  let ahora: Date;

  beforeEach(async () => {
    localStorage.clear();
    ahora = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [AgendaDiaComponent],
      providers: [
        { provide: RELOJ, useValue: () => ahora },
        { provide: AgendaRepo, useClass: DemoAgendaRepo },
      ],
    }).compileComponents();
  });

  async function esperar(fixture: ComponentFixture<unknown>) {
    await new Promise(r => setTimeout(r));
    fixture.detectChanges();
  }

  async function crear(fecha: string) {
    const fixture = TestBed.createComponent(AgendaDiaComponent);
    fixture.componentRef.setInput('fecha', fecha);
    fixture.detectChanges();
    await esperar(fixture);
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const columnas = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLElement>('[data-testid="columna"]'));
  const bloques = (el: HTMLElement | Element, tipo: string) =>
    Array.from(el.querySelectorAll<HTMLElement>('[data-testid="bloque"]')).filter(b => b.classList.contains(tipo));
  const encabezados = (el: HTMLElement) => columnas(el).map(c => c.querySelector('h3')!.textContent?.trim());

  const repo = () => TestBed.inject(AgendaRepo);
  const citasDe = async (fecha: string) => (await repo().citas()).filter(c => c.fecha === fecha);

  it('dibuja una columna por servicio activo, con su nombre', async () => {
    const { el } = await crear(LUNES);
    expect(encabezados(el)).toEqual(['Consulta inicial', 'Sesión estándar', 'Sesión extendida']);
  });

  it('no dibuja una columna para un servicio inactivo', async () => {
    const extendida = (await repo().servicios()).find(s => s.id === 'srv-extendida')!;
    await repo().guardarServicio({ ...extendida, activo: false });
    const { el } = await crear(LUNES);
    expect(encabezados(el)).toEqual(['Consulta inicial', 'Sesión estándar']);
  });

  it('las citas confirmadas son bloques de cita con «Hora · Nombre A.»', async () => {
    const hoy = (await citasDe(LUNES)).filter(c => c.estado === 'Confirmada');
    expect(hoy.length).toBeGreaterThan(0);
    const clientes = await repo().clientes();
    const { el } = await crear(LUNES);

    const etiquetas = bloques(el, 'cita').map(b => b.textContent?.trim());
    const esperadas = hoy.map(c => {
      const cli = clientes.find(x => x.id === c.clienteId)!;
      return `${c.inicio} · ${cli.nombre} ${cli.apellido[0]}.`;
    });
    expect(etiquetas.sort()).toEqual(esperadas.sort());
  });

  it('una cita atendida se dibuja en gris y una cancelada no se dibuja', async () => {
    const [primera, segunda] = (await citasDe(LUNES)).filter(c => c.estado === 'Confirmada');
    const { fixture, el } = await crear(LUNES);
    const total = bloques(el, 'cita').length;

    await repo().marcarAtendida(primera.id);
    await esperar(fixture);
    expect(bloques(el, 'atendida').length).toBe(1);
    expect(bloques(el, 'cita').length).toBe(total - 1);

    await repo().cancelarCita(segunda.id);
    await esperar(fixture);
    expect(bloques(el, 'cita').length).toBe(total - 2);
    expect(bloques(el, 'atendida').length).toBe(1);
  });

  it('una cita cuya hora de fin ya pasó se dibuja en gris', async () => {
    ahora = new Date(2026, 10, 9, 18, 0); // pasó todo el horario de hoy
    const confirmadas = (await citasDe(LUNES)).filter(c => c.estado === 'Confirmada');
    const { el } = await crear(LUNES);
    expect(bloques(el, 'cita').length).toBe(0);
    expect(bloques(el, 'atendida').length).toBe(confirmadas.length);
  });

  it('los días pasados se dibujan en gris', async () => {
    const pasada = (await repo().citas()).find(c => c.fecha < LUNES && c.estado === 'Atendida')!;
    const { el } = await crear(pasada.fecha);
    expect(bloques(el, 'atendida').length).toBeGreaterThan(0);
    expect(bloques(el, 'cita').length).toBe(0);
    expect(el.querySelector('.ahora')).toBeNull();
  });

  it('la línea de «ahora» aparece solo hoy', async () => {
    const { fixture, el } = await crear(LUNES);
    expect(el.querySelectorAll('.ahora').length).toBe(columnas(el).length);

    fixture.componentRef.setInput('fecha', MARTES);
    await esperar(fixture);
    expect(el.querySelector('.ahora')).toBeNull();
  });

  it('todas las columnas comparten el mismo rango de horas', async () => {
    const { el } = await crear(LUNES);
    const alturas = columnas(el).map(c => (c.querySelector('.regla') as HTMLElement).style.height);
    expect(alturas.length).toBe(3);
    expect(new Set(alturas).size).toBe(1);
    expect(alturas[0]).not.toBe('');
  });

  it('lo que queda fuera de la ventana de un servicio va rayado', async () => {
    const { el } = await crear(SABADO); // el sábado solo atiende la sesión estándar
    const [consulta, estandar] = columnas(el);
    expect(bloques(consulta, 'fuera').length).toBe(1);
    expect(consulta.querySelectorAll('[data-testid="bloque"]').length).toBe(1);
    expect(bloques(estandar, 'fuera').length).toBeGreaterThan(0);
  });

  it('se recalcula al cambiar la fecha', async () => {
    const { fixture, el } = await crear(LUNES);
    const antes = bloques(el, 'cita').length;
    expect(antes).toBeGreaterThan(0);

    fixture.componentRef.setInput('fecha', SABADO);
    await esperar(fixture);
    expect(bloques(el, 'cita').length).toBe((await citasDe(SABADO)).filter(c => c.estado === 'Confirmada').length);
  });

  it('un día sin ventanas en ningún servicio muestra el aviso', async () => {
    const { el } = await crear(DOMINGO);
    expect(columnas(el).length).toBe(0);
    expect(el.textContent).toContain('Ningún servicio atiende este día.');
  });

  it('con todos los servicios inactivos muestra el aviso', async () => {
    for (const s of await repo().servicios()) await repo().guardarServicio({ ...s, activo: false });
    const { el } = await crear(LUNES);
    expect(el.textContent).toContain('Ningún servicio atiende este día.');
  });

  it('no muestra el aviso en un día con servicio', async () => {
    const { el } = await crear(LUNES);
    expect(el.textContent).not.toContain('Ningún servicio atiende este día.');
  });
});
