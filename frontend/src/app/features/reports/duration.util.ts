import { formatIsoDuration as format } from '../../core/pipes/iso-duration.pipe';

/**
 * Formatea una duración ISO-8601 (p.ej. "PT8H30M", tal y como la serializa
 * `java.time.Duration` en las respuestas del backend) como `HH:MM`.
 *
 * @deprecated Usa el pipe `isoDuration` en plantillas o `formatIsoDuration`
 * de `core/pipes/iso-duration.pipe`. Se mantiene por compatibilidad.
 */
export function formatIsoDuration(value: string): string {
  return format(value, 'hm');
}
