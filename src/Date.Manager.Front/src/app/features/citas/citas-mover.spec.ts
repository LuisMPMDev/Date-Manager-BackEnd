import { ComponentFixture } from '@angular/core/testing';
import { Cita } from '../../core/models';
import {
  alerta, boton, celdas, configurar, crear, dialogo, esperar, fila, primeraConfirmada, repo,
} from './citas.pruebas';

describe('CitasComponent: mover una cita', () => {
  beforeEach(configurar);

  // --- Mover ---------------------------------------------------------------

  async function abrirMover(fixture: ComponentFixture<unknown>, el: HTMLElement, cita: Cita) {
    boton(fila(el, cita.id), 'Mover cita').click();
    await esperar(fixture);
    return dialogo(el)!;
  }

  const huecos = (d: HTMLElement) => Array.from(d.querySelectorAll<HTMLButtonElement>('[data-testid="hueco"]'));
  const diaDe = (d: HTMLElement) => d.querySelector('[data-testid="dia"]')!.textContent?.trim();

  it('mover elige una hora libre del mismo servicio y actualiza la fila', async () => {
    const { fixture, el } = await crear();
    const cita = await primeraConfirmada(el);
    const dlg = await abrirMover(fixture, el, cita);
    expect(dlg.querySelector('h3')!.textContent).toContain('Mover cita');
    expect(dlg.textContent).toContain((await repo().servicios()).find(s => s.id === cita.servicioId)!.nombre);

    // pasa a otro día (el siguiente con horarios libres)
    do {
      boton(dlg, '›').click();
      await esperar(fixture);
    } while (!huecos(dlg).length);
    expect(huecos(dlg).length).toBeGreaterThan(0);
    const destino = huecos(dlg)[huecos(dlg).length - 1];
    const hora = destino.textContent!.trim();
    const libres = await repo().horariosLibres(cita.servicioId, cita.fecha, cita.id);
    expect(libres.length).toBeGreaterThan(0);

    destino.click();
    await esperar(fixture);
    boton(dlg, 'Mover cita').click();
    await esperar(fixture);

    expect(dialogo(el)).toBeNull();
    const movida = (await repo().citas()).find(c => c.id === cita.id)!;
    expect(movida.servicioId).toBe(cita.servicioId);
    expect(movida.inicio).toBe(hora);
    expect(movida.fecha).not.toBe(cita.fecha);
    expect(movida.estado).toBe('Confirmada');

    const celdasFila = celdas(fila(el, cita.id));
    expect(celdasFila[1]).toBe(`${movida.inicio}–${movida.fin}`);
    expect(celdasFila[0]).not.toBe('');
    expect(el.querySelector('[role="status"]')!.textContent).toContain('Cita movida');
  });

  it('la hora actual de la cita se puede volver a elegir en su propio día', async () => {
    const { fixture, el } = await crear();
    const cita = await primeraConfirmada(el);
    const dlg = await abrirMover(fixture, el, cita);
    expect(diaDe(dlg)).toBeTruthy();
    expect(huecos(dlg).map(h => h.textContent!.trim())).toContain(cita.inicio);
  });

  it('las flechas del día se limitan entre hoy y 30 días adelante', async () => {
    const { fixture, el } = await crear();
    const cita = await primeraConfirmada(el);
    const dlg = await abrirMover(fixture, el, cita);

    // retrocede hasta hoy
    for (let i = 0; i < 12 && !boton(dlg, '‹').disabled; i++) {
      boton(dlg, '‹').click();
      await esperar(fixture);
    }
    expect(boton(dlg, '‹').disabled).toBe(true);
    expect(diaDe(dlg)).toBe('Lunes 9 de noviembre');

    for (let i = 0; i < 31 && !boton(dlg, '›').disabled; i++) boton(dlg, '›').click();
    await esperar(fixture);
    expect(boton(dlg, '›').disabled).toBe(true);
    expect(diaDe(dlg)).toBe('Miércoles 9 de diciembre');
  });

  it('si el horario se ocupó, avisa y recarga los horarios', async () => {
    const { fixture, el } = await crear();
    const cita = await primeraConfirmada(el);
    const dlg = await abrirMover(fixture, el, cita);
    // otro día distinto al de la cita, con horarios libres
    do {
      boton(dlg, '›').click();
      await esperar(fixture);
    } while (!huecos(dlg).length);
    const destino = huecos(dlg)[0];
    const hora = destino.textContent!.trim();
    const fechaDestino = dlg.querySelector<HTMLElement>('[data-testid="dia"]')!.dataset['fecha']!;
    destino.click();
    await esperar(fixture);

    // otra persona reserva ese mismo horario antes de confirmar
    await repo().reservar({
      servicioId: cita.servicioId,
      fecha: fechaDestino,
      inicio: hora,
      cliente: { nombre: 'Otra', apellido: 'Persona', telefono: '300 000 0000', correo: 'otra@correo.com' },
    });
    boton(dlg, 'Mover cita').click();
    await esperar(fixture);

    expect(alerta(el)).toBe('Ese horario ya no está disponible, elige otro.');
    expect(dialogo(el)).not.toBeNull();
    expect(huecos(dlg).map(h => h.textContent!.trim())).not.toContain(hora);
    expect(boton(dlg, 'Mover cita').disabled).toBe(true);
    expect((await repo().citas()).find(c => c.id === cita.id)!.inicio).toBe(cita.inicio);
  });

  it('«Mover cita» queda deshabilitado hasta elegir un horario', async () => {
    const { fixture, el } = await crear();
    const cita = await primeraConfirmada(el);
    const dlg = await abrirMover(fixture, el, cita);
    expect(boton(dlg, 'Mover cita').disabled).toBe(true);
    huecos(dlg)[0].click();
    await esperar(fixture);
    expect(boton(dlg, 'Mover cita').disabled).toBe(false);
  });

  it('«Cerrar» cierra el diálogo sin mover la cita', async () => {
    const { fixture, el } = await crear();
    const cita = await primeraConfirmada(el);
    const dlg = await abrirMover(fixture, el, cita);
    huecos(dlg)[0].click();
    await esperar(fixture);
    boton(dlg, 'Cerrar').click();
    await esperar(fixture);

    expect(dialogo(el)).toBeNull();
    const igual = (await repo().citas()).find(c => c.id === cita.id)!;
    expect(igual.fecha).toBe(cita.fecha);
    expect(igual.inicio).toBe(cita.inicio);
  });

  // --- Foco ----------------------------------------------------------------

  it('al abrir «Mover cita» el foco va al título y al cerrar vuelve al botón', async () => {
    const { fixture, el } = await crear();
    document.body.appendChild(el);
    const cita = await primeraConfirmada(el);
    const abrir = boton(fila(el, cita.id), 'Mover cita');
    abrir.focus();
    abrir.click();
    await esperar(fixture);
    expect(document.activeElement).toBe(dialogo(el)!.querySelector('h3'));

    boton(dialogo(el)!, 'Cerrar').click();
    await esperar(fixture);
    expect(document.activeElement).toBe(boton(fila(el, cita.id), 'Mover cita'));
    el.remove();
  });

  it('Escape cierra el diálogo de mover', async () => {
    const { fixture, el } = await crear();
    const cita = await primeraConfirmada(el);
    const dlg = await abrirMover(fixture, el, cita);
    dlg.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await esperar(fixture);
    expect(dialogo(el)).toBeNull();
  });

  it('abrir «Mover cita» en otra cita reinicia el diálogo con los datos de la nueva', async () => {
    const { fixture, el } = await crear();
    document.body.appendChild(el);
    const citas = await repo().citas();
    const visibles = Array.from(el.querySelectorAll<HTMLElement>('tr[data-cita]')).map(f => citas.find(c => c.id === f.dataset['cita'])!);
    const confirmadas = visibles.filter(c => c.estado === 'Confirmada');
    const a = confirmadas[0];
    const b = confirmadas.find(c => c.servicioId !== a.servicioId)!;
    const servicios = await repo().servicios();
    const nombreB = servicios.find(s => s.id === b.servicioId)!.nombre;
    const nombreA = servicios.find(s => s.id === a.servicioId)!.nombre;

    // Cita A: elige un horario que otra persona ocupa antes de confirmar, para dejar selección y error.
    await abrirMover(fixture, el, a);
    do {
      boton(dialogo(el)!, '›').click();
      await esperar(fixture);
    } while (!huecos(dialogo(el)!).length);
    const horaA = huecos(dialogo(el)!)[0].textContent!.trim();
    const fechaA = dialogo(el)!.querySelector<HTMLElement>('[data-testid="dia"]')!.dataset['fecha']!;
    huecos(dialogo(el)!)[0].click();
    await esperar(fixture);
    await repo().reservar({
      servicioId: a.servicioId, fecha: fechaA, inicio: horaA,
      cliente: { nombre: 'Otra', apellido: 'Persona', telefono: '0', correo: 'otra@correo.com' },
    });
    boton(dialogo(el)!, 'Mover cita').click();
    await esperar(fixture);
    expect(alerta(el)).not.toBe('');

    // Cita B, de otro servicio: el diálogo parte de cero.
    const abrirB = boton(fila(el, b.id), 'Mover cita');
    abrirB.focus();
    abrirB.click();
    await esperar(fixture);

    const dlg = dialogo(el)!;
    expect(dlg.textContent).toContain(nombreB);
    expect(dlg.textContent).not.toContain(nombreA);
    expect(alerta(el)).toBe('');
    expect(boton(dlg, 'Mover cita').disabled).toBe(true);
    expect(dlg.textContent).not.toContain('Nuevo horario');
    expect(document.activeElement).toBe(dlg.querySelector('h3'));

    const fechaB = dlg.querySelector<HTMLElement>('[data-testid="dia"]')!.dataset['fecha']!;
    const libresB = await repo().horariosLibres(b.servicioId, fechaB, b.id);
    expect(libresB.length).toBeGreaterThan(0);
    expect(huecos(dlg).map(h => h.textContent!.trim())).toEqual(libresB);

    boton(dlg, 'Cerrar').click();
    await esperar(fixture);
    expect(dialogo(el)).toBeNull();
    expect(document.activeElement).toBe(boton(fila(el, b.id), 'Mover cita'));
    el.remove();
  });
});
