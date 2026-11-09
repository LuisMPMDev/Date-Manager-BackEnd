import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { appConfig } from './app.config';
import { AgendaRepo } from './core/agenda-repo';
import { HttpAgendaRepo } from './core/http-agenda-repo';
import { SesionExpirada } from './core/sesion-expirada';
import { SesionService } from './core/sesion';

describe('appConfig', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [...appConfig.providers] });
    await TestBed.inject(ApplicationInitStatus).donePromise;
  });

  it('provee el repositorio HTTP, no la demo', () => {
    expect(TestBed.inject(AgendaRepo)).toBeInstanceOf(HttpAgendaRepo);
  });

  it('construye la sesión al arrancar: ya escucha un 401 que llegue antes de la primera guarda', () => {
    // Si la sesión se creara perezosamente, el aviso se perdería (nadie lo escucharía todavía).
    TestBed.inject(SesionExpirada).emitir();
    expect(TestBed.inject(SesionService).aviso()).toBe('Tu sesión venció. Inicia sesión de nuevo.');
  });
});
