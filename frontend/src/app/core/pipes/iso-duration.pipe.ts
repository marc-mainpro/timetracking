import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formato de salida del pipe:
 * - `hms`  → `HH:MM:SS` (por defecto)
 * - `hm`   → `HH:MM`
 * - `long` → `7h 30min` (omite las partes en cero)
 */
export type IsoDurationFormat = 'hms' | 'hm' | 'long';

// Duración ISO-8601 tal y como la serializa `java.time.Duration`, p.ej.
// "PT7.662757S", "PT8H30M" o "P1DT2H". Aceptamos días opcionales por robustez.
const ISO_DURATION_PATTERN = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$/;

/**
 * Convierte una duración ISO-8601 a segundos totales, o `null` si el valor
 * no es una duración válida (o no contiene ningún componente).
 */
export function isoDurationToSeconds(value: string | null | undefined): number | null {
  if (!value) {
    return null;
  }
  const match = ISO_DURATION_PATTERN.exec(value);
  if (!match || (!match[1] && !match[2] && !match[3] && !match[4])) {
    return null;
  }
  const days = Number(match[1] ?? 0);
  const hours = Number(match[2] ?? 0);
  const minutes = Number(match[3] ?? 0);
  const seconds = Number(match[4] ?? 0);
  return days * 86_400 + hours * 3_600 + minutes * 60 + seconds;
}

/**
 * Formatea una duración ISO-8601 al formato indicado. Si el valor no es
 * válido devuelve el `fallback` propio de cada formato.
 */
export function formatIsoDuration(value: string | null | undefined, format: IsoDurationFormat = 'hms'): string {
  const totalSeconds = isoDurationToSeconds(value);
  const seconds = totalSeconds === null ? 0 : Math.floor(totalSeconds);
  const hours = Math.floor(seconds / 3_600);
  const minutes = Math.floor((seconds % 3_600) / 60);
  const secs = seconds % 60;

  if (format === 'long') {
    if (totalSeconds === null || seconds === 0) {
      return '0min';
    }
    const parts: string[] = [];
    if (hours > 0) {
      parts.push(`${hours}h`);
    }
    if (minutes > 0) {
      parts.push(`${minutes}min`);
    }
    if (secs > 0 && hours === 0) {
      parts.push(`${secs}s`);
    }
    return parts.join(' ');
  }

  const pad = (n: number): string => n.toString().padStart(2, '0');
  if (format === 'hm') {
    return `${pad(hours)}:${pad(minutes)}`;
  }
  return `${pad(hours)}:${pad(minutes)}:${pad(secs)}`;
}

@Pipe({
  name: 'isoDuration'
})
export class IsoDurationPipe implements PipeTransform {
  transform(value: string | null | undefined, format: IsoDurationFormat = 'hms'): string {
    return formatIsoDuration(value, format);
  }
}
