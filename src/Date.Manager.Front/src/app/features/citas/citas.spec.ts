import { sumarDias } from '../../shared/formato';
import {
  boton, celdas, configurar, crear, desde, elegir, esperar, fila, filas, HOY, ids, porFechaYHora, primeraConfirmada,
  repo, TARDE, textosBotones,
} from './citas.pruebas';

describe('CitasComponent', () => {
  beforeEach(configurar);

  // --- Listado y filtros ---------------------------------------------------

  it('lista las citas de los últimos 7 días en adelante, ordenadas por fecha y hora', async () => {
    const { el } = await crear();
    expect(el.querySelector('h2')!.textContent).toContain('Citas');
    const encabezados = Array.from(el.querySelectorAll('thead th')).map(t => t.textContent?.trim());
    expect(encabezados).toEqual(['Fecha', 'Hora', 'Servicio', 'Cliente', 'Teléfono', 'Estado', 'Acciones']);

    const todas = await repo().citas();
    const esperadas = desde(todas, sumarDias(HOY, -7));
    expect(esperadas.length).toBeLessThan(todas.length);
    expect(ids(el)).toEqual(esperadas.map(c => c.id));
  });

  it('cada fila muestra fecha, hora, servicio, cliente, teléfono y estado', async () => {
    const { el } = await crear();
    const cita = (await repo().citas()).find(c => c.estado === 'Confirmada' && c.fecha >= HOY)!;
    const servicio = (await repo().servicios()).find(s => s.id === cita.servicioId)!;
    const cliente = (await repo().clientes()).find(c => c.id === cita.clienteId)!;
    const [fecha, hora, nombreServicio, nombreCliente, telefono, estado] = celdas(fila(el, cita.id));
    expect(fecha).toMatch(/^(Lunes|Martes|Miércoles|Jueves|Viernes|Sábado|Domingo) \d{1,2} de [a-zé]+$/);
    expect(hora).toBe(`${cita.inicio}–${cita.fin}`);
    expect(nombreServicio).toBe(servicio.nombre);
    expect(nombreCliente).toBe(`${cliente.nombre} ${cliente.apellido}`);
    expect(telefono).toBe(cliente.telefono);
    expect(estado).toBe('Confirmada');
  });

  it('«Mostrar anteriores» incluye las citas más viejas y vuelve a ocultarlas', async () => {
    const { fixture, el } = await crear();
    const todas = await repo().citas();
    const porDefecto = ids(el).length;

    el.querySelector<HTMLInputElement>('input[name="anteriores"]')!.click();
    await esperar(fixture);
    expect(ids(el)).toEqual([...todas].sort(porFechaYHora).map(c => c.id));
    expect(ids(el).length).toBeGreaterThan(porDefecto);

    el.querySelector<HTMLInputElement>('input[name="anteriores"]')!.click();
    await esperar(fixture);
    expect(ids(el).length).toBe(porDefecto);
  });

  it('filtra por servicio', async () => {
    const { fixture, el } = await crear();
    const opciones = Array.from(el.querySelectorAll<HTMLOptionElement>('select[name="servicio"] option'));
    expect(opciones.map(o => o.textContent?.trim())).toEqual([
      'Todos', 'Consulta inicial', 'Sesión estándar', 'Sesión extendida',
    ]);
    elegir(el, 'select[name="servicio"]', 'srv-extendida');
    await esperar(fixture);

    const esperadas = desde(await repo().citas(), sumarDias(HOY, -7)).filter(c => c.servicioId === 'srv-extendida');
    expect(esperadas.length).toBeGreaterThan(0);
    expect(ids(el)).toEqual(esperadas.map(c => c.id));
  });

  it('filtra por estado', async () => {
    const { fixture, el } = await crear();
    const opciones = Array.from(el.querySelectorAll<HTMLOptionElement>('select[name="estado"] option'));
    expect(opciones.map(o => o.textContent?.trim())).toEqual(['Todos', 'Confirmada', 'Atendida', 'Cancelada']);
    elegir(el, 'select[name="estado"]', 'Cancelada');
    await esperar(fixture);

    const esperadas = desde(await repo().citas(), sumarDias(HOY, -7)).filter(c => c.estado === 'Cancelada');
    expect(esperadas.length).toBeGreaterThan(0);
    expect(ids(el)).toEqual(esperadas.map(c => c.id));
  });

  it('filtra por una fecha, incluso anterior a los últimos 7 días', async () => {
    const { fixture, el } = await crear();
    const vieja = (await repo().citas()).find(c => c.fecha < sumarDias(HOY, -7))!;
    elegir(el, 'input[name="fecha"]', vieja.fecha);
    await esperar(fixture);

    const esperadas = (await repo().citas()).filter(c => c.fecha === vieja.fecha).sort(porFechaYHora);
    expect(ids(el)).toEqual(esperadas.map(c => c.id));
  });

  it('sin resultados muestra «No hay citas con estos filtros.»', async () => {
    const { fixture, el } = await crear();
    expect(el.textContent).not.toContain('No hay citas con estos filtros.');
    elegir(el, 'input[name="fecha"]', '2027-01-01');
    await esperar(fixture);
    expect(filas(el).length).toBe(0);
    expect(el.textContent).toContain('No hay citas con estos filtros.');
  });

  it('el estado Cancelada se marca en rojo', async () => {
    const { el } = await crear();
    const cancelada = (await repo().citas()).find(c => c.estado === 'Cancelada' && c.fecha >= sumarDias(HOY, -7))!;
    expect(fila(el, cancelada.id).querySelector('.chip')!.classList).toContain('cancelada');
    const confirmada = await primeraConfirmada(el);
    expect(fila(el, confirmada.id).querySelector('.chip')!.classList).not.toContain('cancelada');
  });

  // --- Permisos y estados --------------------------------------------------

  it('en una cita Confirmada que ya empezó aparecen las tres acciones', async () => {
    const { el } = await crear(undefined, TARDE);
    const cita = await primeraConfirmada(el);
    expect(textosBotones(fila(el, cita.id))).toEqual(['Mover cita', 'Cancelar cita', 'Marcar como atendida']);
  });

  it('«Marcar como atendida» solo se ofrece cuando la cita ya empezó', async () => {
    const { fixture, el } = await crear(); // 08:00: todavía no empieza ninguna cita de hoy
    const citas = await repo().citas();
    const proxima = citas.filter(c => c.fecha === HOY && c.estado === 'Confirmada').sort(porFechaYHora)[0];
    expect(proxima.inicio > '08:00').toBe(true);
    expect(textosBotones(fila(el, proxima.id))).toEqual(['Mover cita', 'Cancelar cita']);
    const futura = citas.find(c => c.fecha > HOY && c.estado === 'Confirmada')!;
    elegir(el, 'input[name="fecha"]', futura.fecha);
    await esperar(fixture);
    expect(textosBotones(fila(el, futura.id))).toEqual(['Mover cita', 'Cancelar cita']);
  });

  it('«Marcar como atendida» aparece justo a la hora de inicio y se mantiene después', async () => {
    const { el } = await crear(undefined, new Date(2026, 10, 9, 9, 0));
    const citas = await repo().citas();
    const deHoy = citas.filter(c => c.fecha === HOY && c.estado === 'Confirmada');
    expect(deHoy.length).toBeGreaterThan(0);
    for (const c of deHoy) {
      expect(textosBotones(fila(el, c.id)).includes('Marcar como atendida'), `${c.inicio}`).toBe(c.inicio <= '09:00');
    }
  });

  it('las citas Canceladas y Atendidas no tienen acciones', async () => {
    const { fixture, el } = await crear();
    elegir(el, 'select[name="estado"]', 'Cancelada');
    await esperar(fixture);
    expect(filas(el).length).toBeGreaterThan(0);
    expect(el.querySelectorAll('tbody button').length).toBe(0);

    elegir(el, 'select[name="estado"]', 'Atendida');
    await esperar(fixture);
    expect(filas(el).length).toBeGreaterThan(0);
    expect(el.querySelectorAll('tbody button').length).toBe(0);
  });

  it('sin gestionar_citas no aparecen los botones Cancelar y Mover', async () => {
    const { el } = await crear(['ver_citas', 'atender_citas'], TARDE);
    const cita = await primeraConfirmada(el);
    expect(textosBotones(fila(el, cita.id))).toEqual(['Marcar como atendida']);
    expect(textosBotones(el)).not.toContain('Cancelar cita');
    expect(textosBotones(el)).not.toContain('Mover cita');
  });

  it('sin atender_citas no aparece «Marcar como atendida»', async () => {
    const { el } = await crear(['ver_citas', 'gestionar_citas']);
    const cita = await primeraConfirmada(el);
    expect(textosBotones(fila(el, cita.id))).toEqual(['Mover cita', 'Cancelar cita']);
    expect(textosBotones(el)).not.toContain('Marcar como atendida');
  });

  it('con solo ver_citas no hay acciones ni columna de acciones', async () => {
    const { el } = await crear(['ver_citas']);
    expect(filas(el).length).toBeGreaterThan(0);
    expect(el.querySelectorAll('tbody button').length).toBe(0);
    const encabezados = Array.from(el.querySelectorAll('thead th')).map(t => t.textContent?.trim());
    expect(encabezados).not.toContain('Acciones');
  });

  // --- Cancelar ------------------------------------------------------------

  it('cancelar una cita Confirmada la muestra como Cancelada', async () => {
    const { fixture, el } = await crear();
    const cita = await primeraConfirmada(el);

    boton(fila(el, cita.id), 'Cancelar cita').click();
    await esperar(fixture);
    expect(fila(el, cita.id).textContent).toContain('¿Cancelar esta cita?');
    expect(textosBotones(fila(el, cita.id))).toEqual(['Sí, cancelar', 'No, volver']);
    expect((await repo().citas()).find(c => c.id === cita.id)!.estado).toBe('Confirmada');

    boton(fila(el, cita.id), 'Sí, cancelar').click();
    await esperar(fixture);
    expect(celdas(fila(el, cita.id))[5]).toBe('Cancelada');
    expect(fila(el, cita.id).querySelector('.chip')!.classList).toContain('cancelada');
    expect(fila(el, cita.id).querySelectorAll('button').length).toBe(0);
    expect((await repo().citas()).find(c => c.id === cita.id)!.estado).toBe('Cancelada');
    expect(el.querySelector('[role="status"]')!.textContent).toContain('Cita cancelada');
  });

  it('«No, volver» deja la cita Confirmada y restituye los botones', async () => {
    const { fixture, el } = await crear(undefined, TARDE);
    const cita = await primeraConfirmada(el);
    boton(fila(el, cita.id), 'Cancelar cita').click();
    await esperar(fixture);
    boton(fila(el, cita.id), 'No, volver').click();
    await esperar(fixture);

    expect(celdas(fila(el, cita.id))[5]).toBe('Confirmada');
    expect(textosBotones(fila(el, cita.id))).toEqual(['Mover cita', 'Cancelar cita', 'Marcar como atendida']);
    expect((await repo().citas()).find(c => c.id === cita.id)!.estado).toBe('Confirmada');
  });

  it('marcar como atendida cambia el estado y quita las acciones', async () => {
    const { fixture, el } = await crear(undefined, TARDE);
    const cita = await primeraConfirmada(el);
    boton(fila(el, cita.id), 'Marcar como atendida').click();
    await esperar(fixture);

    expect(celdas(fila(el, cita.id))[5]).toBe('Atendida');
    expect(fila(el, cita.id).querySelectorAll('button').length).toBe(0);
    expect((await repo().citas()).find(c => c.id === cita.id)!.estado).toBe('Atendida');
  });


  it('al pedir confirmación para cancelar el foco va a «No, volver» y regresa a «Cancelar cita»', async () => {
    const { fixture, el } = await crear();
    document.body.appendChild(el);
    const cita = await primeraConfirmada(el);
    const abrir = boton(fila(el, cita.id), 'Cancelar cita');
    abrir.focus();
    abrir.click();
    await esperar(fixture);
    expect(document.activeElement).toBe(boton(fila(el, cita.id), 'No, volver'));

    boton(fila(el, cita.id), 'No, volver').click();
    await esperar(fixture);
    expect(document.activeElement).toBe(boton(fila(el, cita.id), 'Cancelar cita'));
    el.remove();
  });
});
