import { TestBed } from '@angular/core/testing';
import { AhoraService } from './ahora';
import { RELOJ } from './errores';

describe('AhoraService', () => {
  let hora: Date;
  let lecturas: number;

  beforeEach(() => {
    vi.useFakeTimers();
    hora = new Date(2026, 10, 9, 8, 0);
    lecturas = 0;
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: RELOJ,
          useValue: () => {
            lecturas++;
            return new Date(hora);
          },
        },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('arranca con la hora del reloj', () => {
    expect(TestBed.inject(AhoraService).ahora()).toEqual(new Date(2026, 10, 9, 8, 0));
  });

  it('se renueva cada 60 segundos con la hora del reloj', () => {
    const servicio = TestBed.inject(AhoraService);
    hora = new Date(2026, 10, 9, 8, 1);

    vi.advanceTimersByTime(59_000);
    expect(servicio.ahora()).toEqual(new Date(2026, 10, 9, 8, 0));

    vi.advanceTimersByTime(1_000);
    expect(servicio.ahora()).toEqual(new Date(2026, 10, 9, 8, 1));

    hora = new Date(2026, 10, 9, 8, 2);
    vi.advanceTimersByTime(60_000);
    expect(servicio.ahora()).toEqual(new Date(2026, 10, 9, 8, 2));
  });

  it('actualizar vuelve a leer el reloj al instante', () => {
    const servicio = TestBed.inject(AhoraService);
    hora = new Date(2026, 10, 9, 8, 30);
    expect(servicio.ahora()).toEqual(new Date(2026, 10, 9, 8, 0));
    servicio.actualizar();
    expect(servicio.ahora()).toEqual(new Date(2026, 10, 9, 8, 30));
  });

  it('al destruirse deja de leer el reloj', () => {
    TestBed.inject(AhoraService);
    const antes = lecturas;
    TestBed.resetTestingModule();
    vi.advanceTimersByTime(5 * 60_000);
    expect(lecturas).toBe(antes);
  });
});
