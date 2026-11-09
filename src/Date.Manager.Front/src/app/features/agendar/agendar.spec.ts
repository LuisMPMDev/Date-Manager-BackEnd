import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ApiError } from '../../core/api-error';
import { authInterceptor, errorInterceptor } from '../../core/interceptores';
import { sumarMinutos } from '../../core/horarios';
import { Servicio } from '../../core/models';
import { CitaPublica, PublicoApi } from '../../core/publico-api';
import { RELOJ } from '../../core/errores';
import { SesionService } from '../../core/sesion';
import { MARTA, ROL_ADMIN } from '../../core/sesion.pruebas';
import { TokenStore } from '../../core/token-store';
import { AgendarComponent } from './agendar';

const AHORA = new Date(2026, 10, 9, 8, 0); // lunes 9 de noviembre de 2026, 08:00
const CODIGO = 'abc123XYZ';
const API = 'http://localhost:8080/api';
const BASE = `${API}/publico/invitaciones/${CODIGO}`;
const MENSAJE_OCUPADO = 'Ese horario ya no está disponible, elige otro.';

function ventanas(servicioId: string, tramos: [string, string][]) {
  return [1, 2, 3, 4, 5].flatMap(dia =>
    tramos.map(([inicio, fin], i) => ({ id: `${servicioId}-d${dia}-${i}`, dia: dia as 1, inicio, fin })));
}

const CONSULTA: Servicio = {
  id: 'srv-consulta', nombre: 'Consulta inicial', duracionMin: 30, activo: true, valor: 40000,
  ventanas: ventanas('srv-consulta', [['09:00', '13:00']]),
};
const ESTANDAR: Servicio = {
  id: 'srv-estandar', nombre: 'Sesión estándar', duracionMin: 60, activo: true, valor: 80000,
  ventanas: ventanas('srv-estandar', [['08:00', '12:00']]),
};
const EXTENDIDA: Servicio = {
  id: 'srv-extendida', nombre: 'Sesión extendida', duracionMin: 90, activo: false, valor: 110000,
  ventanas: ventanas('srv-extendida', [['09:00', '13:00']]),
};
const LIBRES: Record<string, string[]> = {
  'srv-consulta': ['09:00', '09:30', '10:00'],
  'srv-estandar': ['08:00', '09:00', '10:00', '11:00'],
};

/** Doble de PublicoApi: cada prueba cambia lo que necesita con mockImplementation / mockRejectedValue. */
function crearApi() {
  return {
    estado: vi.fn(async (_codigo: string) => ({ estado: 'Activo' as const, venceEn: '2026-11-10T08:00:00Z' })),
    servicios: vi.fn(async (_codigo: string): Promise<Servicio[]> => [CONSULTA, ESTANDAR, EXTENDIDA]),
    horarios: vi.fn(async (_codigo: string, servicioId: string, _fecha: string) => LIBRES[servicioId] ?? []),
    reservar: vi.fn(async (_codigo: string, p: { servicioId: string; fecha: string; inicio: string }): Promise<CitaPublica> => ({
      servicioId: p.servicioId, servicio: 'Sesión estándar', fecha: p.fecha, inicio: p.inicio,
      fin: sumarMinutos(p.inicio, 60), estado: 'Confirmada',
    })),
  };
}

type ApiFalsa = ReturnType<typeof crearApi>;

function configurar(api: ApiFalsa) {
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: 'agendar/:codigo', component: AgendarComponent }]),
      { provide: RELOJ, useValue: () => AHORA },
      { provide: PublicoApi, useValue: api },
    ],
  });
}

async function esperar(harness: { detectChanges(): void }) {
  await new Promise(r => setTimeout(r));
  harness.detectChanges();
}

async function abrir(api: ApiFalsa = crearApi(), codigo = CODIGO) {
  configurar(api);
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(`/agendar/${codigo}`, AgendarComponent);
  await esperar(harness);
  return { api, harness, el: harness.routeNativeElement as HTMLElement };
}

type Pantalla = Awaited<ReturnType<typeof abrir>>;

const servicios = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLButtonElement>('[data-testid="servicio"]'));
const huecos = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLButtonElement>('[data-testid="hueco"]'));
const boton = (el: HTMLElement, texto: string) =>
  Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(b => b.textContent?.trim() === texto)!;
const alerta = (el: HTMLElement) => el.querySelector('[role="alert"]')?.textContent?.trim();
const errorDe = (el: HTMLElement, nombre: string) =>
  el.querySelector(`input[name="${nombre}"]`)!.closest('.campo')!.querySelector('.campo-error')?.textContent?.trim();

async function elegirServicio({ harness, el }: Pantalla, nombre: string) {
  servicios(el).find(b => b.textContent?.includes(nombre))!.click();
  await esperar(harness);
}

async function elegirHueco({ harness, el }: Pantalla, hora: string) {
  huecos(el).find(b => b.textContent?.trim() === hora)!.click();
  await esperar(harness);
}

async function irAlPaso2(p: Pantalla, hora = '08:00') {
  await elegirServicio(p, 'Sesión estándar');
  await elegirHueco(p, hora);
  boton(p.el, 'Continuar').click();
  await esperar(p.harness);
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

async function enviar({ harness, el }: Pantalla) {
  el.querySelector('form')!.dispatchEvent(new Event('submit'));
  await esperar(harness);
}

describe('AgendarComponent', () => {
  describe('según el estado del enlace', () => {
    it('lee el código de la ruta y pide el estado de ese enlace', async () => {
      const { api } = await abrir();
      expect(api.estado).toHaveBeenCalledExactlyOnceWith(CODIGO);
      expect(api.servicios).toHaveBeenCalledExactlyOnceWith(CODIGO);
    });

    it('muestra solo los servicios activos', async () => {
      const { el } = await abrir();
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

    it.each([
      ['Vencido', /venció/i],
      ['Agotado', /ya no tiene reservas|agotó/i],
      ['Desactivado', /desactivó|desactivado/i],
    ] as const)('un enlace %s muestra su mensaje, sin formulario ni llamadas a servicios', async (estado, mensaje) => {
      const api = crearApi();
      api.estado.mockResolvedValue({ estado, venceEn: '2026-11-08T08:00:00Z' } as never);
      const { el } = await abrir(api);
      expect(el.querySelector('h1')!.textContent).toMatch(mensaje);
      expect(el.textContent).toContain('Pide un enlace nuevo');
      expect(servicios(el).length).toBe(0);
      expect(el.querySelector('form')).toBeNull();
      expect(boton(el, 'Continuar')).toBeUndefined();
      expect(api.servicios).not.toHaveBeenCalled();
      expect(api.horarios).not.toHaveBeenCalled();
    });

    it('un código inexistente (404 ENLACE_NO_ENCONTRADO) muestra «enlace no encontrado»', async () => {
      const api = crearApi();
      api.estado.mockRejectedValue(new ApiError(404, 'ENLACE_NO_ENCONTRADO', 'No encontramos ese enlace.'));
      const { el } = await abrir(api);
      expect(el.querySelector('h1')!.textContent).toMatch(/enlace no encontrado/i);
      expect(servicios(el).length).toBe(0);
      expect(api.servicios).not.toHaveBeenCalled();
    });

    it('si el estado no se puede leer (sin red) avisa y deja reintentar', async () => {
      const api = crearApi();
      api.estado.mockRejectedValueOnce(new ApiError(0, 'ERROR_RED', 'No se pudo contactar al servidor.'));
      const p = await abrir(api);
      expect(alerta(p.el)).toBe('No se pudo contactar al servidor.');
      expect(servicios(p.el).length).toBe(0);

      boton(p.el, 'Reintentar').click();
      await esperar(p.harness);
      expect(api.estado).toHaveBeenCalledTimes(2);
      expect(servicios(p.el).length).toBe(2);
      expect(alerta(p.el)).toBeUndefined();
    });

    it('en la vista de fallo el foco queda en el título, también tras «Reintentar»', async () => {
      const api = crearApi();
      api.estado.mockRejectedValue(new ApiError(0, 'ERROR_RED', 'No se pudo contactar al servidor.'));
      const p = await abrir(api);
      expect(document.activeElement).toBe(p.el.querySelector('h1'));

      (document.activeElement as HTMLElement).blur();
      boton(p.el, 'Reintentar').click();
      await esperar(p.harness);
      expect(p.el.querySelector('h1')!.textContent).toContain('No pudimos abrir el enlace');
      expect(document.activeElement).toBe(p.el.querySelector('h1'));
    });

    it('un estado inesperado de la API se trata como fallo y no como flujo ni como enlace cerrado', async () => {
      const api = crearApi();
      api.estado.mockResolvedValue({ estado: 'Pausado', venceEn: '2026-11-08T08:00:00Z' } as never);
      const { el } = await abrir(api);
      expect(el.querySelector('h1')!.textContent).toContain('No pudimos abrir el enlace');
      expect(alerta(el)).toBeTruthy();
      expect(boton(el, 'Reintentar')).toBeTruthy();
      expect(servicios(el).length).toBe(0);
      expect(api.servicios).not.toHaveBeenCalled();
    });

    it('un 410 al cargar los servicios (el enlace cambió entre una llamada y otra) muestra el mensaje del estado', async () => {
      const api = crearApi();
      api.servicios.mockRejectedValue(new ApiError(410, 'ENLACE_AGOTADO', 'Este enlace ya no tiene reservas disponibles.'));
      const { el } = await abrir(api);
      expect(el.querySelector('h1')!.textContent).toMatch(/ya no tiene reservas|agotó/i);
      expect(servicios(el).length).toBe(0);
    });

    it('si la ruta cambia de código reinicia el flujo con el enlace nuevo', async () => {
      const p = await abrir();
      await elegirServicio(p, 'Sesión estándar');
      await p.harness.navigateByUrl('/agendar/otroCodigo');
      await esperar(p.harness);
      expect(p.api.estado).toHaveBeenLastCalledWith('otroCodigo');
      expect(p.api.servicios).toHaveBeenLastCalledWith('otroCodigo');
      expect(servicios(p.el).every(b => b.getAttribute('aria-pressed') === 'false')).toBe(true);
    });
  });

  describe('flujo de reserva', () => {
    it('muestra el día en español, no deja ir antes de hoy y para a los 30 días', async () => {
      const p = await abrir();
      await elegirServicio(p, 'Sesión estándar');
      const dia = () => p.el.querySelector('[data-testid="dia"]')!.textContent!.trim();
      const anterior = () => p.el.querySelector<HTMLButtonElement>('button[aria-label="Día anterior"]')!;
      const siguiente = () => p.el.querySelector<HTMLButtonElement>('button[aria-label="Día siguiente"]')!;

      expect(dia()).toBe('Lunes 9 de noviembre');
      expect(anterior().disabled).toBe(true);
      siguiente().click();
      await esperar(p.harness);
      expect(dia()).toBe('Martes 10 de noviembre');
      expect(anterior().disabled).toBe(false);

      for (let i = 0; i < 29; i++) {
        siguiente().click();
        await esperar(p.harness);
      }
      expect(dia()).toBe('Miércoles 9 de diciembre');
      expect(siguiente().disabled).toBe(true);
    });

    it('al elegir servicio y día pide los horarios de esa fecha', async () => {
      const p = await abrir();
      await elegirServicio(p, 'Sesión estándar');
      expect(p.api.horarios).toHaveBeenLastCalledWith(CODIGO, 'srv-estandar', '2026-11-09');
      p.el.querySelector<HTMLButtonElement>('button[aria-label="Día siguiente"]')!.click();
      await esperar(p.harness);
      expect(p.api.horarios).toHaveBeenLastCalledWith(CODIGO, 'srv-estandar', '2026-11-10');
    });

    it('servicio, día, hora y datos válidos confirman la cita con los datos de la respuesta', async () => {
      const api = crearApi();
      api.reservar.mockResolvedValue({
        servicioId: 'srv-estandar', servicio: 'Terapia confirmada por el servidor', fecha: '2026-11-10', inicio: '10:00',
        fin: '11:00', estado: 'Confirmada',
      });
      const p = await abrir(api);
      expect(boton(p.el, 'Continuar').disabled).toBe(true);
      await elegirServicio(p, 'Sesión estándar');
      p.el.querySelector<HTMLButtonElement>('button[aria-label="Día siguiente"]')!.click();
      await esperar(p.harness);
      await elegirHueco(p, '10:00');
      expect(p.el.querySelector('[data-testid="bloque"].elegido')).not.toBeNull();
      expect(boton(p.el, 'Continuar').disabled).toBe(false);

      boton(p.el, 'Continuar').click();
      await esperar(p.harness);
      expect(p.el.textContent).toContain('Martes 10 de noviembre, 10:00 – 11:00');
      escribir(p.el, 'nombre', '  Camila ');
      escribir(p.el, 'apellido', 'Rojas');
      escribir(p.el, 'telefono', '300 123 4567');
      escribir(p.el, 'correo', 'camila@correo.com');
      await enviar(p);

      expect(api.reservar).toHaveBeenCalledExactlyOnceWith(CODIGO, {
        servicioId: 'srv-estandar', fecha: '2026-11-10', inicio: '10:00',
        cliente: { nombre: 'Camila', apellido: 'Rojas', telefono: '300 123 4567', correo: 'camila@correo.com' },
      });
      const texto = p.el.textContent!;
      expect(p.el.querySelector('h1,h2')!.textContent).toContain('Cita confirmada');
      expect(texto).toContain('Confirmada');
      expect(texto).toContain('Terapia confirmada por el servidor'); // el nombre de la respuesta, no el de la selección local
      expect(texto).not.toContain('Sesión estándar');
      expect(texto).toContain('Martes 10 de noviembre');
      expect(texto).toContain('10:00 – 11:00');
      expect(texto).toMatch(/80\D?000/);
      expect(texto).toContain('Camila Rojas');
      expect(boton(p.el, 'Reservar otra cita')).toBeTruthy();
    });

    it('datos incompletos o correo inválido no avanzan y muestran el error junto al campo', async () => {
      const p = await abrir();
      await irAlPaso2(p);

      await enviar(p);
      expect(p.el.querySelector('form')).not.toBeNull();
      expect(errorDe(p.el, 'nombre')).toBe('Escribe tu nombre.');
      expect(errorDe(p.el, 'apellido')).toBe('Escribe tu apellido.');
      expect(errorDe(p.el, 'telefono')).toBe('Escribe tu teléfono.');
      expect(errorDe(p.el, 'correo')).toBe('Escribe un correo válido.');

      llenarDatos(p.el);
      escribir(p.el, 'correo', 'camila@correo');
      await enviar(p);
      expect(errorDe(p.el, 'correo')).toBe('Escribe un correo válido.');
      escribir(p.el, 'correo', 'camila@correo..com'); // la misma regla que Clientes
      await enviar(p);
      expect(errorDe(p.el, 'correo')).toBe('Escribe un correo válido.');
      expect(errorDe(p.el, 'nombre')).toBeUndefined();
      expect(p.api.reservar).not.toHaveBeenCalled();
    });

    it('«Reservar otra cita» vuelve al paso 1 sin servicio ni hora elegidos', async () => {
      const p = await abrir();
      await elegirServicio(p, 'Consulta inicial');
      await elegirHueco(p, '09:00');
      boton(p.el, 'Continuar').click();
      await esperar(p.harness);
      llenarDatos(p.el);
      await enviar(p);
      boton(p.el, 'Reservar otra cita').click();
      await esperar(p.harness);
      expect(servicios(p.el).length).toBe(2);
      expect(servicios(p.el).every(b => b.getAttribute('aria-pressed') === 'false')).toBe(true);
      expect(p.el.querySelector('[data-testid="bloque"].elegido')).toBeNull();
    });

    it('al cambiar de paso el foco pasa al título del paso nuevo', async () => {
      const p = await abrir();
      const titulo = () => p.el.querySelector('h1')!;
      await elegirServicio(p, 'Sesión estándar');
      await elegirHueco(p, '08:00');

      boton(p.el, 'Continuar').click();
      await esperar(p.harness);
      expect(titulo().textContent).toContain('Tus datos');
      expect(document.activeElement).toBe(titulo());

      Array.from(p.el.querySelectorAll('button')).find(b => b.textContent?.includes('Cambiar horario'))!.click();
      await esperar(p.harness);
      expect(titulo().textContent).toContain('Reserva tu cita');
      expect(document.activeElement).toBe(titulo());

      boton(p.el, 'Continuar').click();
      await esperar(p.harness);
      llenarDatos(p.el);
      await enviar(p);
      expect(titulo().textContent).toContain('Cita confirmada');
      expect(document.activeElement).toBe(titulo());

      boton(p.el, 'Reservar otra cita').click();
      await esperar(p.harness);
      expect(document.activeElement).toBe(titulo());
    });

    it('un envío con errores lleva el foco al primer campo inválido', async () => {
      const p = await abrir();
      await irAlPaso2(p);

      await enviar(p);
      expect(document.activeElement).toBe(p.el.querySelector('input[name="nombre"]'));

      escribir(p.el, 'nombre', 'Camila');
      escribir(p.el, 'apellido', 'Rojas');
      escribir(p.el, 'telefono', '300 123 4567');
      escribir(p.el, 'correo', 'sin-arroba');
      await enviar(p);
      expect(document.activeElement).toBe(p.el.querySelector('input[name="correo"]'));
    });

    it('tiene un enlace discreto «Acceso del negocio» hacia el inicio de sesión', async () => {
      const { el } = await abrir();
      const enlace = Array.from(el.querySelectorAll('a')).find(a => a.textContent?.trim() === 'Acceso del negocio')!;
      expect(enlace.getAttribute('href')).toBe('/login');
    });
  });

  describe('respuestas que llegan tarde', () => {
    function manual<T>() {
      let resolver!: (v: T) => void;
      let rechazar!: (e: unknown) => void;
      const promesa = new Promise<T>((res, rej) => {
        resolver = res;
        rechazar = rej;
      });
      return { promesa, resolver, rechazar };
    }
    const textos = (el: HTMLElement) => huecos(el).map(h => h.textContent?.trim());
    const clicServicio = (el: HTMLElement, nombre: string) =>
      servicios(el).find(b => b.textContent?.includes(nombre))!.click();
    const enviarYa = (el: HTMLElement) => el.querySelector('form')!.dispatchEvent(new Event('submit'));

    it('una respuesta de horarios vieja (otro servicio) no pisa la lista nueva', async () => {
      const api = crearApi();
      const vieja = manual<string[]>();
      api.horarios.mockImplementation(async (_c, servicioId) =>
        servicioId === 'srv-estandar' ? vieja.promesa : ['09:00', '09:30', '10:00']);
      const p = await abrir(api);
      clicServicio(p.el, 'Sesión estándar'); // queda retenida
      await esperar(p.harness);
      await elegirServicio(p, 'Consulta inicial');
      expect(textos(p.el)).toEqual(['09:00', '09:30', '10:00']);

      vieja.resolver(['08:00', '11:00']);
      await esperar(p.harness);
      expect(textos(p.el)).toEqual(['09:00', '09:30', '10:00']);
    });

    it('una respuesta de horarios vieja (otra fecha) no pisa la lista nueva', async () => {
      const api = crearApi();
      const vieja = manual<string[]>();
      api.horarios.mockImplementation(async (_c, _s, fecha) =>
        fecha === '2026-11-09' ? vieja.promesa : ['10:00', '11:00']);
      const p = await abrir(api);
      clicServicio(p.el, 'Sesión estándar'); // fecha 9: retenida
      await esperar(p.harness);
      p.el.querySelector<HTMLButtonElement>('button[aria-label="Día siguiente"]')!.click();
      await esperar(p.harness);
      expect(textos(p.el)).toEqual(['10:00', '11:00']);

      vieja.resolver(['08:00']);
      await esperar(p.harness);
      expect(textos(p.el)).toEqual(['10:00', '11:00']);
    });

    it('al cambiar el código de la ruta, los horarios del enlace anterior se descartan', async () => {
      const api = crearApi();
      const vieja = manual<string[]>();
      api.horarios.mockImplementation(async (codigo, servicioId) =>
        codigo === CODIGO ? vieja.promesa : (LIBRES[servicioId] ?? []));
      const p = await abrir(api);
      clicServicio(p.el, 'Sesión estándar');
      await esperar(p.harness);

      await p.harness.navigateByUrl('/agendar/otroCodigo');
      await esperar(p.harness);
      vieja.resolver(['08:00', '09:00']);
      await esperar(p.harness);
      expect(servicios(p.el).every(b => b.getAttribute('aria-pressed') === 'false')).toBe(true);
      expect(huecos(p.el).length).toBe(0);
    });

    it('un 410 de horarios del enlace anterior no cierra el enlace nuevo', async () => {
      const api = crearApi();
      const vieja = manual<string[]>();
      api.horarios.mockImplementation(async (codigo, servicioId) =>
        codigo === CODIGO ? vieja.promesa : (LIBRES[servicioId] ?? []));
      const p = await abrir(api);
      clicServicio(p.el, 'Sesión estándar');
      await esperar(p.harness);

      await p.harness.navigateByUrl('/agendar/otroCodigo');
      await esperar(p.harness);
      vieja.rechazar(new ApiError(410, 'ENLACE_AGOTADO', 'x'));
      await esperar(p.harness);
      expect(p.el.querySelector('h1')!.textContent).toContain('Reserva tu cita');
      expect(servicios(p.el).length).toBe(2);
    });

    it('una reserva en vuelo del enlace anterior no confirma nada en el enlace nuevo', async () => {
      const api = crearApi();
      const enVuelo = manual<CitaPublica>();
      api.reservar.mockReturnValue(enVuelo.promesa);
      const p = await abrir(api);
      await irAlPaso2(p);
      llenarDatos(p.el);
      enviarYa(p.el);
      await esperar(p.harness);
      expect(api.reservar).toHaveBeenCalledTimes(1);

      await p.harness.navigateByUrl('/agendar/otroCodigo');
      await esperar(p.harness);
      enVuelo.resolver({ servicioId: 'srv-estandar', servicio: 'Sesión estándar', fecha: '2026-11-09', inicio: '08:00', fin: '09:00', estado: 'Confirmada' });
      await esperar(p.harness);
      expect(p.el.querySelector('h1')!.textContent).toContain('Reserva tu cita');
      expect(p.el.textContent).not.toContain('Cita confirmada');
      expect(servicios(p.el).length).toBe(2);
    });

    it('un 410 de una reserva en vuelo del enlace anterior no cierra el enlace nuevo', async () => {
      const api = crearApi();
      const enVuelo = manual<CitaPublica>();
      api.reservar.mockReturnValue(enVuelo.promesa);
      const p = await abrir(api);
      await irAlPaso2(p);
      llenarDatos(p.el);
      enviarYa(p.el);
      await esperar(p.harness);

      await p.harness.navigateByUrl('/agendar/otroCodigo');
      await esperar(p.harness);
      enVuelo.rechazar(new ApiError(410, 'ENLACE_AGOTADO', 'x'));
      await esperar(p.harness);
      expect(p.el.querySelector('h1')!.textContent).toContain('Reserva tu cita');
      expect(servicios(p.el).length).toBe(2);
    });
  });

  describe('al enviar', () => {
    it('un doble clic en «Solicitar cita» envía una sola petición y deshabilita el botón mientras tanto', async () => {
      const api = crearApi();
      let terminar!: (c: CitaPublica) => void;
      api.reservar.mockReturnValue(new Promise<CitaPublica>(r => (terminar = r)));
      const p = await abrir(api);
      await irAlPaso2(p);
      llenarDatos(p.el);

      const formulario = p.el.querySelector('form')!;
      formulario.dispatchEvent(new Event('submit'));
      formulario.dispatchEvent(new Event('submit'));
      await esperar(p.harness);
      expect(api.reservar).toHaveBeenCalledTimes(1);
      expect(boton(p.el, 'Solicitar cita').disabled).toBe(true);

      terminar({ servicioId: 'srv-estandar', servicio: 'Sesión estándar', fecha: '2026-11-09', inicio: '08:00', fin: '09:00', estado: 'Confirmada' });
      await esperar(p.harness);
      expect(api.reservar).toHaveBeenCalledTimes(1);
      expect(p.el.querySelector('h1')!.textContent).toContain('Cita confirmada');
    });

    it.each([
      ['ENLACE_AGOTADO', /ya no tiene reservas|agotó/i],
      ['ENLACE_VENCIDO', /venció/i],
      ['ENLACE_DESACTIVADO', /desactivó|desactivado/i],
    ])('un 410 %s a mitad del flujo cierra el formulario con el mensaje del estado', async (codigo, mensaje) => {
      const api = crearApi();
      api.reservar.mockRejectedValue(new ApiError(410, codigo, 'Mensaje de la API.'));
      const p = await abrir(api);
      await irAlPaso2(p);
      llenarDatos(p.el);
      await enviar(p);

      expect(p.el.querySelector('h1')!.textContent).toMatch(mensaje);
      expect(p.el.textContent).toContain('Pide un enlace nuevo');
      expect(p.el.querySelector('form')).toBeNull();
      expect(servicios(p.el).length).toBe(0);
      expect(boton(p.el, 'Solicitar cita')).toBeUndefined();
      expect(document.activeElement).toBe(p.el.querySelector('h1'));
    });

    it('un 409 HORARIO_NO_DISPONIBLE avisa, recarga los horarios de la fecha y conserva lo escrito', async () => {
      const api = crearApi();
      api.reservar.mockRejectedValue(new ApiError(409, 'HORARIO_NO_DISPONIBLE', MENSAJE_OCUPADO));
      const p = await abrir(api);
      await irAlPaso2(p, '08:00');
      llenarDatos(p.el);
      const llamadas = api.horarios.mock.calls.length;
      api.horarios.mockResolvedValue(['09:00', '10:00', '11:00']); // otra persona se llevó las 08:00
      await enviar(p);

      expect(alerta(p.el)).toBe(MENSAJE_OCUPADO);
      expect(p.el.querySelector('form')).toBeNull(); // vuelve a elegir horario
      expect(api.horarios.mock.calls.length).toBe(llamadas + 1);
      expect(api.horarios).toHaveBeenLastCalledWith(CODIGO, 'srv-estandar', '2026-11-09');
      expect(p.el.querySelector('[data-testid="bloque"].elegido')).toBeNull();
      expect(boton(p.el, 'Continuar').disabled).toBe(true);
      expect(huecos(p.el).some(h => h.textContent?.trim() === '08:00')).toBe(false);
      expect(huecos(p.el).length).toBeGreaterThan(0);
      expect(document.activeElement).toBe(p.el.querySelector('h1'));

      await elegirHueco(p, '09:00');
      boton(p.el, 'Continuar').click();
      await esperar(p.harness);
      const valor = (n: string) => p.el.querySelector<HTMLInputElement>(`input[name="${n}"]`)!.value;
      expect([valor('nombre'), valor('apellido'), valor('telefono'), valor('correo')])
        .toEqual(['Camila', 'Rojas', '300 123 4567', 'camila@correo.com']);
    });

    it.each(['cliente.correo', 'correo'])('un 400 con campos (%s) muestra el error junto al campo y conserva lo escrito', async clave => {
      const api = crearApi();
      api.reservar.mockRejectedValue(
        new ApiError(400, 'VALIDACION', 'Hay datos inválidos en la petición.', { [clave]: 'El correo no es válido.' }),
      );
      const p = await abrir(api);
      await irAlPaso2(p);
      llenarDatos(p.el);
      await enviar(p);

      expect(p.el.querySelector('form')).not.toBeNull();
      expect(errorDe(p.el, 'correo')).toBe('El correo no es válido.');
      expect(p.el.querySelector('input[name="correo"]')!.getAttribute('aria-invalid')).toBe('true');
      expect(errorDe(p.el, 'nombre')).toBeUndefined();
      expect(alerta(p.el)).toBeUndefined();
      expect(p.el.querySelector<HTMLInputElement>('input[name="nombre"]')!.value).toBe('Camila');
      expect(document.activeElement).toBe(p.el.querySelector('input[name="correo"]'));

      escribir(p.el, 'correo', 'otro@correo.com'); // al corregir el campo, el error del servidor se va
      await esperar(p.harness);
      expect(errorDe(p.el, 'correo')).toBeUndefined();
    });

    it('un 400 con una clave que no es de un campo del formulario se muestra como error general', async () => {
      const api = crearApi();
      api.reservar.mockRejectedValue(
        new ApiError(400, 'VALIDACION', 'Hay datos inválidos en la petición.', { fecha: 'Elige la fecha.' }),
      );
      const p = await abrir(api);
      await irAlPaso2(p);
      llenarDatos(p.el);
      await enviar(p);
      expect(alerta(p.el)).toBe('Elige la fecha.');
      expect(p.el.querySelector('form')).not.toBeNull();
    });

    it('un 400 sin campos se muestra como error general y el formulario sigue abierto', async () => {
      const api = crearApi();
      api.reservar.mockRejectedValue(new ApiError(400, 'VALIDACION', 'La fecha no puede ser anterior a hoy.'));
      const p = await abrir(api);
      await irAlPaso2(p);
      llenarDatos(p.el);
      await enviar(p);

      expect(alerta(p.el)).toBe('La fecha no puede ser anterior a hoy.');
      expect(p.el.querySelector('.campo-error')).toBeNull();
      expect(p.el.querySelector('form')).not.toBeNull();
      expect(p.el.querySelector<HTMLInputElement>('input[name="telefono"]')!.value).toBe('300 123 4567');
      expect(boton(p.el, 'Solicitar cita').disabled).toBe(false);
    });

    it('un fallo inesperado (sin red) avisa, conserva lo escrito y permite reintentar', async () => {
      const api = crearApi();
      api.reservar.mockRejectedValueOnce(new Error('boom'));
      const p = await abrir(api);
      await irAlPaso2(p);
      llenarDatos(p.el);
      await enviar(p);
      expect(alerta(p.el)).toBe('No pudimos reservar tu cita. Intenta de nuevo.');
      expect(p.el.querySelector<HTMLInputElement>('input[name="nombre"]')!.value).toBe('Camila');

      await enviar(p);
      expect(api.reservar).toHaveBeenCalledTimes(2);
      expect(p.el.querySelector('h1')!.textContent).toContain('Cita confirmada');
    });
  });
});

describe('AgendarComponent con los interceptores reales', () => {
  it('ninguna llamada de la zona pública lleva Authorization, aunque haya sesión del negocio iniciada', async () => {
    sessionStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'agendar/:codigo', component: AgendarComponent }]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        { provide: RELOJ, useValue: () => AHORA },
      ],
    });
    // Sesión del negocio abierta en esta misma pestaña (primero la sesión, luego el token, para que no pida /auth/yo).
    TestBed.inject(SesionService).iniciar(MARTA, ROL_ADMIN);
    TestBed.inject(TokenStore).guardar('jwt-del-negocio');
    const ctrl = TestBed.inject(HttpTestingController);

    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/agendar/${CODIGO}`, AgendarComponent);
    const el = harness.routeNativeElement as HTMLElement;
    const pantalla = { harness, el } as Pantalla;

    function atender(url: string, cuerpo: object, estado = 200) {
      const req = ctrl.expectOne(url);
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush(cuerpo, { status: estado, statusText: 'OK' });
    }

    atender(BASE, { estado: 'Activo', venceEn: '2026-11-10T08:00:00Z' });
    await esperar(harness);
    atender(`${BASE}/servicios`, [CONSULTA, ESTANDAR]);
    await esperar(harness);

    await elegirServicio(pantalla, 'Sesión estándar');
    atender(`${BASE}/servicios/srv-estandar/horarios?fecha=2026-11-09`, LIBRES['srv-estandar']);
    await esperar(harness);
    await elegirHueco(pantalla, '08:00');
    boton(el, 'Continuar').click();
    await esperar(harness);
    llenarDatos(el);
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await esperar(harness);
    atender(`${BASE}/citas`, {
      servicioId: 'srv-estandar', servicio: 'Sesión estándar', fecha: '2026-11-09', inicio: '08:00', fin: '09:00',
      estado: 'Confirmada',
    }, 201);
    await esperar(harness);
    expect(el.querySelector('h1')!.textContent).toContain('Cita confirmada');
    ctrl.verify();

    // Control: en este mismo entorno el panel sí lleva el Bearer, así que la prueba de arriba no es vacía.
    TestBed.inject(HttpClient).get(`${API}/panel/servicios`).subscribe();
    const panel = ctrl.expectOne(`${API}/panel/servicios`);
    expect(panel.request.headers.get('Authorization')).toBe('Bearer jwt-del-negocio');
    panel.flush([]);
  });

  it('un 410 real del POST (ENLACE_AGOTADO) cierra el formulario con el mensaje del estado', async () => {
    sessionStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'agendar/:codigo', component: AgendarComponent }]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        { provide: RELOJ, useValue: () => AHORA },
      ],
    });
    const ctrl = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/agendar/${CODIGO}`, AgendarComponent);
    const el = harness.routeNativeElement as HTMLElement;
    const pantalla = { harness, el } as Pantalla;

    ctrl.expectOne(BASE).flush({ estado: 'Activo', venceEn: '2026-11-10T08:00:00Z' });
    await esperar(harness);
    ctrl.expectOne(`${BASE}/servicios`).flush([ESTANDAR]);
    await esperar(harness);
    await elegirServicio(pantalla, 'Sesión estándar');
    ctrl.expectOne(`${BASE}/servicios/srv-estandar/horarios?fecha=2026-11-09`).flush(LIBRES['srv-estandar']);
    await esperar(harness);
    await elegirHueco(pantalla, '08:00');
    boton(el, 'Continuar').click();
    await esperar(harness);
    llenarDatos(el);
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await esperar(harness);
    ctrl.expectOne(`${BASE}/citas`).flush(
      { codigo: 'ENLACE_AGOTADO', mensaje: 'Este enlace ya no tiene reservas disponibles.' },
      { status: 410, statusText: 'Gone' },
    );
    await esperar(harness);

    expect(el.querySelector('form')).toBeNull();
    expect(el.querySelector('h1')!.textContent).toMatch(/ya no tiene reservas|agotó/i);
  });
});
