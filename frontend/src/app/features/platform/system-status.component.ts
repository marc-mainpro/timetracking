import { Component, inject, signal } from '@angular/core';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { SystemStatus, SystemStatusService } from './system-status.service';

const QUEUE_LABELS: Record<string, string> = {
  outbox: 'Eventos de integración',
  notifications: 'Envío de notificaciones'
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

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.systemStatusService.get().subscribe({
      next: (status) => {
        this.status.set(status);
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
}
