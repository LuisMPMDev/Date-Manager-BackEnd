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

  const tramos = (r: ReturnType<typeof construirRegla>, tipo: string) =>
    r!.bloques.filter(b => b.tipo === tipo).map(b => [b.desdeMin, b.hastaMin]);

  it('cada inicio ofrecido cubre solo su celda de 30 min; el resto de la ventana va ocupado', () => {
    // Libre solo a las 10:00 (servicio de 60 min): de la ventana 09:00-13:00 queda ocupado todo menos 10:00-10:30.
    const r = construirRegla({ ventanas: [VENTANA_MANANA], duracionMin: 60, libres: ['10:00'], elegido: null });
    expect(tramos(r, 'ocupado')).toEqual([[540, 600], [630, 780]]);
  });

  it('si cada inicio de la ventana se ofrece no hay bloques ocupados', () => {
    const r = construirRegla({
      ventanas: [{ inicio: '09:00', fin: '11:00' }],
      duracionMin: 30,
      libres: ['09:00', '09:30', '10:00', '10:30'],
      elegido: null,
    });
    expect(r!.bloques).toEqual([]);
  });

  it('dos tandas libres separadas por un hueco dejan el hueco ocupado', () => {
    const r = construirRegla({
      ventanas: [{ inicio: '09:00', fin: '12:00' }],
      duracionMin: 30,
      libres: ['09:00', '09:30', '11:00', '11:30'],
      elegido: null,
    });
    expect(tramos(r, 'ocupado')).toEqual([[600, 660]]);
  });

  it('con servicio de 90 min el final de la ventana tras el último inicio ofrecido va ocupado', () => {
    // Ventana 09:00-12:00, 90 min: inicios 09:00, 09:30, 10:00, 10:30 (el último cabe hasta 12:00).
    const r = construirRegla({
      ventanas: [{ inicio: '09:00', fin: '12:00' }],
      duracionMin: 90,
      libres: ['09:00', '09:30', '10:00', '10:30'],
      elegido: null,
    });
    expect(tramos(r, 'ocupado')).toEqual([[660, 720]]);
  });

  it('con servicio de 90 min y reservas que quitan inicios, todo minuto no reservable queda rayado', () => {
    // Ventana 09:00-12:00; solo se ofrecen 09:00, 09:30 y 10:00. Lo demás no se puede reservar.
    const r = construirRegla({
      ventanas: [{ inicio: '09:00', fin: '12:00' }],
      duracionMin: 90,
      libres: ['09:00', '09:30', '10:00'],
      elegido: null,
    });
    expect(tramos(r, 'ocupado')).toEqual([[630, 720]]);
    expect(r!.huecos.map(h => h.etiqueta)).toEqual(['09:00', '09:30', '10:00']);
  });

  it('acepta horarios libres desordenados', () => {
    const r = construirRegla({
      ventanas: [VENTANA_MANANA],
      duracionMin: 30,
      libres: ['11:00', '09:00', '10:00'],
      elegido: null,
    });
    expect(tramos(r, 'ocupado')).toEqual([[570, 600], [630, 660], [690, 780]]);
  });

  it('con citas contiguas (inicios no ofrecidos seguidos) forman un solo tramo ocupado', () => {
    const r = construirRegla({
      ventanas: [{ inicio: '09:00', fin: '12:00' }],
      duracionMin: 60,
      libres: ['09:00', '11:00'],
      elegido: null,
    });
    expect(tramos(r, 'ocupado')).toEqual([[570, 660], [690, 720]]);
  });

  it('el elegido se dibuja encima con la altura de la duración', () => {
    const r = construirRegla({ ventanas: [VENTANA_MANANA], duracionMin: 90, libres: ['10:00'], elegido: '10:00' });
    expect(tramos(r, 'elegido')).toEqual([[600, 690]]);
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
