import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, Provider } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AgendaRepo } from '../core/agenda-repo';
import { HttpAgendaRepo } from '../core/http-agenda-repo';
import { authInterceptor, errorInterceptor } from '../core/interceptores';
import { Rol, Usuario } from '../core/models';
import { SesionService } from '../core/sesion';
import { TokenStore } from '../core/token-store';

/** Ayudas de las pruebas de pantallas con el repositorio HTTP real y HttpTestingController (sin pruebas propias). */
export const API = 'http://localhost:8080/api';

@Component({ template: '' })
class Vacia {}

/** Pantalla real + HttpAgendaRepo + interceptores reales; /login y /sin-acceso existen para poder navegar a ellas. */
export function configurarHttp(extra: Provider[] = []): void {
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
      provideHttpClientTesting(),
      provideRouter([
        { path: 'login', component: Vacia },
        { path: 'sin-acceso', component: Vacia },
      ]),
      { provide: AgendaRepo, useClass: HttpAgendaRepo },
      ...extra,
    ],
  });
}

/** Abre sesión con token guardado (como tras un login real) y el rol dado. */
export function abrirSesion(usuario: Usuario, rol: Rol): void {
  const sesion = TestBed.inject(SesionService); // se construye sin token: no intenta restaurar la sesión con GET /auth/yo
  TestBed.inject(TokenStore).guardar('token-de-prueba');
  sesion.iniciar(usuario, rol);
}

export const http = () => TestBed.inject(HttpTestingController);
export const ruta = () => TestBed.inject(Router).url;

export async function esperar(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise(r => setTimeout(r));
    fixture.detectChanges();
  }
}

/** Responde con éxito todas las peticiones pendientes a `/panel/<recurso>` (por lo regular, una). */
export function servir(recurso: string, cuerpo: object | null, metodo = 'GET'): void {
  const pendientes = http().match(r => r.method === metodo && r.url === `${API}/${recurso}`);
  if (!pendientes.length) throw new Error(`No hay ${metodo} pendiente a /${recurso}`);
  for (const p of pendientes) p.flush(cuerpo);
}

/** Responde con un error de la API `{codigo, mensaje, campos?}` todas las peticiones pendientes a ese recurso. */
export function fallar(
  recurso: string, status: number, codigo: string, mensaje: string, campos?: Record<string, string>, metodo = 'GET',
): void {
  const pendientes = http().match(r => r.method === metodo && r.url === `${API}/${recurso}`);
  if (!pendientes.length) throw new Error(`No hay ${metodo} pendiente a /${recurso}`);
  for (const p of pendientes) p.flush({ codigo, mensaje, ...(campos ? { campos } : {}) }, { status, statusText: codigo });
}
