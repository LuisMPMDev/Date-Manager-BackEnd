import { construirRegla } from './regla-bloques';

const VENTANA_MANANA = { inicio: '09:00', fin: '13:00' };

describe('construirRegla', () => {
  it('sin ventanas ese día no hay regla', () => {
    expect(construirRegla({ ventanas: [], duracionMin: 60, libres: [], elegido: null })).toBeNull();
  });

  it('el rango cubre las ventanas, redondeado hacia afuera a horas completas', () => {
    const r = construirRegla({
      ventanas: [{ inicio: '08:30', fin: '12:15' }],
      duracionMin: 60,
      libres: [],
      elegido: null,
    })!;
    expect([r.desdeMin, r.hastaMin]).toEqual([480, 780]);
  });

  it('cada horario libre es un hueco de 30 min con su hora como etiqueta', () => {
    const r = construirRegla({
      ventanas: [VENTANA_MANANA],
      duracionMin: 60,
      libres: ['09:00', '09:30'],
      elegido: null,
    })!;
    expect(r.huecos).toEqual([
      { desdeMin: 540, hastaMin: 570, etiqueta: '09:00' },
      { desdeMin: 570, hastaMin: 600, etiqueta: '09:30' },
    ]);
  });

  it('dibuja como ocupado lo de la ventana que ningún horario ofrecido cubre', () => {
    // Un servicio de 60 min libre a las 10:00 cubre 10:00-11:00; de la ventana 09:00-13:00 quedan 09-10 y 11-13.
    const r = construirRegla({ ventanas: [VENTANA_MANANA], duracionMin: 60, libres: ['10:00'], elegido: null })!;
    const ocupados = r.bloques.filter(b => b.tipo === 'ocupado').map(b => [b.desdeMin, b.hastaMin]);
    expect(ocupados).toEqual([[540, 600], [660, 780]]);
  });

  it('si los horarios libres cubren toda la ventana no hay bloques ocupados', () => {
    const r = construirRegla({
      ventanas: [{ inicio: '09:00', fin: '11:00' }],
      duracionMin: 60,
      libres: ['09:00', '09:30', '10:00'],
      elegido: null,
    })!;
    expect(r.bloques).toEqual([]);
  });

  it('marca como fuera lo que no pertenece a ninguna ventana dentro del rango', () => {
    const r = construirRegla({
      ventanas: [{ inicio: '09:00', fin: '12:00' }, { inicio: '14:00', fin: '16:00' }],
      duracionMin: 60,
      libres: ['09:00', '09:30', '10:00', '10:30', '11:00', '14:00', '14:30', '15:00'],
      elegido: null,
    })!;
    const fuera = r.bloques.filter(b => b.tipo === 'fuera').map(b => [b.desdeMin, b.hastaMin]);
    expect(fuera).toEqual([[720, 840]]);
  });

  it('el horario elegido es un bloque tan alto como la duración del servicio', () => {
    const r = construirRegla({
      ventanas: [VENTANA_MANANA],
      duracionMin: 90,
      libres: ['10:00'],
      elegido: '10:00',
    })!;
    const elegido = r.bloques.find(b => b.tipo === 'elegido')!;
    expect([elegido.desdeMin, elegido.hastaMin]).toEqual([600, 690]);
    expect(elegido.etiqueta).toBe('10:00 – 11:30');
  });
});
