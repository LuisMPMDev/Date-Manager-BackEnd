import { aFechaISO, fechaLarga, moneda, sumarDias } from './formato';

describe('formato', () => {
  it('aFechaISO usa la fecha local con ceros', () => {
    expect(aFechaISO(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });

  it('sumarDias cruza meses y años', () => {
    expect(sumarDias('2026-11-09', 1)).toBe('2026-11-10');
    expect(sumarDias('2026-11-09', 30)).toBe('2026-12-09');
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01');
    expect(sumarDias('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('fechaLarga escribe «Martes 10 de noviembre» sin depender del idioma del entorno', () => {
    expect(fechaLarga('2026-11-10')).toBe('Martes 10 de noviembre');
    expect(fechaLarga('2026-11-08')).toBe('Domingo 8 de noviembre');
    expect(fechaLarga('2026-01-01')).toBe('Jueves 1 de enero');
  });

  it('moneda da pesos colombianos sin decimales', () => {
    expect(moneda(80000)).toMatch(/80\D?000/);
    expect(moneda(80000)).not.toMatch(/[,.]\d{2}$/);
  });
});
