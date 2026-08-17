import { DestroyRef, Injectable, Signal, computed, inject, signal } from '@angular/core';
import { interval } from 'rxjs';

const MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

/**
 * Reloj en vivo compartido por toda la app. Un unico `interval(1000)` para el
 * reloj de la barra superior y el de las pantallas de acceso: si cada
 * componente montara el suyo tendriamos varios temporizadores desincronizados
 * pintando la misma hora.
 */
@Injectable({ providedIn: 'root' })
export class ClockService {
  private readonly destroyRef = inject(DestroyRef);
  private readonly current = signal(Date.now());

  /** Milisegundos epoch, refrescados cada segundo. */
  readonly now: Signal<number> = this.current.asReadonly();

  /**
   * Fraccion transcurrida del dia local (0 a las 00:00, 1 a las 24:00). La usa
   * la hairline de las pantallas de acceso para medir el dia.
   */
  readonly dayProgress = computed(() => {
    const date = new Date(this.current());
    const millisSinceMidnight =
      date.getHours() * 3600000 +
      date.getMinutes() * 60000 +
      date.getSeconds() * 1000 +
      date.getMilliseconds();
    return millisSinceMidnight / MILLIS_PER_DAY;
  });

  /** Zona horaria del navegador, la que interpreta las horas que se muestran. */
  readonly timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  constructor() {
    const subscription = interval(1000).subscribe(() => this.current.set(Date.now()));
    this.destroyRef.onDestroy(() => subscription.unsubscribe());
  }
}
