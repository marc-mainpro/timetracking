import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import {
  PagedTenantRegistrations,
  PlatformRegistrationsService,
  TenantRegistration
} from './platform-registrations.service';

const STATUS_FILTERS = [
  'PENDING_REVIEW',
  'PENDING_EMAIL_VERIFICATION',
  'CONSUMED',
  'REJECTED',
  'EXPIRED',
  ''
] as const;

/**
 * Revisión de solicitudes de alta desde el panel de plataforma (T53-03).
 *
 * <p>El filtro por defecto es {@code PENDING_REVIEW}: es la única bandeja que
 * exige acción humana. Las demás solicitudes se consultan, no se deciden.
 */
@Component({
  selector: 'app-platform-registrations',
  imports: [DatePipe],
  templateUrl: './platform-registrations.component.html',
  styleUrl: './platform-registrations.component.scss'
})
export class PlatformRegistrationsComponent {
  private readonly registrationsService = inject(PlatformRegistrationsService);
  private readonly errorMessagesService = inject(ErrorMessagesService);

  readonly statusFilters = STATUS_FILTERS;
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly selectedStatus = signal<string>('PENDING_REVIEW');
  readonly result = signal<PagedTenantRegistrations | null>(null);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.registrationsService.list(this.page(), 20, this.selectedStatus() || undefined).subscribe({
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

  applyStatus(status: string): void {
    this.selectedStatus.set(status);
    this.page.set(0);
    this.load();
  }

  approve(registration: TenantRegistration): void {
    if (!window.confirm(`¿Aprobar el alta de "${registration.companyName}"?`)) {
      return;
    }
    this.error.set(null);
    this.message.set(null);
    this.registrationsService.approve(registration.id).subscribe({
      next: (approved) => {
        this.message.set(
          `Solicitud aprobada. El tenant se ha creado en estado PENDING (${approved.createdTenantId}); actívalo desde Tenants.`
        );
        this.load();
      },
      error: (err) => this.error.set(this.errorMessagesService.fromProblem(err.error))
    });
  }

  reject(registration: TenantRegistration): void {
    const reason = window.prompt(`Motivo del rechazo de "${registration.companyName}":`);
    if (reason === null) {
      return;
    }
    if (reason.trim().length === 0) {
      this.error.set('El motivo del rechazo es obligatorio.');
      return;
    }
    this.error.set(null);
    this.message.set(null);
    this.registrationsService.reject(registration.id, reason).subscribe({
      next: () => {
        this.message.set(`Solicitud de "${registration.companyName}" rechazada.`);
        this.load();
      },
      error: (err) => this.error.set(this.errorMessagesService.fromProblem(err.error))
    });
  }

  nextPage(): void {
    const result = this.result();
    if (!result || result.page + 1 >= result.totalPages) {
      return;
    }
    this.page.update((value) => value + 1);
    this.load();
  }

  previousPage(): void {
    if (this.page() === 0) {
      return;
    }
    this.page.update((value) => value - 1);
    this.load();
  }

  statusLabel(status: string): string {
    return status === '' ? 'Todas' : status;
  }
}
