import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import {
  AppNotification,
  NotificationType,
  NotificationsService,
  PagedNotifications
} from './notifications.service';

const TYPE_LABELS: Record<NotificationType, string> = {
  WORKDAY_ANOMALY_DETECTED: 'Incidencia en jornada',
  CORRECTION_APPROVED: 'Corrección aprobada',
  CORRECTION_REJECTED: 'Corrección rechazada',
  ABSENCE_APPROVED: 'Ausencia aprobada',
  ABSENCE_REJECTED: 'Ausencia rechazada'
};

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss'
})
export class NotificationsComponent {
  private readonly notificationsService = inject(NotificationsService);
  private readonly errorMessagesService = inject(ErrorMessagesService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly page = signal(0);
  readonly result = signal<PagedNotifications | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.notificationsService.list(this.page(), 20).subscribe({
      next: (result) => {
        this.result.set(result);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.errorMessagesService.fromProblem(err.error));
      }
    });
  }

  markRead(notification: AppNotification): void {
    if (notification.read) {
      return;
    }
    this.notificationsService.markRead(notification.id).subscribe({
      next: () => this.load(),
      error: (err) => this.error.set(this.errorMessagesService.fromProblem(err.error))
    });
  }

  typeLabel(type: NotificationType): string {
    return TYPE_LABELS[type] ?? type;
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.page.set(this.page() - 1);
      this.load();
    }
  }

  nextPage(): void {
    const result = this.result();
    if (result && result.page + 1 < result.totalPages) {
      this.page.set(this.page() + 1);
      this.load();
    }
  }
}
