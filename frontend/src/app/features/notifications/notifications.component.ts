import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import {
  AppNotification,
  NotificationType,
  NotificationsService,
  PagedNotifications
} from './notifications.service';

/**
 * Cada tipo del backend, traducido a una píldora corta. Son etiquetas de
 * categoría, no resúmenes: el título de la notificación va justo al lado, así
 * que repetirlo aquí sería ruido.
 */
const TYPE_LABELS: Record<NotificationType, string> = {
  WORKDAY_ANOMALY_DETECTED: 'Tu jornada',
  CORRECTION_APPROVED: 'Corrección resuelta',
  CORRECTION_REJECTED: 'Corrección resuelta',
  ABSENCE_APPROVED: 'Ausencia resuelta',
  ABSENCE_REJECTED: 'Ausencia resuelta',
  ACCOUNT_CREATED: 'Tu cuenta',
  ACCOUNT_DEACTIVATED: 'Tu cuenta',
  SHIFT_ASSIGNED: 'Tu turno',
  CORRECTION_REQUESTED: 'Pendiente de revisar',
  ABSENCE_REQUESTED: 'Pendiente de resolver',
  TEAM_WORKDAY_ANOMALY: 'Jornada de un empleado',
  TENANT_SUSPENDED: 'Tu organización',
  TENANT_REACTIVATED: 'Tu organización',
  TENANT_ARCHIVED: 'Tu organización',
  REGISTRATION_PENDING_REVIEW: 'Alta pendiente',
  SYSTEM_QUEUE_STUCK: 'Estado del sistema'
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
  private readonly router = inject(Router);

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

  /**
   * Abre la pantalla a la que se refiere el aviso y lo da por leído: pulsar
   * una notificación ya es señal de que se ha visto.
   */
  open(notification: AppNotification): void {
    this.markRead(notification);
    if (notification.actionPath) {
      void this.router.navigateByUrl(notification.actionPath);
    }
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
