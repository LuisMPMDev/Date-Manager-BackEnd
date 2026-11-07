import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgendaRepo } from '../../core/agenda-repo';
import { DemoAgendaRepo } from '../../core/demo-agenda-repo';
import { RELOJ } from '../../core/errores';
import { ServiciosComponent } from './servicios';

const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00

describe('ServiciosComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ServiciosComponent],
      providers: [
        { provide: RELOJ, useValue: () => AHORA },
        { provide: AgendaRepo, useClass: DemoAgendaRepo },
      ],
    }).compileComponents();
  });

  async function esperar(fixture: ComponentFixture<unknown>) {
    for (let i = 0; i < 2; i++) {
      await new Promise(r => setTimeout(r));
      fixture.detectChanges();
    }
  }

  async function crear() {
    const fixture = TestBed.createComponent(ServiciosComponent);
    fixture.detectChanges();
    await esperar(fixture);
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const repo = () => TestBed.inject(AgendaRepo);
  const filas = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLTableRowElement>('tbody tr'));
  const celdas = (fila: HTMLElement) => Array.from(fila.querySelectorAll('td')).map(c => c.textContent?.trim() ?? '');
  const boton = (el: HTMLElement | Element, texto: string) =>
    Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(b => b.textContent?.trim() === texto)!;
  const filaDe = (el: HTMLElement, nombre: string) => filas(el).find(f => f.textContent?.includes(nombre))!;
  const alerta = (el: HTMLElement) => el.querySelector('[role="alert"]')?.textContent?.trim() ?? '';

  function escribir(el: HTMLElement, selector: string, valor: string) {
    const campo = el.querySelector<HTMLInputElement | HTMLSelectElement>(selector)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  it('lista los servicios con duración, valor, estado y ventanas resumidas', async () => {
    const { el } = await crear();
    expect(el.querySelector('h2')!.textContent).toContain('Servicios');
    const encabezados = Array.from(el.querySelectorAll('thead th')).map(t => t.textContent?.trim());
    expect(encabezados).toEqual(['Nombre', 'Duración', 'Valor', 'Estado', 'Ventanas', 'Acciones']);

    expect(filas(el).length).toBe(3);
    const [nombre, duracion, valor, estado, ventanas] = celdas(filaDe(el, 'Sesión extendida'));
    expect(nombre).toBe('Sesión extendida');
    expect(duracion).toBe('90 min');
    expect(valor).toMatch(/110\D?000/);
    expect(estado).toBe('Activo');
    expect(ventanas).toBe('Mar 09:00–13:00, Jue 09:00–13:00, Vie 14:00–18:00');
  });

  it('desactivar un servicio lo guarda inactivo y ofrece activarlo', async () => {
    const { fixture, el } = await crear();
    boton(filaDe(el, 'Consulta inicial'), 'Desactivar').click();
    await esperar(fixture);

    const fila = filaDe(el, 'Consulta inicial');
    expect(celdas(fila)[3]).toBe('Inactivo');
    expect(boton(fila, 'Activar')).toBeTruthy();
    expect((await repo().servicios()).find(s => s.id === 'srv-consulta')!.activo).toBe(false);

    boton(fila, 'Activar').click();
    await esperar(fixture);
    expect(celdas(filaDe(el, 'Consulta inicial'))[3]).toBe('Activo');
    expect((await repo().servicios()).find(s => s.id === 'srv-consulta')!.activo).toBe(true);
  });

  it('crea un servicio nuevo con su ventana', async () => {
    const { fixture, el } = await crear();
    expect(el.querySelector('form')).toBeNull();
    boton(el, 'Nuevo servicio').click();
    await esperar(fixture);

    escribir(el, 'input[name="nombre"]', 'Masaje relajante');
    escribir(el, 'input[name="duracion"]', '45');
    escribir(el, 'input[name="valor"]', '65000');
    boton(el, 'Agregar ventana').click();
    await esperar(fixture);
    escribir(el, 'select[name="dia-0"]', '3');
    escribir(el, 'input[name="inicio-0"]', '10:00');
    escribir(el, 'input[name="fin-0"]', '14:00');
    boton(el, 'Guardar servicio').click();
    await esperar(fixture);

    expect(el.querySelector('form')).toBeNull();
    expect(filas(el).length).toBe(4);
    const [, duracion, valor, estado, ventanas] = celdas(filaDe(el, 'Masaje relajante'));
    expect(duracion).toBe('45 min');
    expect(valor).toMatch(/65\D?000/);
    expect(estado).toBe('Activo');
    expect(ventanas).toBe('Mié 10:00–14:00');

    const servicios = await repo().servicios();
    const guardado = servicios.find(s => s.nombre === 'Masaje relajante')!;
    expect(guardado.duracionMin).toBe(45);
    expect(guardado.valor).toBe(65000);
    expect(guardado.activo).toBe(true);
    expect(guardado.ventanas.length).toBe(1);
    expect(guardado.ventanas[0]).toMatchObject({ dia: 3, inicio: '10:00', fin: '14:00' });
    expect(new Set(servicios.map(s => s.id)).size).toBe(4);
  });

  it('edita un servicio existente y conserva su id', async () => {
    const { fixture, el } = await crear();
    boton(filaDe(el, 'Sesión estándar'), 'Editar').click();
    await esperar(fixture);

    expect(el.querySelector<HTMLInputElement>('input[name="nombre"]')!.value).toBe('Sesión estándar');
    expect(el.querySelector<HTMLInputElement>('input[name="duracion"]')!.value).toBe('60');
    expect(el.querySelectorAll('[data-testid="ventana"]').length).toBe(12); // 6 días × 2 tramos

    escribir(el, 'input[name="nombre"]', 'Sesión larga');
    escribir(el, 'input[name="duracion"]', '75');
    boton(el, 'Guardar servicio').click();
    await esperar(fixture);

    const guardado = (await repo().servicios()).find(s => s.id === 'srv-estandar')!;
    expect(guardado.nombre).toBe('Sesión larga');
    expect(guardado.duracionMin).toBe(75);
    expect(guardado.ventanas.length).toBe(12);
    expect(filas(el).length).toBe(3);
    expect(celdas(filaDe(el, 'Sesión larga'))[1]).toBe('75 min');
  });

  it('«Quitar» elimina la ventana y «Agregar ventana» añade una', async () => {
    const { fixture, el } = await crear();
    boton(filaDe(el, 'Sesión extendida'), 'Editar').click();
    await esperar(fixture);
    expect(el.querySelectorAll('[data-testid="ventana"]').length).toBe(3);

    boton(el.querySelector('[data-testid="ventana"]')!, 'Quitar').click();
    await esperar(fixture);
    expect(el.querySelectorAll('[data-testid="ventana"]').length).toBe(2);

    boton(el, 'Agregar ventana').click();
    await esperar(fixture);
    expect(el.querySelectorAll('[data-testid="ventana"]').length).toBe(3);

    boton(el, 'Guardar servicio').click(); // la ventana nueva trae horas por defecto válidas
    await esperar(fixture);
    expect((await repo().servicios()).find(s => s.id === 'srv-extendida')!.ventanas.length).toBe(3);
  });

  it('dos ventanas que se cruzan el mismo día muestran el error y no se guardan', async () => {
    const { fixture, el } = await crear();
    boton(el, 'Nuevo servicio').click();
    await esperar(fixture);
    escribir(el, 'input[name="nombre"]', 'Cruzado');
    boton(el, 'Agregar ventana').click();
    boton(el, 'Agregar ventana').click();
    await esperar(fixture);
    escribir(el, 'input[name="inicio-1"]', '11:00'); // la primera queda Lunes 09:00–12:00
    escribir(el, 'input[name="fin-1"]', '14:00');
    boton(el, 'Guardar servicio').click();
    await esperar(fixture);

    expect(alerta(el)).toContain('Lunes');
    expect(alerta(el)).toContain('se cruzan');
    expect(el.querySelector('form')).not.toBeNull();
    expect((await repo().servicios()).length).toBe(3);

    escribir(el, 'input[name="inicio-1"]', '12:00'); // ahora se tocan: es válido
    boton(el, 'Guardar servicio').click();
    await esperar(fixture);
    expect(el.querySelector('form')).toBeNull();
    expect((await repo().servicios()).length).toBe(4);
  });

  it('el selector de día usa nombres en español y guarda 1 a 7', async () => {
    const { fixture, el } = await crear();
    boton(el, 'Nuevo servicio').click();
    await esperar(fixture);
    boton(el, 'Agregar ventana').click();
    await esperar(fixture);
    const opciones = Array.from(el.querySelectorAll<HTMLOptionElement>('select[name="dia-0"] option'));
    expect(opciones.map(o => o.textContent?.trim())).toEqual(
      ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'],
    );
    expect(opciones.map(o => o.value)).toEqual(['1', '2', '3', '4', '5', '6', '7']);
  });

  it('un servicio inválido muestra el error y no se guarda', async () => {
    const { fixture, el } = await crear();
    boton(el, 'Nuevo servicio').click();
    await esperar(fixture);
    escribir(el, 'input[name="nombre"]', '');
    escribir(el, 'input[name="duracion"]', '30');
    escribir(el, 'input[name="valor"]', '10000');
    boton(el, 'Guardar servicio').click();
    await esperar(fixture);

    expect(alerta(el)).toContain('nombre');
    expect(el.querySelector('form')).not.toBeNull();
    expect((await repo().servicios()).length).toBe(3);

    escribir(el, 'input[name="nombre"]', 'Algo');
    boton(el, 'Agregar ventana').click();
    await esperar(fixture);
    escribir(el, 'input[name="inicio-0"]', '12:00');
    escribir(el, 'input[name="fin-0"]', '09:00');
    boton(el, 'Guardar servicio').click();
    await esperar(fixture);
    expect(alerta(el)).toContain('posterior');
    expect((await repo().servicios()).length).toBe(3);
  });

  it('«Cancelar» cierra el formulario sin guardar', async () => {
    const { fixture, el } = await crear();
    boton(el, 'Nuevo servicio').click();
    await esperar(fixture);
    escribir(el, 'input[name="nombre"]', 'Descartado');
    boton(el.querySelector('form')!, 'Cancelar').click();
    await esperar(fixture);

    expect(el.querySelector('form')).toBeNull();
    expect((await repo().servicios()).length).toBe(3);
  });

  it('el foco pasa al título del formulario y vuelve al botón que lo abrió', async () => {
    const { fixture, el } = await crear();
    document.body.appendChild(el);
    const nuevo = boton(el, 'Nuevo servicio');
    nuevo.focus();
    nuevo.click();
    await esperar(fixture);
    expect(document.activeElement).toBe(el.querySelector('form h3'));

    boton(el.querySelector('form')!, 'Cancelar').click();
    await esperar(fixture);
    expect(document.activeElement).toBe(boton(el, 'Nuevo servicio'));
    el.remove();
  });
});
