import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { conSesion } from './core/guards';
import { PublicoApi } from './core/publico-api';

describe('rutas de la aplicación', () => {
  it('ya no existe /reservar', () => {
    expect(routes.some(r => r.path === 'reservar')).toBe(false);
  });

  it('la raíz y cualquier ruta desconocida llevan a login', () => {
    const raiz = routes.find(r => r.path === '' && r.redirectTo !== undefined)!;
    expect(raiz.redirectTo).toBe('login');
    expect(raiz.pathMatch).toBe('full');
    expect(routes.find(r => r.path === '**')!.redirectTo).toBe('login');
  });

  it('agendar/:codigo es pública: sin guardias ni hijas bajo el panel', () => {
    const ruta = routes.find(r => r.path === 'agendar/:codigo')!;
    expect(ruta).toBeDefined();
    expect(ruta.canActivate).toBeUndefined();
    expect(ruta.canActivateChild).toBeUndefined();
    expect(ruta.loadComponent).toBeTypeOf('function');
    const panel = routes.find(r => r.component !== undefined && r.canActivate?.includes(conSesion))!;
    expect(panel.children!.some(r => r.path === 'agendar/:codigo')).toBe(false);
  });

  describe('navegando', () => {
    async function arrancar() {
      sessionStorage.clear();
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          provideRouter(routes),
          provideHttpClient(),
          provideHttpClientTesting(),
          // La pantalla pública no es lo que se prueba aquí: solo que se llegue a ella sin sesión.
          { provide: PublicoApi, useValue: { estado: () => new Promise(() => undefined) } },
        ],
      });
      return RouterTestingHarness.create();
    }

    it('/ lleva a /login', async () => {
      const harness = await arrancar();
      await harness.navigateByUrl('/');
      expect(TestBed.inject(Router).url).toBe('/login');
    });

    it('/reservar ya no existe: cae en /login', async () => {
      const harness = await arrancar();
      await harness.navigateByUrl('/reservar');
      expect(TestBed.inject(Router).url).toBe('/login');
    });

    it('/agendar/<código> se abre sin sesión y no redirige', async () => {
      const harness = await arrancar();
      await harness.navigateByUrl('/agendar/abc123');
      expect(TestBed.inject(Router).url).toBe('/agendar/abc123');
    });
  });
});
