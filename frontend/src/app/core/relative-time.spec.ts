import { daysSince, relativeTime } from './relative-time';

describe('relativeTime', () => {
  const now = Date.parse('2026-08-17T12:00:00Z');

  function ago(millis: number): string {
    return new Date(now - millis).toISOString();
  }

  it('usa minutos por debajo de una hora', () => {
    expect(relativeTime(ago(20 * 60000), now)).toContain('20 minutos');
  });

  it('usa horas por debajo de un día', () => {
    expect(relativeTime(ago(5 * 3600000), now)).toContain('5 horas');
  });

  it('usa días por debajo de un mes', () => {
    expect(relativeTime(ago(9 * 24 * 3600000), now)).toContain('9 días');
  });

  it('pasa a meses y años cuando la distancia crece', () => {
    expect(relativeTime(ago(120 * 24 * 3600000), now)).toContain('meses');
    expect(relativeTime(ago(800 * 24 * 3600000), now)).toContain('años');
  });

  it('cuenta días completos, sin redondear hacia arriba', () => {
    expect(daysSince(ago(47 * 3600000), now)).toBe(1);
    expect(daysSince(ago(49 * 3600000), now)).toBe(2);
  });
});
