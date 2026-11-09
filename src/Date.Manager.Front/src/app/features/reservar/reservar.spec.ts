import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AgendaRepo } from '../../core/agenda-repo';
import { DemoAgendaRepo } from '../../testing/demo-agenda-repo';
import { RELOJ } from '../../core/errores';
import { ReservarComponent } from './reservar';

const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00

describe('ReservarComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ReservarComponent],
      providers: [
        provideRouter([]),
        { provide: RELOJ, useValue: () => AHORA },
        { provide: AgendaRepo, useClass: DemoAgendaRepo },
      ],
    }).compileComponents();
  });

  async function esperar(fixture: ComponentFixture<unknown>) {
    await new Promise(r => setTimeout(r));
    fixture.detectChanges();
  }

  async function crear() {
    const fixture = TestBed.createComponent(ReservarComponent);
    fixture.detectChanges();
    await esperar(fixture);
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const servicios = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLButtonElement>('[data-testid="servicio"]'));
  const huecos = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLButtonElement>('[data-testid="hueco"]'));
  const boton = (el: HTMLElement, texto: string) =>
    Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(b => b.textContent?.trim() === texto)!;

  async function elegirServicio(f: ComponentFixture<unknown>, el: HTMLElement, nombre: string) {
    servicios(el).find(b => b.textContent?.includes(nombre))!.click();
    await esperar(f);
  }

  async function elegirHueco(f: ComponentFixture<unknown>, el: HTMLElement, hora: string) {
    huecos(el).find(b => b.textContent?.trim() === hora)!.click();
    await esperar(f);
  }

  function escribir(el: HTMLElement, nombre: string, valor: string) {
    const input = el.querySelector<HTMLInputElement>(`input[name="${nombre}"]`)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  function llenarDatos(el: HTMLElement) {
    escribir(el, 'nombre', 'Camila');
    escribir(el, 'apellido', 'Rojas');
    escribir(el, 'telefono', '300 123 4567');
    escribir(el, 'correo', 'camila@correo.com');
  }

  async function enviar(f: ComponentFixture<unknown>, el: HTMLElement) {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await esperar(f);
  }

  it('muestra solo servicios activos', async () => {
    const repo = TestBed.inject(AgendaRepo);
    const extendida = (await repo.servicios()).find(s => s.nombre === 'Sesión extendida')!;
    await repo.guardarServicio({ ...extendida, activo: false });
    const { el } = await crear();
    const textos = servicios(el).map(b => b.textContent ?? '');
    expect(textos.length).toBe(2);
    expect(textos[0]).toContain('Consulta inicial');
    expect(textos[0]).toContain('30 min');
    expect(textos[0]).toMatch(/40\D?000/);
    expect(textos[1]).toContain('Sesión estándar');
    expect(textos[1]).toContain('60 min');
    expect(textos[1]).toMatch(/80\D?000/);
    expect(textos.some(t => t.includes('Sesión extendida'))).toBe(false);
  });

  it('muestra el día en español, no deja ir antes de hoy y para a los 30 días', async () => {
    const { fixture, el } = await crear();
    await elegirServicio(fixture, el, 'Sesión estándar');
    const dia = () => el.querySelector('[data-testid="dia"]')!.textContent!.trim();
    const anterior = () => el.querySelector<HTMLButtonElement>('button[aria-label="Día anterior"]')!;
    const siguiente = () => el.querySelector<HTMLButtonElement>('button[aria-label="Día siguiente"]')!;

    expect(dia()).toBe('Lunes 9 de noviembre');
    expect(anterior().disabled).toBe(true);
    siguiente().click();
    await esperar(fixture);
    expect(dia()).toBe('Martes 10 de noviembre');
    expect(anterior().disabled).toBe(false);

    for (let i = 0; i < 29; i++) {
      siguiente().click();
      await esperar(fixture);
    }
    expect(dia()).toBe('Miércoles 9 de diciembre');
    expect(siguiente().disabled).toBe(true);
  });

  it('elegir servicio, día y hora y enviar datos válidos muestra «Cita confirmada» con servicio, fecha, hora y valor', async () => {
    const { fixture, el } = await crear();
    expect(boton(el, 'Continuar').disabled).toBe(true);
    await elegirServicio(fixture, el, 'Sesión estándar');
    el.querySelector<HTMLButtonElement>('button[aria-label="Día siguiente"]')!.click();
    await esperar(fixture);
    await elegirHueco(fixture, el, '10:00');
    expect(el.querySelector('[data-testid="bloque"].elegido')).not.toBeNull();
    expect(boton(el, 'Continuar').disabled).toBe(false);

    boton(el, 'Continuar').click();
    await esperar(fixture);
    expect(el.textContent).toContain('Martes 10 de noviembre, 10:00 – 11:00');
    llenarDatos(el);
    await enviar(fixture, el);

    const texto = el.textContent!;
    expect(el.querySelector('h1,h2')!.textContent).toContain('Cita confirmada');
    expect(texto).toContain('Confirmada');
    expect(texto).toContain('Sesión estándar');
    expect(texto).toContain('Martes 10 de noviembre');
    expect(texto).toContain('10:00 – 11:00');
    expect(texto).toMatch(/80\D?000/);
    expect(texto).toContain('Camila Rojas');
    expect(boton(el, 'Reservar otra cita')).toBeTruthy();

    const citas = await TestBed.inject(AgendaRepo).citas();
    expect(citas.some(c => c.fecha === '2026-11-10' && c.inicio === '10:00' && c.servicioId === 'srv-estandar')).toBe(true);
  });

  it('datos incompletos o correo inválido no avanzan y muestran el error junto al campo', async () => {
    const { fixture, el } = await crear();
    await elegirServicio(fixture, el, 'Sesión estándar');
    await elegirHueco(fixture, el, '08:00');
    boton(el, 'Continuar').click();
    await esperar(fixture);

    await enviar(fixture, el);
    expect(el.querySelector('form')).not.toBeNull();
    const errorDe = (nombre: string) =>
      el.querySelector(`input[name="${nombre}"]`)!.closest('.campo')!.querySelector('.campo-error')?.textContent?.trim();
    expect(errorDe('nombre')).toBe('Escribe tu nombre.');
    expect(errorDe('apellido')).toBe('Escribe tu apellido.');
    expect(errorDe('telefono')).toBe('Escribe tu teléfono.');
    expect(errorDe('correo')).toBe('Escribe un correo válido.');

    llenarDatos(el);
    escribir(el, 'correo', 'camila@correo');
    await enviar(fixture, el);
    expect(el.querySelector('form')).not.toBeNull();
    expect(errorDe('correo')).toBe('Escribe un correo válido.');
    escribir(el, 'correo', 'camila@correo..com'); // la misma regla que Clientes
    await enviar(fixture, el);
    expect(errorDe('correo')).toBe('Escribe un correo válido.');
    expect(errorDe('nombre')).toBeUndefined();
    expect((await TestBed.inject(AgendaRepo).citas()).some(c => c.fecha === '2026-11-09' && c.inicio === '08:00')).toBe(false);
  });

  it('si el horario se ocupa antes de enviar, muestra «Ese horario ya no está disponible, elige otro.» y vuelve al paso 1 con la hora elegida deseleccionada', async () => {
    const repo = TestBed.inject(AgendaRepo);
    const { fixture, el } = await crear();
    await elegirServicio(fixture, el, 'Sesión estándar');
    await elegirHueco(fixture, el, '08:00');
    boton(el, 'Continuar').click();
    await esperar(fixture);
    llenarDatos(el);

    // Otra persona reserva ese mismo horario mientras tanto.
    await repo.reservar({
      servicioId: 'srv-estandar',
      fecha: '2026-11-09',
      inicio: '08:00',
      cliente: { nombre: 'Otra', apellido: 'Persona', telefono: '300 000 0000', correo: 'otra@correo.com' },
    });
    await enviar(fixture, el);

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Ese horario ya no está disponible, elige otro.');
    expect(el.querySelector('form')).toBeNull();
    expect(el.querySelector('[data-testid="bloque"].elegido')).toBeNull();
    expect(boton(el, 'Continuar').disabled).toBe(true);
    expect(huecos(el).some(h => h.textContent?.trim() === '08:00')).toBe(false);
    expect(huecos(el).length).toBeGreaterThan(0);
  });

  it('«Reservar otra cita» vuelve al paso 1 sin servicio ni hora elegidos', async () => {
    const { fixture, el } = await crear();
    await elegirServicio(fixture, el, 'Consulta inicial');
    await elegirHueco(fixture, el, '09:00');
    boton(el, 'Continuar').click();
    await esperar(fixture);
    llenarDatos(el);
    await enviar(fixture, el);
    boton(el, 'Reservar otra cita').click();
    await esperar(fixture);
    expect(servicios(el).length).toBe(3);
    expect(servicios(el).every(b => b.getAttribute('aria-pressed') === 'false')).toBe(true);
    expect(el.querySelector('[data-testid="bloque"].elegido')).toBeNull();
  });

  it('al cambiar de paso el foco pasa al título del paso nuevo', async () => {
    const { fixture, el } = await crear();
    const titulo = () => el.querySelector('h1')!;
    await elegirServicio(fixture, el, 'Sesión estándar');
    await elegirHueco(fixture, el, '08:00');

    boton(el, 'Continuar').click();
    await esperar(fixture);
    expect(titulo().textContent).toContain('Tus datos');
    expect(document.activeElement).toBe(titulo());

    Array.from(el.querySelectorAll('button')).find(b => b.textContent?.includes('Cambiar horario'))!.click();
    await esperar(fixture);
    expect(titulo().textContent).toContain('Reserva tu cita');
    expect(document.activeElement).toBe(titulo());

    boton(el, 'Continuar').click();
    await esperar(fixture);
    llenarDatos(el);
    await enviar(fixture, el);
    expect(titulo().textContent).toContain('Cita confirmada');
    expect(document.activeElement).toBe(titulo());

    boton(el, 'Reservar otra cita').click();
    await esperar(fixture);
    expect(document.activeElement).toBe(titulo());
  });

  it('al volver por un horario ocupado el foco queda en el título del paso 1', async () => {
    const { fixture, el } = await crear();
    await elegirServicio(fixture, el, 'Sesión estándar');
    await elegirHueco(fixture, el, '08:00');
    boton(el, 'Continuar').click();
    await esperar(fixture);
    llenarDatos(el);
    await TestBed.inject(AgendaRepo).reservar({
      servicioId: 'srv-estandar',
      fecha: '2026-11-09',
      inicio: '08:00',
      cliente: { nombre: 'Otra', apellido: 'Persona', telefono: '300 000 0000', correo: 'otra@correo.com' },
    });
    await enviar(fixture, el);
    expect(document.activeElement).toBe(el.querySelector('h1'));
  });

  it('un envío con errores lleva el foco al primer campo inválido', async () => {
    const { fixture, el } = await crear();
    await elegirServicio(fixture, el, 'Sesión estándar');
    await elegirHueco(fixture, el, '08:00');
    boton(el, 'Continuar').click();
    await esperar(fixture);

    await enviar(fixture, el);
    expect(document.activeElement).toBe(el.querySelector('input[name="nombre"]'));

    escribir(el, 'nombre', 'Camila');
    escribir(el, 'apellido', 'Rojas');
    escribir(el, 'telefono', '300 123 4567');
    escribir(el, 'correo', 'sin-arroba');
    await enviar(fixture, el);
    expect(document.activeElement).toBe(el.querySelector('input[name="correo"]'));
  });

  it('tiene un enlace discreto «Acceso del negocio» hacia el inicio de sesión', async () => {
    const { el } = await crear();
    const enlace = Array.from(el.querySelectorAll('a')).find(a => a.textContent?.trim() === 'Acceso del negocio')!;
    expect(enlace.getAttribute('href')).toBe('/login');
  });
});
