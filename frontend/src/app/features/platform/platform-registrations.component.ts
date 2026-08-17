import { DatePipe, LowerCasePipe } from '@angular/common';
import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { daysSince, relativeTime } from '../../core/relative-time';
import { ErrorMessagesService } from '../../core/services/error-messages.service';
import {
  PagedTenantRegistrations,
  PlatformRegistrationsService,
  TenantRegistration,
  TenantRegistrationStatus
} from './platform-registrations.service';

const STATUS_FILTERS = [
  'PENDING_REVIEW',
  'PENDING_EMAIL_VERIFICATION',
  'CONSUMED',
  'REJECTED',
  'EXPIRED',
  ''
] as const;

/** Cada estado en singular, para la píldora de una solicitud. */
const STATUS_LABELS: Record<TenantRegistrationStatus, string> = {
  PENDING_REVIEW: 'Por revisar',
  PENDING_EMAIL_VERIFICATION: 'Sin verificar',
  APPROVED: 'Aprobada',
  REJECTED: 'Rechazada',
  EXPIRED: 'Caducada',
  CONSUMED: 'Alta creada'
};

/** El mismo estado como bandeja: los filtros nombran conjuntos. */
const FILTER_LABELS: Record<string, string> = {
  PENDING_REVIEW: 'Por revisar',
  PENDING_EMAIL_VERIFICATION: 'Sin verificar',
  CONSUMED: 'Altas creadas',
  REJECTED: 'Rechazadas',
  EXPIRED: 'Caducadas',
  '': 'Todas'
};

export type RegistrationActionKind = 'approve' | 'reject';

/**
 * Decisión a la espera de confirmación. Aprobar crea una organización y
 * rechazar guarda un motivo que el solicitante puede acabar leyendo: ninguna
 * de las dos debería depender de un `window.confirm` sin contexto.
 */
interface PendingDecision {
  readonly registration: TenantRegistration;
  readonly kind: RegistrationActionKind;
  readonly title: string;
  readonly consequence: string;
  readonly confirmLabel: string;
  readonly reason: 'required' | 'none';
  readonly danger: boolean;
}

/** Una solicitud que lleva más de esto sin revisar ya hace esperar a alguien. */
const STALE_REVIEW_DAYS = 3;

/**
 * Revisión de solicitudes de alta desde el panel de plataforma (T53-03).
 *
 * <p>El filtro por defecto es {@code PENDING_REVIEW}: es la única bandeja que
 * exige acción humana. Las demás solicitudes se consultan, no se deciden.
 */
@Component({
  selector: 'app-platform-registrations',
  imports: [DatePipe, LowerCasePipe, ReactiveFormsModule],
  templateUrl: './platform-registrations.component.html',
  styleUrl: './platform-registrations.component.scss'
})
export class PlatformRegistrationsComponent {
  private readonly registrationsService = inject(PlatformRegistrationsService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  private readonly confirmDialog = viewChild<ElementRef<HTMLDialogElement>>('confirmDialog');

  readonly statusFilters = STATUS_FILTERS;
  readonly loading = signal(false);
  readonly deciding = signal(false);
  readonly page = signal(0);
  readonly selectedStatus = signal<string>('PENDING_REVIEW');
  readonly result = signal<PagedTenantRegistrations | null>(null);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly pendingDecision = signal<PendingDecision | null>(null);

  /** «Ahora» congelado en cada carga; ver `relative-time`. */
  private readonly renderedAt = signal(Date.now());

  readonly reasonControl = this.fb.nonNullable.control('');

  constructor() {
    this.load();
  }

  // --- Datos ---------------------------------------------------------------

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.registrationsService.list(this.page(), 20, this.selectedStatus() || undefined).subscribe({
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

  // --- Decisiones ----------------------------------------------------------

  approve(registration: TenantRegistration): void {
    this.ask({
      registration,
      kind: 'approve',
      title: `Aprobar el alta de ${registration.companyName}`,
      consequence:
        'Se crea la organización y su administrador. Queda pendiente de activar en Organizaciones.',
      confirmLabel: 'Aprobar',
      reason: 'none',
      danger: false
    });
  }

  reject(registration: TenantRegistration): void {
    this.ask({
      registration,
      kind: 'reject',
      title: `Rechazar el alta de ${registration.companyName}`,
      consequence: `${registration.email} no podrá usar esta solicitud. El motivo queda registrado.`,
      confirmLabel: 'Rechazar',
      reason: 'required',
      danger: true
    });
  }

  confirmDecision(): void {
    const pending = this.pendingDecision();
    if (!pending || this.deciding()) {
      return;
    }
    const reason = this.reasonControl.value.trim();
    if (pending.reason === 'required' && reason.length === 0) {
      this.reasonControl.markAsTouched();
      this.reasonControl.setErrors({ required: true });
      return;
    }

    this.deciding.set(true);
    this.error.set(null);
    this.message.set(null);

    const name = pending.registration.companyName;
    this.requestFor(pending, reason).subscribe({
      next: () => {
        this.deciding.set(false);
        this.cancelDecision();
        this.message.set(
          pending.kind === 'approve'
            ? `${name} aprobada. Actívala desde Organizaciones.`
            : `Solicitud de ${name} rechazada.`
        );
        this.load();
      },
      error: (err) => {
        this.deciding.set(false);
        this.cancelDecision();
        this.error.set(this.errorMessagesService.fromProblem(err.error));
      }
    });
  }

  cancelDecision(): void {
    this.confirmDialog()?.nativeElement.close();
    this.pendingDecision.set(null);
  }

  // --- Presentación --------------------------------------------------------

  statusLabel(status: TenantRegistrationStatus): string {
    return STATUS_LABELS[status];
  }

  filterLabel(status: string): string {
    return FILTER_LABELS[status] ?? status;
  }

  relativeTime(iso: string): string {
    return relativeTime(iso, this.renderedAt());
  }

  /** Solo lo pendiente de revisar envejece: lo ya decidido no espera a nadie. */
  isStale(registration: TenantRegistration): boolean {
    return (
      registration.status === 'PENDING_REVIEW' &&
      daysSince(registration.createdAt, this.renderedAt()) >= STALE_REVIEW_DAYS
    );
  }

  ownerName(registration: TenantRegistration): string {
    return `${registration.ownerFirstName} ${registration.ownerLastName}`;
  }

  // --- Interno -------------------------------------------------------------

  private ask(decision: PendingDecision): void {
    this.error.set(null);
    this.message.set(null);
    this.reasonControl.reset('');
    this.reasonControl.setErrors(null);
    this.pendingDecision.set(decision);
    this.confirmDialog()?.nativeElement.showModal();
  }

  private requestFor(pending: PendingDecision, reason: string): Observable<TenantRegistration> {
    return pending.kind === 'approve'
      ? this.registrationsService.approve(pending.registration.id)
      : this.registrationsService.reject(pending.registration.id, reason);
  }
}
