import { Injectable } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgendaRepo } from '../../core/agenda-repo';
import { AuthApi } from '../../core/auth-api';
import { DemoAgendaRepo } from '../../core/demo-agenda-repo';
import { RELOJ } from '../../core/errores';
import { Cliente } from '../../core/models';
import { sembrar } from '../../core/seed';
import { SesionService } from '../../core/sesion';
import { AuthApiDemo, rolDe } from '../../core/sesion.pruebas';
import { ClientesComponent } from './clientes';

const AHORA = new Date(2026, 10, 9, 8, 0);

@Injectable()
class RepoQueFallaAlGuardar extends DemoAgendaRepo {
  override async guardarCliente(): Promise<Cliente> {
    throw new Error('sin conexión');
  }
}

async function configurar(repo: typeof DemoAgendaRepo = DemoAgendaRepo) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [ClientesComponent],
    providers: [
      { provide: RELOJ, useValue: () => AHORA },
      { provide: AgendaRepo, useClass: repo },
      { provide: AuthApi, useClass: AuthApiDemo },
    ],
  }).compileComponents();
}

async function esperar(fixture: ComponentFixture<unknown>) {
  for (let i = 0; i < 2; i++) {
    await new Promise(r => setTimeout(r));
    fixture.detectChanges();
  }
}

async function crear() {
  const datos = sembrar(AHORA);
  TestBed.inject(SesionService).iniciar(datos.usuarios[0], rolDe(datos.usuarios[0], datos.roles));
  const fixture = TestBed.createComponent(ClientesComponent);
  fixture.detectChanges();
  await esperar(fixture);
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

const repo = () => TestBed.inject(AgendaRepo);
const filas = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLTableRowElement>('tbody tr[data-cliente]'));
const celdas = (fila: Element) => Array.from(fila.querySelectorAll('td')).map(c => c.textContent?.trim() ?? '');
const nombres = (el: HTMLElement) => filas(el).map(f => celdas(f)[0]);
const alerta = (el: HTMLElement) => el.querySelector('[role="alert"]')?.textContent?.trim() ?? '';
const boton = (el: HTMLElement, texto: string) =>
  Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(b => b.textContent?.trim() === texto)!;
const campo = (el: HTMLElement, nombre: string) => el.querySelector<HTMLInputElement>(`form input[name="${nombre}"]`)!;
const buscador = (el: HTMLElement) => el.querySelector<HTMLInputElement>('input[name="buscar"]')!;

function escribir(input: HTMLInputElement, valor: string) {
  input.value = valor;
  input.dispatchEvent(new Event('input'));
}

async function editar(fixture: ComponentFixture<unknown>, el: HTMLElement, id: string) {
  filas(el).find(f => f.dataset['cliente'] === id)!.querySelector<HTMLButtonElement>('button')!.click();
  await esperar(fixture);
}

describe('ClientesComponent', () => {
  beforeEach(() => configurar());

  it('lista los clientes con nombre, apellido, teléfono, correo y cuántas citas no canceladas tienen', async () => {
    const { el } = await crear();
    expect(el.querySelector('h2')!.textContent).toContain('Clientes');
    expect(Array.from(el.querySelectorAll('thead th')).map(t => t.textContent?.trim()))
      .toEqual(['Nombre', 'Apellido', 'Teléfono', 'Correo', 'Citas', 'Acciones']);

    const clientes = await repo().clientes();
    const citas = await repo().citas();
    expect(filas(el)).toHaveLength(clientes.length);
    for (const c of clientes) {
      const fila = filas(el).find(f => f.dataset['cliente'] === c.id)!;
      const total = citas.filter(x => x.clienteId === c.id && x.estado !== 'Cancelada').length;
      expect(celdas(fila).slice(0, 5)).toEqual([c.nombre, c.apellido, c.telefono, c.correo, String(total)]);
    }
  });

  it('no permite crear ni eliminar clientes', async () => {
    const { el } = await crear();
    const textos = Array.from(el.querySelectorAll('button')).map(b => b.textContent?.trim());
    expect(textos.filter(t => t !== 'Editar')).toEqual([]);
  });

  describe('búsqueda', () => {
    it('tiene una caja rotulada «Buscar por nombre, correo o teléfono»', async () => {
      const { el } = await crear();
      const caja = buscador(el);
      expect(el.querySelector(`label[for="${caja.id}"]`)!.textContent?.trim()).toBe('Buscar por nombre, correo o teléfono');
    });

    it('filtra sin importar mayúsculas ni tildes, y por correo o teléfono', async () => {
      const { fixture, el } = await crear();
      escribir(buscador(el), '  SEBASTIAN ');
      await esperar(fixture);
      expect(nombres(el)).toEqual(['Sebastián']);

      escribir(buscador(el), 'lucia@');
      await esperar(fixture);
      expect(nombres(el)).toEqual(['Lucía']);

      escribir(buscador(el), '0105');
      await esperar(fixture);
      expect(nombres(el)).toEqual(['Isabela']);

      escribir(buscador(el), '');
      await esperar(fixture);
      expect(filas(el)).toHaveLength(8);
    });

    it('sin coincidencias muestra «No hay clientes con esa búsqueda.»', async () => {
      const { fixture, el } = await crear();
      escribir(buscador(el), 'zzzz');
      await esperar(fixture);
      expect(filas(el)).toHaveLength(0);
      expect(el.querySelector('tbody')!.textContent).toContain('No hay clientes con esa búsqueda.');
    });
  });

  describe('edición', () => {
    it('«Editar» abre el formulario con los datos del cliente', async () => {
      const { fixture, el } = await crear();
      expect(el.querySelector('form')).toBeNull();
      await editar(fixture, el, 'cli-2');
      expect(campo(el, 'nombre').value).toBe('Daniel');
      expect(campo(el, 'apellido').value).toBe('Ortiz');
      expect(campo(el, 'telefono').value).toBe('300 555 0102');
      expect(campo(el, 'correo').value).toBe('daniel@correo.com');
      for (const n of ['nombre', 'apellido', 'telefono', 'correo']) {
        expect(el.querySelector(`label[for="${campo(el, n).id}"]`)).not.toBeNull();
      }
      expect(boton(el, 'Guardar cliente')).toBeTruthy();
      expect(boton(el, 'Cancelar')).toBeTruthy();
    });

    it('«Guardar cliente» guarda los cambios, cierra el formulario y avisa', async () => {
      const { fixture, el } = await crear();
      await editar(fixture, el, 'cli-2');
      escribir(campo(el, 'nombre'), '  Danilo ');
      escribir(campo(el, 'telefono'), '301 000 1111');
      escribir(campo(el, 'correo'), 'danilo@correo.com');
      boton(el, 'Guardar cliente').click();
      await esperar(fixture);

      const guardado = (await repo().clientes()).find(c => c.id === 'cli-2')!;
      expect(guardado).toEqual({
        id: 'cli-2', nombre: 'Danilo', apellido: 'Ortiz', telefono: '301 000 1111', correo: 'danilo@correo.com',
      });
      expect(el.querySelector('form')).toBeNull();
      expect(celdas(filas(el).find(f => f.dataset['cliente'] === 'cli-2')!).slice(0, 4))
        .toEqual(['Danilo', 'Ortiz', '301 000 1111', 'danilo@correo.com']);
      expect(el.querySelector('[role="status"]')!.textContent).toContain('Cliente guardado.');
    });

    it('«Cancelar» descarta lo escrito: se edita una copia', async () => {
      const { fixture, el } = await crear();
      await editar(fixture, el, 'cli-2');
      escribir(campo(el, 'nombre'), 'Otro');
      await esperar(fixture);
      expect(nombres(el)).toContain('Daniel'); // la tabla no cambia mientras se escribe
      boton(el, 'Cancelar').click();
      await esperar(fixture);

      expect(el.querySelector('form')).toBeNull();
      expect(nombres(el)).toContain('Daniel');
      expect((await repo().clientes()).find(c => c.id === 'cli-2')!.nombre).toBe('Daniel');

      await editar(fixture, el, 'cli-2'); // al volver a abrir, vuelve a mostrar los datos guardados
      expect(campo(el, 'nombre').value).toBe('Daniel');
    });

    it('no guarda si falta un dato o el correo no es válido, y lo explica en una alerta', async () => {
      const { fixture, el } = await crear();
      await editar(fixture, el, 'cli-2');
      escribir(campo(el, 'apellido'), '');
      boton(el, 'Guardar cliente').click();
      await esperar(fixture);
      expect(alerta(el)).toBe('Escribe el apellido.');

      escribir(campo(el, 'apellido'), 'Ortiz');
      escribir(campo(el, 'correo'), 'daniel@correo');
      boton(el, 'Guardar cliente').click();
      await esperar(fixture);
      expect(alerta(el)).toBe('Escribe un correo válido, por ejemplo nombre@correo.com.');
      expect((await repo().clientes()).find(c => c.id === 'cli-2')!.correo).toBe('daniel@correo.com');
      expect(el.querySelector('form')).not.toBeNull();
    });

    it('no deja usar el correo de otro cliente, sin importar mayúsculas', async () => {
      const { fixture, el } = await crear();
      await editar(fixture, el, 'cli-2');
      escribir(campo(el, 'correo'), 'CAMILA@correo.com');
      boton(el, 'Guardar cliente').click();
      await esperar(fixture);
      expect(alerta(el)).toBe('Ya existe otro cliente con ese correo.');
      expect((await repo().clientes()).find(c => c.id === 'cli-2')!.correo).toBe('daniel@correo.com');
    });

    it('la alerta desaparece al cancelar y no vuelve al abrir otro cliente', async () => {
      const { fixture, el } = await crear();
      await editar(fixture, el, 'cli-2');
      escribir(campo(el, 'nombre'), '');
      boton(el, 'Guardar cliente').click();
      await esperar(fixture);
      expect(alerta(el)).not.toBe('');
      boton(el, 'Cancelar').click();
      await esperar(fixture);
      await editar(fixture, el, 'cli-3');
      expect(alerta(el)).toBe('');
    });

    it('si el repositorio falla al guardar, avisa y deja el formulario abierto', async () => {
      await configurar(RepoQueFallaAlGuardar);
      const { fixture, el } = await crear();
      await editar(fixture, el, 'cli-2');
      escribir(campo(el, 'nombre'), 'Danilo');
      boton(el, 'Guardar cliente').click();
      await esperar(fixture);
      expect(alerta(el)).toBe('No pudimos guardar el cliente. Intenta de nuevo.');
      expect(el.querySelector('form')).not.toBeNull();
      expect(campo(el, 'nombre').value).toBe('Danilo');
    });
  });
});
