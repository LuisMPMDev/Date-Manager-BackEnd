import { nuevoId } from './ids';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('nuevoId', () => {
  it('produce un UUID v4 válido', () => {
    expect(nuevoId()).toMatch(UUID_V4);
  });

  it('produce uno distinto cada vez', () => {
    const ids = new Set(Array.from({ length: 50 }, () => nuevoId()));
    expect(ids.size).toBe(50);
  });
});
