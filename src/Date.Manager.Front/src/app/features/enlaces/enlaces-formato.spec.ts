import { fechaHora } from './enlaces-formato';

/**
 * Todas las pruebas fijan la zona, así que no dependen de la del equipo. El texto exacto lo decide ICU
 * (varía «p. m.» y «COT» frente a «GMT-5»), por eso se normalizan los espacios y se aceptan ambas formas.
 */
describe('fechaHora', () => {
  const INSTANTE = '2026-11-10T15:30:00Z';
  const normal = (zona: string) => fechaHora(INSTANTE, zona).replace(/\s+/g, ' ');

  it('en UTC muestra día, mes, año, hora y el nombre de la zona', () => {
    expect(normal('UTC')).toMatch(/^10 (de )?nov\.? (de )?2026,? 0?3:30 p\.? ?m\.? (UTC|GMT)$/);
  });

  it('en Bogotá (UTC-5) es cinco horas antes y lo dice', () => {
    expect(normal('America/Bogota')).toMatch(/^10 (de )?nov\.? (de )?2026,? 10:30 a\.? ?m\.? (COT|GMT-5|UTC-5)$/);
  });

  it('en la India (UTC+5:30) respeta la media hora', () => {
    expect(normal('Asia/Kolkata')).toMatch(/^10 (de )?nov\.? (de )?2026,? 0?9:00 p\.? ?m\.? (IST|GMT\+5:30|UTC\+5:30)$/);
  });

  it('en Madrid (UTC+1 en noviembre)', () => {
    expect(normal('Europe/Madrid')).toMatch(/^10 (de )?nov\.? (de )?2026,? 0?4:30 p\.? ?m\.? (CET|GMT\+1|UTC\+1)$/);
  });

  it('sin zona usa la del navegador', () => {
    const zona = Intl.DateTimeFormat().resolvedOptions().timeZone;
    expect(fechaHora(INSTANTE)).toBe(fechaHora(INSTANTE, zona));
  });
});
