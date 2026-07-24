import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import {
  AuditEvent,
  PagedTenants,
  PlatformTenantDetail,
  PlatformTenantSummary,
  PlatformTenantsService
} from './platform-tenants.service';

const STATUS_FILTERS = ['', 'PENDING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'] as const;

@Component({
  selector: 'app-platform-tenants',
  imports: [DatePipe],
  templateUrl: './platform-tenants.component.html',
  styleUrl: './platform-tenants.component.scss'
})
export class PlatformTenantsComponent {
  private readonly tenantsService = inject(PlatformTenantsService);
  private readonly errorMessagesService = inject(ErrorMessagesService);

  readonly statusFilters = STATUS_FILTERS;
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly result = signal<PagedTenants | null>(null);
  readonly selectedStatus = signal<string>('');
  readonly selectedTenant = signal<PlatformTenantDetail | null>(null);
  readonly auditEvents = signal<AuditEvent[]>([]);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    this.load();
    this.loadAudit();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.tenantsService.list(this.page(), 20, this.selectedStatus() || undefined).subscribe({
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

  loadAudit(): void {
    this.tenantsService.audit(0, 20).subscribe({
      next: (result) => this.auditEvents.set(result.content),
      error: () => this.auditEvents.set([])
    });
  }

  applyStatus(status: string): void {
    this.selectedStatus.set(status);
    this.page.set(0);
    this.load();
  }

  viewDetail(tenant: PlatformTenantSummary): void {
    this.tenantsService.get(tenant.id).subscribe({
      next: (detail) => this.selectedTenant.set(detail),
      error: (err) => this.error.set(this.errorMessagesService.fromProblem(err.error))
    });
  }

  closeDetail(): void {
    this.selectedTenant.set(null);
  }

  activate(tenant: PlatformTenantSummary): void {
    if (!window.confirm(`¿Activar el tenant "${tenant.name}"?`)) {
      return;
    }
    this.runAction(this.tenantsService.activate(tenant.id), `Tenant "${tenant.name}" activado.`);
  }

  suspend(tenant: PlatformTenantSummary): void {
    const reason = window.prompt(`Motivo de la suspensión de "${tenant.name}":`);
    if (reason === null) {
      return;
    }
    if (reason.trim().length === 0) {
      this.error.set('El motivo de la suspensión es obligatorio.');
      return;
    }
    this.runAction(this.tenantsService.suspend(tenant.id, reason), `Tenant "${tenant.name}" suspendido.`);
  }

  reactivate(tenant: PlatformTenantSummary): void {
    if (!window.confirm(`¿Reactivar el tenant "${tenant.name}"?`)) {
      return;
    }
    this.runAction(this.tenantsService.reactivate(tenant.id), `Tenant "${tenant.name}" reactivado.`);
  }

  archive(tenant: PlatformTenantSummary): void {
    if (!window.confirm(`¿Archivar de forma permanente el tenant "${tenant.name}"?`)) {
      return;
    }
    const reason = window.prompt('Motivo del archivado (opcional):') ?? undefined;
    this.runAction(this.tenantsService.archive(tenant.id, reason), `Tenant "${tenant.name}" archivado.`);
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
    return status === '' ? 'Todos' : status;
  }

  private runAction(request: ReturnType<PlatformTenantsService['activate']>, successMessage: string): void {
    this.error.set(null);
    this.message.set(null);
    request.subscribe({
      next: (detail) => {
        this.message.set(successMessage);
        this.selectedTenant.set(detail);
        this.load();
        this.loadAudit();
      },
      error: (err) => this.error.set(this.errorMessagesService.fromProblem(err.error))
    });
  }
}
