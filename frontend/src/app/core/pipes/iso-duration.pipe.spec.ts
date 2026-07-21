import { IsoDurationPipe, formatIsoDuration, isoDurationToSeconds } from './iso-duration.pipe';

describe('IsoDurationPipe', () => {
  const pipe = new IsoDurationPipe();

  it('formatea segundos fraccionarios como HH:MM:SS por defecto', () => {
    expect(pipe.transform('PT7.662757S')).toBe('00:00:07');
  });

  it('formatea horas y minutos', () => {
    expect(pipe.transform('PT8H30M')).toBe('08:30:00');
    expect(pipe.transform('PT8H30M', 'hm')).toBe('08:30');
  });

  it('soporta días', () => {
    expect(pipe.transform('P1DT2H', 'hm')).toBe('26:00');
  });

  it('usa el formato largo omitiendo ceros', () => {
    expect(pipe.transform('PT8H30M', 'long')).toBe('8h 30min');
    expect(pipe.transform('PT45S', 'long')).toBe('45s');
    expect(pipe.transform('PT0S', 'long')).toBe('0min');
  });

  it('devuelve el fallback ante valores inválidos o nulos', () => {
    expect(pipe.transform('bad-value')).toBe('00:00:00');
    expect(pipe.transform('bad-value', 'hm')).toBe('00:00');
    expect(pipe.transform(null)).toBe('00:00:00');
  });

  it('isoDurationToSeconds devuelve null para valores no válidos', () => {
    expect(isoDurationToSeconds('PT1H')).toBe(3_600);
    expect(isoDurationToSeconds('bad-value')).toBeNull();
    expect(isoDurationToSeconds('')).toBeNull();
  });

  it('formatIsoDuration es la misma lógica que el pipe', () => {
    expect(formatIsoDuration('PT90M', 'hm')).toBe('01:30');
  });
});
