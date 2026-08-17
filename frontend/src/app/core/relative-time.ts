const MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

/**
 * «hace 8 meses», «hace 2 horas». La unidad se elige por magnitud para que la
 * cifra sea corta: en una tabla importa el orden de magnitud, no la precisión.
 *
 * <p>`reference` es el «ahora» contra el que se mide, y se pasa a propósito en
 * lugar de leer el reloj aquí dentro: las pantallas lo congelan en cada carga
 * para no repintar la tabla entera cada segundo.
 */
export function relativeTime(iso: string, reference: number): string {
  const elapsed = reference - Date.parse(iso);
  const format = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });

  const minutes = Math.round(elapsed / 60000);
  if (Math.abs(minutes) < 60) {
    return format.format(-minutes, 'minute');
  }
  const hours = Math.round(elapsed / 3600000);
  if (Math.abs(hours) < 24) {
    return format.format(-hours, 'hour');
  }
  const days = Math.round(elapsed / MILLIS_PER_DAY);
  if (Math.abs(days) < 31) {
    return format.format(-days, 'day');
  }
  const months = Math.round(days / 30);
  if (Math.abs(months) < 12) {
    return format.format(-months, 'month');
  }
  return format.format(-Math.round(days / 365), 'year');
}

/** Días completos transcurridos desde `iso`. */
export function daysSince(iso: string, reference: number): number {
  return Math.floor((reference - Date.parse(iso)) / MILLIS_PER_DAY);
}
