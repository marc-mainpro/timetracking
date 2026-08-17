import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { DatePipe, LowerCasePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { daysSince, relativeTime } from '../../core/relative-time';
import { ErrorMessagesService } from '../../core/services/error-messages.service';
import {
  AuditEvent,
  PagedTenants,
  PlatformTenantDetail,
  PlatformTenantSummary,
  PlatformTenantsService,
  TenantStatus
} from './platform-tenants.service';

const STATUS_FILTERS = ['', 'PENDING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'] as const;

/** Nombre de cada estado en singular, para la píldora de una fila. */
const STATUS_LABELS: Record<TenantStatus, string> = {
  PENDING: 'Pendiente',
  ACTIVE: 'Activa',
  SUSPENDED: 'Suspendida',
  ARCHIVED: 'Archivada'
};

/** El mismo estado en plural: los filtros hablan de conjuntos, no de una. */
const FILTER_LABELS: Record<string, string> = {
  '': 'Todas',
  PENDING: 'Pendientes',
  ACTIVE: 'Activas',
  SUSPENDED: 'Suspendidas',
  ARCHIVED: 'Archivadas'
};

export type TenantActionKind = 'activate' | 'suspend' | 'reactivate' | 'archive';

/**
 * Acción a la espera de confirmación. Sustituye a `window.confirm`/`prompt`: el
 * motivo de una suspensión es obligatorio y se guarda tal cual, así que necesita
 * un campo de verdad, con validación y opción de corregirse.
 */
interface PendingAction {
  readonly tenant: PlatformTenantSummary;
  readonly kind: TenantActionKind;
  readonly title: string;
  readonly consequence: string;
  readonly confirmLabel: string;
  /** El motivo es obligatorio al suspender y opcional al archivar. */
  readonly reason: 'required' | 'optional' | 'none';
  readonly danger: boolean;
}

/** A partir de aquí una cuenta activa se considera en silencio. */
const SILENCE_THRESHOLD_DAYS = 30;

@Component({
  selector: 'app-platform-tenants',
  imports: [DatePipe, LowerCasePipe, ReactiveFormsModule],
  templateUrl: './platform-tenants.component.html',
  styleUrl: './platform-tenants.component.scss'
})
export class PlatformTenantsComponent {
  private readonly tenantsService = inject(PlatformTenantsService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  private readonly detailDialog = viewChild<ElementRef<HTMLDialogElement>>('detailDialog');
  private readonly createDialog = viewChild<ElementRef<HTMLDialogElement>>('createDialog');
  private readonly confirmDialog = viewChild<ElementRef<HTMLDialogElement>>('confirmDialog');

  readonly statusFilters = STATUS_FILTERS;
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly acting = signal(false);
  readonly page = signal(0);
  readonly result = signal<PagedTenants | null>(null);
  readonly selectedStatus = signal<string>('');
  readonly selectedTenant = signal<PlatformTenantDetail | null>(null);
  readonly auditEvents = signal<AuditEvent[]>([]);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly pendingAction = signal<PendingAction | null>(null);

  /**
   * «Ahora» congelado en cada carga en lugar del `ClockService`: las antigüedades
   * se miden contra este instante y con un reloj vivo la tabla entera se
   * repintaría cada segundo para cambiar un «hace 8 meses» una vez al mes.
   */
  private readonly renderedAt = signal(Date.now());

  readonly reasonControl = this.fb.nonNullable.control('');

  readonly form = this.fb.nonNullable.group({
    tenantName: ['', [Validators.required]],
    timezone: ['Europe/Madrid', [Validators.required]],
    adminEmail: ['', [Validators.required, Validators.email]],
    adminPassword: ['', [Validators.required, Validators.minLength(10)]],
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]]
  });

  constructor() {
    this.load();
    this.loadAudit();
  }

  // --- Datos ---------------------------------------------------------------

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.tenantsService.list(this.page(), 20, this.selectedStatus() || undefined).subscribe({
      next: (result) => {
        this.result.set(result);
        this.renderedAt.set(Date.now());
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

  // --- Detalle -------------------------------------------------------------

  viewDetail(tenant: PlatformTenantSummary): void {
    this.error.set(null);
    this.tenantsService.get(tenant.id).subscribe({
      next: (detail) => {
        this.selectedTenant.set(detail);
        this.detailDialog()?.nativeElement.showModal();
      },
      error: (err) => this.error.set(this.errorMessagesService.fromProblem(err.error))
    });
  }

  closeDetail(): void {
    this.detailDialog()?.nativeElement.close();
    this.selectedTenant.set(null);
  }

  // --- Alta ----------------------------------------------------------------

  openCreate(): void {
    this.formError.set(null);
    this.createDialog()?.nativeElement.showModal();
  }

  closeCreate(): void {
    this.createDialog()?.nativeElement.close();
  }

  createTenant(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.formError.set(null);
    this.message.set(null);
    const name = this.form.controls.tenantName.value;
    this.tenantsService.create(this.form.getRawValue()).subscribe({
      next: () => {
        this.saving.set(false);
        this.message.set(`${name} creada.`);
        this.form.reset({
          tenantName: '',
          timezone: 'Europe/Madrid',
          adminEmail: '',
          adminPassword: '',
          firstName: '',
          lastName: ''
        });
        this.closeCreate();
        this.page.set(0);
        this.load();
      },
      error: (err) => {
        this.saving.set(false);
        this.formError.set(this.errorMessagesService.fromProblem(err.error));
      }
    });
  }

  // --- Acciones sobre un tenant -------------------------------------------

  activate(tenant: PlatformTenantSummary): void {
    this.ask({
      tenant,
      kind: 'activate',
      title: `Activar ${tenant.name}`,
      consequence: 'Su equipo podrá entrar y empezar a fichar.',
      confirmLabel: 'Activar',
      reason: 'none',
      danger: false
    });
  }

  suspend(tenant: PlatformTenantSummary): void {
    this.ask({
      tenant,
      kind: 'suspend',
      title: `Suspender ${tenant.name}`,
      consequence: `${this.userCountLabel(tenant)} dejarán de poder acceder hasta que la reactives.`,
      confirmLabel: 'Suspender',
      reason: 'required',
      danger: false
    });
  }

  reactivate(tenant: PlatformTenantSummary): void {
    this.ask({
      tenant,
      kind: 'reactivate',
      title: `Reactivar ${tenant.name}`,
      consequence: 'Su equipo recupera el acceso de inmediato.',
      confirmLabel: 'Reactivar',
      reason: 'none',
      danger: false
    });
  }

  archive(tenant: PlatformTenantSummary): void {
    this.ask({
      tenant,
      kind: 'archive',
      title: `Archivar ${tenant.name}`,
      consequence: 'La organización deja de operar y no se puede restaurar desde aquí.',
      confirmLabel: 'Archivar',
      reason: 'optional',
      danger: true
    });
  }

  /** Confirma la acción pendiente. El diálogo es el único camino para ejecutarla. */
  confirmAction(): void {
    const pending = this.pendingAction();
    if (!pending || this.acting()) {
      return;
    }
    const reason = this.reasonControl.value.trim();
    if (pending.reason === 'required' && reason.length === 0) {
      this.reasonControl.markAsTouched();
      this.reasonControl.setErrors({ required: true });
      return;
    }

    this.acting.set(true);
    this.runAction(
      this.requestFor(pending, reason),
      `${pending.tenant.name} ${this.pastParticiple(pending.kind)}.`
    );
  }

  cancelAction(): void {
    this.confirmDialog()?.nativeElement.close();
    this.pendingAction.set(null);
  }

  // --- Presentación --------------------------------------------------------

  statusLabel(status: TenantStatus): string {
    return STATUS_LABELS[status];
  }

  filterLabel(status: string): string {
    return FILTER_LABELS[status] ?? status;
  }

  relativeTime(iso: string): string {
    return relativeTime(iso, this.renderedAt());
  }

  /** Días sin un solo acceso; null si nunca ha entrado nadie. */
  silenceDays(tenant: PlatformTenantSummary): number | null {
    if (!tenant.lastAccessAt) {
      return null;
    }
    return daysSince(tenant.lastAccessAt, this.renderedAt());
  }

  /** Una cuenta viva que lleva más de un mes sin usarse merece una mirada. */
  isSilent(tenant: PlatformTenantSummary): boolean {
    if (tenant.status !== 'ACTIVE') {
      return false;
    }
    const days = this.silenceDays(tenant);
    return days === null || days >= SILENCE_THRESHOLD_DAYS;
  }

  accessLabel(tenant: PlatformTenantSummary): string {
    if (!tenant.lastAccessAt) {
      return 'Nunca ha accedido';
    }
    const prefix = this.isSilent(tenant) ? 'Sin accesos desde' : 'Último acceso';
    return `${prefix} ${this.relativeTime(tenant.lastAccessAt)}`;
  }

  userCountLabel(tenant: PlatformTenantSummary): string {
    return `${tenant.userCount} ${tenant.userCount === 1 ? 'usuario' : 'usuarios'}`;
  }

  /** Traduce `TENANT_SUSPENDED` a algo legible sin inventar lo que no conocemos. */
  auditLabel(event: AuditEvent): string {
    return event.action
      .toLowerCase()
      .replace(/_/g, ' ')
      .replace(/^./, (first) => first.toUpperCase());
  }

  // --- Interno -------------------------------------------------------------

  private ask(action: PendingAction): void {
    this.error.set(null);
    this.message.set(null);
    this.reasonControl.reset('');
    this.reasonControl.setErrors(null);
    this.pendingAction.set(action);
    this.confirmDialog()?.nativeElement.showModal();
  }

  private requestFor(pending: PendingAction, reason: string): Observable<PlatformTenantDetail> {
    const id = pending.tenant.id;
    switch (pending.kind) {
      case 'activate':
        return this.tenantsService.activate(id);
      case 'suspend':
        return this.tenantsService.suspend(id, reason);
      case 'reactivate':
        return this.tenantsService.reactivate(id);
      case 'archive':
        return this.tenantsService.archive(id, reason || undefined);
    }
  }

  private pastParticiple(kind: TenantActionKind): string {
    switch (kind) {
      case 'activate':
        return 'activada';
      case 'suspend':
        return 'suspendida';
      case 'reactivate':
        return 'reactivada';
      case 'archive':
        return 'archivada';
    }
  }

  private runAction(request: Observable<PlatformTenantDetail>, successMessage: string): void {
    this.error.set(null);
    this.message.set(null);
    request.subscribe({
      next: (detail) => {
        this.acting.set(false);
        this.cancelAction();
        this.message.set(successMessage);
        // El detalle abierto se refresca en el sitio; si no lo estaba, no se abre.
        if (this.selectedTenant()) {
          this.selectedTenant.set(detail);
        }
        this.load();
        this.loadAudit();
      },
      error: (err) => {
        this.acting.set(false);
        this.cancelAction();
        this.error.set(this.errorMessagesService.fromProblem(err.error));
      }
    });
  }
}
