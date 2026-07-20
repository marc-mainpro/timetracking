const ISO_DURATION_PATTERN = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?$/;

/**
 * Formatea una duración ISO-8601 (p.ej. "PT8H30M", tal y como la serializa
 * `java.time.Duration` en las respuestas del backend) como `HH:MM`.
 */
export function formatIsoDuration(value: string): string {
  const match = ISO_DURATION_PATTERN.exec(value ?? '');
  if (!match) {
    return '00:00';
  }
  const hours = Number(match[1] ?? 0);
  const minutes = Number(match[2] ?? 0);
  const seconds = Number(match[3] ?? 0);
  const totalMinutes = hours * 60 + minutes + Math.floor(seconds / 60);
  const displayHours = Math.floor(totalMinutes / 60);
  const displayMinutes = totalMinutes % 60;
  return `${displayHours.toString().padStart(2, '0')}:${displayMinutes.toString().padStart(2, '0')}`;
}
