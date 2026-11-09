import { fechaHora } from './enlaces-formato';

/** El texto exacto lo fija la zona; se toleran las variantes de ICU («p. m.», «GMT-5» o «COT»). */
describe('fechaHora', () => {
  const INSTANTE = '2026-11-10T15:30:00Z';

  it('en UTC muestra día, mes, año, hora y el nombre de la zona', () => {
    expect(fechaHora(INSTANTE, 'UTC').replace(/\s+/g, ' ')).toMatch(
      /^10 (de )?nov\.? (de )?2026,? 0?3:30 p\.? ?m\.? (UTC|GMT)$/,
    );
  });

  it('en Bogotá (UTC-5) es cinco horas antes y lo dice', () => {
    expect(fechaHora(INSTANTE, 'America/Bogota').replace(/\s+/g, ' ')).toMatch(
      /^10 (de )?nov\.? (de )?2026,? 10:30 a\.? ?m\.? (COT|GMT-5)$/,
    );
  });

  it('sin zona usa la del navegador y también lleva su nombre', () => {
    expect(fechaHora(INSTANTE)).toMatch(/2026/);
    expect(fechaHora(INSTANTE)).toMatch(/[A-Z]{3,5}(?:[+-]\d{1,2})?$/);
  });
});
