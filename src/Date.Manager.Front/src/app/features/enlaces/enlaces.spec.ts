import { Injectable } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot } from '@angular/router';
import { routes } from '../../app.routes';
import { ApiError } from '../../core/api-error';
import { Enlace, EnlacesApi } from '../../core/enlaces-api';
import { conPermiso } from '../../core/guards';
import { SesionService } from '../../core/sesion';
import { ANDRES, MARTA, ROL_ADMIN, ROL_RECEPCION } from '../../core/sesion.pruebas';
import { PanelShell } from '../../layout/panel-shell';
import { EnlacesComponent } from './enlaces';
import { fechaHora } from './enlaces-formato';

const SIN_PERMISO = 'No tienes permiso para ver esta información.';

function enlace(extra: Partial<Enlace> = {}): Enlace {
  return {
    id: 'e-1',
    codigo: 'CODIGO0001',
    ruta: '/agendar/CODIGO0001',
    estado: 'Activo',
    usos: 2,
    maxUsos: 10,
    venceEn: '2026-11-10T15:30:00Z',
    creadoEn: '2026-11-09T15:30:00Z',
    creadoPor: 'u-marta',
    creadoPorNombre: 'Marta Ríos',
    ...extra,
  };
}

/** Promesa que el test resuelve o rechaza a mano. */
function diferida<T>() {
  let resolver!: (v: T) => void;
  let rechazar!: (e: unknown) => void;
  const promesa = new Promise<T>((res, rej) => { resolver = res; rechazar = rej; });
  return { promesa, resolver, rechazar };
}

/** EnlacesApi de pruebas: anota las llamadas y responde lo que digan sus campos. */
@Injectable()
class EnlacesApiFalsa {
  lista: Enlace[] = [];
  readonly listados: number[] = [];
  readonly creaciones: { duracionMinutos?: number; maxUsos?: number }[] = [];
  readonly desactivaciones: string[] = [];
  alListar: () => Promise<Enlace[]> = () => Promise.resolve(this.lista);
  alCrear: (p: { duracionMinutos?: number; maxUsos?: number }) => Promise<Enlace> =
    () => Promise.resolve(enlace({ id: 'e-nuevo', codigo: 'NUEVO00001', ruta: '/agendar/NUEVO00001', usos: 0 }));
  alDesactivar: (id: string) => Promise<Enlace> = id => Promise.resolve(enlace({ id, estado: 'Desactivado' }));

  listar() {
    this.listados.push(this.listados.length + 1);
    return this.alListar();
  }
  crear(p: { duracionMinutos?: number; maxUsos?: number }) {
    this.creaciones.push(p);
    return this.alCrear(p);
  }
  desactivar(id: string) {
    this.desactivaciones.push(id);
    return this.alDesactivar(id);
  }
}

describe('EnlacesComponent', () => {
  beforeEach(async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [EnlacesComponent],
      providers: [{ provide: EnlacesApi, useClass: EnlacesApiFalsa }],
    }).compileComponents();
  });

  const api = () => TestBed.inject(EnlacesApi) as unknown as EnlacesApiFalsa;

  async function esperar(fixture: ComponentFixture<unknown>) {
    for (let i = 0; i < 3; i++) {
      await new Promise(r => setTimeout(r));
      fixture.detectChanges();
    }
  }

  async function crear(lista: Enlace[] = [enlace()]) {
    api().lista = lista;
    const fixture = TestBed.createComponent(EnlacesComponent);
    fixture.detectChanges();
    await esperar(fixture);
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const filas = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLTableRowElement>('tbody tr[data-enlace]'));
  const celdas = (fila: Element) => Array.from(fila.querySelectorAll('td')).map(c => c.textContent?.replace(/\s+/g, ' ').trim() ?? '');
  const boton = (el: Element, texto: string) =>
    Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(b => b.textContent?.trim() === texto);
  const campoDuracion = (el: HTMLElement) => el.querySelector<HTMLInputElement>('#enlace-duracion')!;
  const campoUsos = (el: HTMLElement) => el.querySelector<HTMLInputElement>('#enlace-usos')!;
  const alertas = (el: HTMLElement) => Array.from(el.querySelectorAll('[role="alert"]')).map(a => a.textContent?.trim());
  const estados = (el: HTMLElement) => el.querySelector('[role="status"]')?.textContent?.trim();

  function escribir(campo: HTMLInputElement, valor: string) {
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  const urlDe = (ruta: string) => `${location.origin}${location.pathname}#${ruta}`;
  /** El formato exacto se prueba (con zona fija) en enlaces-formato.spec.ts; aquí solo que se use. */
  const fechaLocal = (iso: string) => fechaHora(iso);

  describe('lista', () => {
    it('muestra código, estado, usos y máximo, vencimiento en hora local y creador', async () => {
      const { el } = await crear([
        enlace(),
        enlace({ id: 'e-2', codigo: 'CODIGO0002', estado: 'Vencido', usos: 0, maxUsos: undefined, creadoPorNombre: 'Andrés Lora' }),
      ]);
      expect(api().listados).toHaveLength(1);
      const [a, b] = filas(el).map(celdas);
      expect(a.slice(0, 5)).toEqual(['CODIGO0001', 'Activo', '2 de 10', fechaLocal('2026-11-10T15:30:00Z'), 'Marta Ríos']);
      expect(b.slice(0, 5)).toEqual(['CODIGO0002', 'Vencido', '0 (sin límite)', fechaLocal('2026-11-10T15:30:00Z'), 'Andrés Lora']);
      expect(a[3]).toMatch(/2026/);
    });

    it('respeta el orden que trae la API (más nuevos primero)', async () => {
      const { el } = await crear([
        enlace({ id: 'n', codigo: 'NUEVO00001' }),
        enlace({ id: 'v', codigo: 'VIEJO00001' }),
      ]);
      expect(filas(el).map(f => celdas(f)[0])).toEqual(['NUEVO00001', 'VIEJO00001']);
    });

    it('sin enlaces lo dice', async () => {
      const { el } = await crear([]);
      expect(filas(el)).toHaveLength(0);
      expect(el.textContent).toContain('Todavía no hay enlaces');
    });

    it('un 403 al listar muestra el aviso de sin permiso', async () => {
      api().alListar = () => Promise.reject(new ApiError(403, 'SIN_PERMISO', 'No tienes permiso para esta acción.'));
      const { el } = await crear();
      expect(alertas(el)).toEqual([SIN_PERMISO]);
      expect(filas(el)).toHaveLength(0);
    });

    it('con error al listar no afirma que no haya enlaces', async () => {
      api().alListar = () => Promise.reject(new ApiError(500, 'ERROR_INTERNO', 'Algo falló en el servidor.'));
      const { el } = await crear();
      expect(el.textContent).not.toContain('Todavía no hay enlaces');
      expect(el.textContent).not.toContain('Cargando enlaces');
    });

    it('otro error al listar muestra el mensaje de la API', async () => {
      api().alListar = () => Promise.reject(new ApiError(500, 'ERROR_INTERNO', 'Algo falló en el servidor.'));
      const { el } = await crear();
      expect(alertas(el)).toEqual(['Algo falló en el servidor.']);
    });
  });

  describe('crear', () => {
    it('trae 1440 minutos por defecto y el máximo vacío', async () => {
      const { el } = await crear();
      expect(campoDuracion(el).value).toBe('1440');
      expect(campoUsos(el).value).toBe('');
    });

    it('con los valores por defecto crea sin máximo, muestra la URL completa y recarga la lista', async () => {
      const { fixture, el } = await crear();
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      expect(api().creaciones).toEqual([{ duracionMinutos: 1440 }]);
      expect('maxUsos' in api().creaciones[0]).toBe(false);
      const url = el.querySelector<HTMLInputElement>('input[readonly]');
      expect(url?.value).toBe(urlDe('/agendar/NUEVO00001'));
      expect(api().listados).toHaveLength(2);
    });

    it('manda duración y máximo cuando se escriben', async () => {
      const { fixture, el } = await crear();
      escribir(campoDuracion(el), '120');
      escribir(campoUsos(el), '3');
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      expect(api().creaciones).toEqual([{ duracionMinutos: 120, maxUsos: 3 }]);
    });

    it('dos clics seguidos en «Crear enlace» envían una sola petición', async () => {
      const espera = diferida<Enlace>();
      api().alCrear = () => espera.promesa;
      const { fixture, el } = await crear();
      const crearBtn = boton(el, 'Crear enlace')!;
      crearBtn.click();
      crearBtn.click();
      fixture.detectChanges();
      expect(api().creaciones).toHaveLength(1);
      expect(crearBtn.disabled).toBe(true);
      espera.resolver(enlace({ id: 'e-nuevo', ruta: '/agendar/NUEVO00001' }));
      await esperar(fixture);
      expect(api().creaciones).toHaveLength(1);
      expect(crearBtn.disabled).toBe(false);
    });

    const MSG_DURACION = 'La duración debe ser un número entero de minutos entre 60 y 43200.';
    const MSG_USOS = 'El máximo de usos debe ser un número entero entre 1 y 2147483647, o quedar vacío para no limitarlo.';

    it('los campos numéricos son de texto: la validación es solo de `armarPeticion`', async () => {
      const { el } = await crear();
      for (const campo of [campoDuracion(el), campoUsos(el)]) {
        expect(campo.type).toBe('text');
        expect(campo.inputMode).toBe('numeric');
      }
    });

    it.each([
      ['59', '', MSG_DURACION],
      ['43201', '', MSG_DURACION],
      ['', '', MSG_DURACION],
      ['90.5', '', MSG_DURACION],
      ['abc', '', MSG_DURACION],
      ['1e3', '', MSG_DURACION],
      ['-60', '', MSG_DURACION],
      ['99999999999', '', MSG_DURACION],
      ['+90', '', MSG_DURACION],
      ['0x3C', '', MSG_DURACION],
      ['９０', '', MSG_DURACION],
      ['9'.repeat(400), '', MSG_DURACION],
      ['1440', '0', MSG_USOS],
      ['1440', '-1', MSG_USOS],
      ['1440', '-2', MSG_USOS],
      ['1440', '2.5', MSG_USOS],
      ['1440', '1.5', MSG_USOS],
      ['1440', 'abc', MSG_USOS],
      ['1440', '-', MSG_USOS],
      ['1440', '1e3', MSG_USOS],
      ['1440', '1.', MSG_USOS],
      ['1440', '2147483648', MSG_USOS],
      ['1440', '99999999999', MSG_USOS],
      ['1440', '+5', MSG_USOS],
      ['1440', '0x10', MSG_USOS],
      ['1440', '１２', MSG_USOS], // dígitos de ancho completo
      ['1440', '9'.repeat(400), MSG_USOS],
    ])('con duración «%s» y usos «%s» avisa y no envía nada', async (duracion, usos, mensaje) => {
      const { fixture, el } = await crear();
      escribir(campoDuracion(el), duracion);
      escribir(campoUsos(el), usos);
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      expect(api().creaciones).toHaveLength(0);
      expect(alertas(el)).toEqual([mensaje]);
    });

    it('recorta espacios: « 90 » en usos vale 90 y «  120 » en duración vale 120', async () => {
      const { fixture, el } = await crear();
      escribir(campoDuracion(el), '  120 ');
      escribir(campoUsos(el), ' 90 ');
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      expect(api().creaciones).toEqual([{ duracionMinutos: 120, maxUsos: 90 }]);
    });

    it('acepta el máximo entero del servidor (2147483647) y un máximo de usos solo de espacios cuenta como vacío', async () => {
      const { fixture, el } = await crear();
      escribir(campoUsos(el), '2147483647');
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      escribir(campoUsos(el), '   ');
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      expect(api().creaciones).toEqual([
        { duracionMinutos: 1440, maxUsos: 2147483647 },
        { duracionMinutos: 1440 },
      ]);
    });

    it('acepta los límites 60 y 43200', async () => {
      const { fixture, el } = await crear();
      escribir(campoDuracion(el), '60');
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      escribir(campoDuracion(el), '43200');
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      expect(api().creaciones.map(c => c.duracionMinutos)).toEqual([60, 43200]);
    });

    it('muestra el mensaje de un 400 de la API y deja los valores escritos', async () => {
      api().alCrear = () => Promise.reject(new ApiError(400, 'VALIDACION', 'El máximo de usos debe ser al menos 1, o vacío para no tener límite.'));
      const { fixture, el } = await crear();
      escribir(campoUsos(el), '5');
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      expect(alertas(el)).toEqual(['El máximo de usos debe ser al menos 1, o vacío para no tener límite.']);
      expect(campoUsos(el).value).toBe('5');
      expect(boton(el, 'Crear enlace')!.disabled).toBe(false);
    });

    it('un 403 al crear muestra el aviso de sin permiso', async () => {
      api().alCrear = () => Promise.reject(new ApiError(403, 'SIN_PERMISO', 'No tienes permiso para esta acción.'));
      const { fixture, el } = await crear();
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      expect(alertas(el)).toEqual([SIN_PERMISO]);
    });

    it('un nuevo intento limpia el error anterior', async () => {
      const { fixture, el } = await crear();
      escribir(campoDuracion(el), '5');
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      expect(alertas(el)).toHaveLength(1);
      escribir(campoDuracion(el), '60');
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      expect(alertas(el)).toHaveLength(0);
    });
  });

  describe('copiar', () => {
    const portapapeles = (impl: unknown) =>
      Object.defineProperty(navigator, 'clipboard', { value: impl, configurable: true });
    const sinPortapapeles = () => Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    afterEach(() => sinPortapapeles());

    it('«Copiar» escribe la URL completa en el portapapeles y lo avisa', async () => {
      const escribirTexto = vi.fn().mockResolvedValue(undefined);
      portapapeles({ writeText: escribirTexto });
      const { fixture, el } = await crear();
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      boton(el, 'Copiar')!.click();
      await esperar(fixture);
      expect(escribirTexto).toHaveBeenCalledWith(urlDe('/agendar/NUEVO00001'));
      expect(estados(el)).toBe('Enlace copiado.');
    });

    it('sin portapapeles deja la URL a la vista, seleccionada, y pide copiarla a mano', async () => {
      sinPortapapeles();
      const { fixture, el } = await crear();
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      boton(el, 'Copiar')!.click();
      await esperar(fixture);
      const url = el.querySelector<HTMLInputElement>('input[readonly]')!;
      expect(url.value).toBe(urlDe('/agendar/NUEVO00001'));
      expect(url.selectionStart).toBe(0);
      expect(url.selectionEnd).toBe(url.value.length);
      expect(estados(el)).toBe('No se pudo copiar automáticamente. Selecciona el texto y cópialo.');
    });

    it('si el portapapeles rechaza, pasa lo mismo que sin portapapeles', async () => {
      portapapeles({ writeText: vi.fn().mockRejectedValue(new Error('denegado')) });
      const { fixture, el } = await crear();
      boton(el, 'Crear enlace')!.click();
      await esperar(fixture);
      boton(el, 'Copiar')!.click();
      await esperar(fixture);
      expect(estados(el)).toBe('No se pudo copiar automáticamente. Selecciona el texto y cópialo.');
    });

    it('«Enlace copiado.» se borra solo tras unos segundos', async () => {
      portapapeles({ writeText: vi.fn().mockResolvedValue(undefined) });
      const { fixture, el } = await crear();
      vi.useFakeTimers();
      try {
        boton(filas(el)[0], 'Copiar enlace')!.click();
        await vi.advanceTimersByTimeAsync(0);
        fixture.detectChanges();
        expect(estados(el)).toBe('Enlace copiado.');
        await vi.advanceTimersByTimeAsync(6000);
        fixture.detectChanges();
        expect(estados(el)).toBe('');
      } finally {
        vi.useRealTimers();
      }
    });

    it('el aviso de copia manual NO se borra solo: hay que dar tiempo a copiar el texto', async () => {
      sinPortapapeles();
      const { fixture, el } = await crear();
      vi.useFakeTimers();
      try {
        boton(filas(el)[0], 'Copiar enlace')!.click();
        await vi.advanceTimersByTimeAsync(0);
        fixture.detectChanges();
        const mensaje = 'No se pudo copiar automáticamente. Selecciona el texto y cópialo.';
        expect(estados(el)).toBe(mensaje);
        await vi.advanceTimersByTimeAsync(60000);
        fixture.detectChanges();
        expect(estados(el)).toBe(mensaje);
        expect(vi.getTimerCount()).toBe(0);
      } finally {
        vi.useRealTimers();
      }
    });

    it('el aviso también se borra al empezar otra acción', async () => {
      portapapeles({ writeText: vi.fn().mockResolvedValue(undefined) });
      const { fixture, el } = await crear();
      boton(filas(el)[0], 'Copiar enlace')!.click();
      await esperar(fixture);
      expect(estados(el)).toBe('Enlace copiado.');
      boton(filas(el)[0], 'Desactivar')!.click();
      fixture.detectChanges();
      expect(estados(el)).toBe('');
    });

    it('al destruir la pantalla no queda ningún temporizador pendiente', async () => {
      portapapeles({ writeText: vi.fn().mockResolvedValue(undefined) });
      const { fixture, el } = await crear();
      vi.useFakeTimers();
      try {
        boton(filas(el)[0], 'Copiar enlace')!.click();
        await vi.advanceTimersByTimeAsync(0);
        expect(vi.getTimerCount()).toBeGreaterThan(0);
        fixture.destroy();
        expect(vi.getTimerCount()).toBe(0);
      } finally {
        vi.useRealTimers();
      }
    });

    it('cada enlace Activo de la lista se puede copiar; los demás no', async () => {
      const escribirTexto = vi.fn().mockResolvedValue(undefined);
      portapapeles({ writeText: escribirTexto });
      const { fixture, el } = await crear([
        enlace(),
        enlace({ id: 'e-2', codigo: 'CODIGO0002', ruta: '/agendar/CODIGO0002', estado: 'Agotado' }),
      ]);
      const [activo, agotado] = filas(el);
      expect(boton(agotado, 'Copiar enlace')).toBeUndefined();
      boton(activo, 'Copiar enlace')!.click();
      await esperar(fixture);
      expect(escribirTexto).toHaveBeenCalledWith(urlDe('/agendar/CODIGO0001'));
    });
  });

  describe('desactivar', () => {
    it('pide confirmación en la pantalla y no llama a la API hasta confirmar', async () => {
      const { fixture, el } = await crear();
      boton(filas(el)[0], 'Desactivar')!.click();
      fixture.detectChanges();
      expect(api().desactivaciones).toHaveLength(0);
      expect(filas(el)[0].textContent).toContain('¿Desactivar el enlace CODIGO0001?');
      expect(boton(filas(el)[0], 'Sí, desactivar')).toBeDefined();
    });

    it('al abrir la confirmación el foco pasa a «No, volver»', async () => {
      const { fixture, el } = await crear();
      boton(filas(el)[0], 'Desactivar')!.click();
      await esperar(fixture);
      expect(document.activeElement).toBe(boton(filas(el)[0], 'No, volver'));
    });

    it('tras confirmar con éxito el foco queda en el encabezado de la lista, no en el body', async () => {
      const { fixture, el } = await crear();
      boton(filas(el)[0], 'Desactivar')!.click();
      await esperar(fixture);
      boton(filas(el)[0], 'Sí, desactivar')!.click();
      await esperar(fixture);
      expect(document.activeElement).toBe(el.querySelector('[data-lista]'));
    });

    it('tras confirmar con error el foco también queda en el encabezado de la lista', async () => {
      api().alDesactivar = () => Promise.reject(new ApiError(404, 'NO_ENCONTRADO', 'El enlace ya no existe.'));
      const { fixture, el } = await crear();
      boton(filas(el)[0], 'Desactivar')!.click();
      await esperar(fixture);
      boton(filas(el)[0], 'Sí, desactivar')!.click();
      await esperar(fixture);
      expect(document.activeElement).toBe(el.querySelector('[data-lista]'));
    });

    it('si se destruye la pantalla con la desactivación en vuelo, al terminar no falla', async () => {
      const espera = diferida<Enlace>();
      api().alDesactivar = () => espera.promesa;
      const { fixture, el } = await crear();
      boton(filas(el)[0], 'Desactivar')!.click();
      fixture.detectChanges();
      const pendiente = (fixture.componentInstance as unknown as { confirmarDesactivar(id: string): Promise<void> })
        .confirmarDesactivar('e-1');
      fixture.destroy();
      espera.resolver(enlace({ estado: 'Desactivado' }));
      await expect(pendiente).resolves.toBeUndefined();
    });

    it('«No, volver» devuelve el foco a «Desactivar»', async () => {
      const { fixture, el } = await crear();
      boton(filas(el)[0], 'Desactivar')!.click();
      await esperar(fixture);
      boton(filas(el)[0], 'No, volver')!.click();
      await esperar(fixture);
      expect(document.activeElement).toBe(boton(filas(el)[0], 'Desactivar'));
    });

    it('«No, volver» cancela sin llamar a la API', async () => {
      const { fixture, el } = await crear();
      boton(filas(el)[0], 'Desactivar')!.click();
      fixture.detectChanges();
      boton(filas(el)[0], 'No, volver')!.click();
      fixture.detectChanges();
      expect(api().desactivaciones).toHaveLength(0);
      expect(boton(filas(el)[0], 'Desactivar')).toBeDefined();
    });

    it('al confirmar desactiva y refresca la lista', async () => {
      const { fixture, el } = await crear();
      boton(filas(el)[0], 'Desactivar')!.click();
      fixture.detectChanges();
      api().lista = [enlace({ estado: 'Desactivado' })];
      boton(filas(el)[0], 'Sí, desactivar')!.click();
      await esperar(fixture);
      expect(api().desactivaciones).toEqual(['e-1']);
      expect(api().listados).toHaveLength(2);
      expect(celdas(filas(el)[0])[1]).toBe('Desactivado');
    });

    it('un enlace Desactivado no ofrece desactivar', async () => {
      const { el } = await crear([enlace({ estado: 'Desactivado' })]);
      expect(boton(filas(el)[0], 'Desactivar')).toBeUndefined();
    });

    it('un enlace Vencido o Agotado todavía se puede desactivar', async () => {
      const { el } = await crear([enlace({ estado: 'Vencido' }), enlace({ id: 'e-2', estado: 'Agotado' })]);
      for (const fila of filas(el)) expect(boton(fila, 'Desactivar')).toBeDefined();
    });

    it('dos clics en «Sí, desactivar» envían una sola petición', async () => {
      const espera = diferida<Enlace>();
      api().alDesactivar = () => espera.promesa;
      const { fixture, el } = await crear();
      boton(filas(el)[0], 'Desactivar')!.click();
      fixture.detectChanges();
      const si = boton(filas(el)[0], 'Sí, desactivar')!;
      si.click();
      si.click();
      fixture.detectChanges();
      expect(api().desactivaciones).toHaveLength(1);
      espera.resolver(enlace({ estado: 'Desactivado' }));
      await esperar(fixture);
    });

    it('si la API falla muestra su mensaje y la lista queda como estaba', async () => {
      api().alDesactivar = () => Promise.reject(new ApiError(404, 'NO_ENCONTRADO', 'El enlace ya no existe.'));
      const { fixture, el } = await crear();
      boton(filas(el)[0], 'Desactivar')!.click();
      fixture.detectChanges();
      boton(filas(el)[0], 'Sí, desactivar')!.click();
      await esperar(fixture);
      expect(alertas(el)).toEqual(['El enlace ya no existe.']);
      expect(filas(el)).toHaveLength(1);
    });
  });
});

describe('ruta de enlaces', () => {
  const panel = routes.find(r => r.component === PanelShell)!;
  const ruta = panel.children!.find(r => r.path === 'enlaces')!;

  function correr(usuario: typeof MARTA, rol: typeof ROL_ADMIN) {
    sessionStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    TestBed.inject(SesionService).iniciar(usuario, rol);
    const instantanea = { data: ruta.data ?? {} } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() => conPermiso(instantanea, {} as RouterStateSnapshot));
  }

  it('es hija del panel, protegida por conPermiso con gestionar_enlaces', () => {
    expect(ruta).toBeDefined();
    expect(ruta.canActivate).toEqual([conPermiso]);
    expect(ruta.data).toEqual({ permisos: ['gestionar_enlaces'] });
    expect(ruta.loadComponent).toBeTypeOf('function');
  });

  it('con gestionar_enlaces entra', async () => {
    expect(await correr(MARTA, ROL_ADMIN)).toBe(true);
  });

  it('sin gestionar_enlaces manda a la primera pantalla permitida', async () => {
    expect(String(await correr(ANDRES, ROL_RECEPCION))).toBe('/agenda');
  });
});
