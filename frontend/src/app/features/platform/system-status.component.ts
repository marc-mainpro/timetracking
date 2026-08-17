import { Component, inject, signal } from '@angular/core';

import { relativeTime } from '../../core/relative-time';
import { ErrorMessagesService } from '../../core/services/error-messages.service';
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
