import { Component, inject, signal } from '@angular/core';

import { relativeTime } from '../../core/relative-time';
import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { FailedQueueComponent } from './failed-queue.component';
import { SystemStatus, SystemStatusService } from './system-status.service';

const QUEUE_LABELS: Record<string, string> = {
  outbox: 'Eventos de integración',
  notifications: 'Envío de notificaciones'
};

/** Qué hace cada cola, para quien mira el panel sin conocer el backend. */
const QUEUE_HINTS: Record<string, string> = {
  outbox: 'Salida hacia los sistemas conectados',
  notifications: 'Avisos por correo y en la aplicación'
};

@Component({
  selector: 'app-system-status',
  standalone: true,
  imports: [FailedQueueComponent],
  templateUrl: './system-status.component.html',
  styleUrl: './system-status.component.scss'
})
export class SystemStatusComponent {
  private readonly systemStatusService = inject(SystemStatusService);
  private readonly errorMessagesService = inject(ErrorMessagesService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly status = signal<SystemStatus | null>(null);

  /** Instante de la última respuesta: un panel de estado sin fecha engaña. */
  private readonly checkedAt = signal<number | null>(null);

  /** Cola cuyo detalle está desplegado; null si ninguna. */
  readonly expandedQueue = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.systemStatusService.get().subscribe({
      next: (status) => {
        this.status.set(status);
        this.checkedAt.set(Date.now());
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.errorMessagesService.fromProblem(err.error));
      }
    });
  }

  /**
   * Despliega o pliega el detalle de una cola. Solo tiene sentido sobre las que
   * tienen algo fallido: en las demás no hay nada sobre lo que intervenir.
   */
  toggleQueue(name: string): void {
    this.expandedQueue.update((current) => (current === name ? null : name));
  }

  /**
   * Tras reintentar o descartar algo, el veredicto de arriba ya no es cierto.
   * Recargarlo es lo que impide que la pantalla siga diciendo que hay una
   * incidencia que acaba de resolverse.
   */
  onQueueChanged(): void {
    this.load();
  }

  queueLabel(name: string): string {
    return QUEUE_LABELS[name] ?? name;
  }

  queueHint(name: string): string | null {
    return QUEUE_HINTS[name] ?? null;
  }

  /** «Consultado hace 2 minutos»; null mientras no haya habido respuesta. */
  checkedLabel(): string | null {
    const checked = this.checkedAt();
    return checked === null ? null : relativeTime(new Date(checked).toISOString(), Date.now());
  }
}
