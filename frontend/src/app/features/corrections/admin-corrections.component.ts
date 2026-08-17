import { DatePipe } from '@angular/common';
import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { AdminEmployeesService, Employee } from '../admin-employees/admin-employees.service';
import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { Workday } from '../workdays/workdays.service';
import { Correction, CorrectionsService, CorrectionStatus, PagedCorrections } from './corrections.service';

const STATUS_FILTERS: readonly (CorrectionStatus | '')[] = ['PENDING', 'APPROVED', 'REJECTED', ''];

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendiente',
  APPROVED: 'Aprobada',
  REJECTED: 'Rechazada'
};

const FILTER_LABELS: Record<string, string> = {
  PENDING: 'Pendientes',
  APPROVED: 'Aprobadas',
  REJECTED: 'Rechazadas',
  '': 'Todas'
};

@Component({
  selector: 'app-admin-corrections',
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './admin-corrections.component.html',
  styleUrl: './admin-corrections.component.scss'
})
export class AdminCorrectionsComponent {
  private readonly correctionsService = inject(CorrectionsService);
  private readonly employeesService = inject(AdminEmployeesService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  private readonly confirmDialog = viewChild<ElementRef<HTMLDialogElement>>('confirmDialog');

  readonly statusFilters = STATUS_FILTERS;
  readonly loading = signal(false);
  readonly decisionLoading = signal(false);
  readonly selectedStatus = signal<CorrectionStatus | ''>('PENDING');
  readonly result = signal<PagedCorrections | null>(null);
  readonly selectedCorrection = signal<Correction | null>(null);
  readonly currentWorkday = signal<Workday | null>(null);
  readonly actionMessage = signal<string | null>(null);
  readonly decisionError = signal<string | null>(null);
  readonly employees = signal<Employee[]>([]);
  /** Decisión a la espera de confirmación: aprobar reescribe una jornada ya registrada. */
  readonly pendingDecision = signal<'approve' | 'reject' | null>(null);

  /**
   * Un solo comentario para las dos decisiones. Antes había un formulario por
   * cada una, lo que duplicaba el campo y obligaba a elegir dónde escribir
   * antes de saber qué se iba a decidir.
   */
  readonly commentControl = this.fb.nonNullable.control('');

  constructor() {
    this.load();
    this.loadEmployees();
  }

  employeeName(employeeId: string): string {
    const employee = this.employees().find((candidate) => candidate.id === employeeId);
    return employee ? `${employee.firstName} ${employee.lastName}` : employeeId;
  }

  applyStatus(status: CorrectionStatus | ''): void {
    this.selectedStatus.set(status);
    this.selectedCorrection.set(null);
    this.currentWorkday.set(null);
    this.load();
  }

  selectCorrection(correction: Correction): void {
    this.selectedCorrection.set(correction);
    this.currentWorkday.set(null);
    this.commentControl.reset('');
    this.commentControl.setErrors(null);
    this.correctionsService.getAdminWorkday(correction.workdayId).subscribe({
      next: (workday) => this.currentWorkday.set(workday),
      error: (error) => this.decisionError.set(this.errorMessagesService.fromProblem(error.error))
    });
  }

  approve(): void {
    this.ask('approve');
  }

  reject(): void {
    // El motivo de un rechazo lo lee quien pidió la corrección: sin él, la
    // persona se queda sin saber qué corregir.
    if (this.commentControl.value.trim().length === 0) {
      this.commentControl.markAsTouched();
      this.commentControl.setErrors({ required: true });
      return;
    }
    this.ask('reject');
  }

  confirmDecision(): void {
    const decision = this.pendingDecision();
    const correction = this.selectedCorrection();
    if (!decision || !correction || this.decisionLoading()) {
      return;
    }
    const comment = this.commentControl.value.trim();
    this.cancelDecision();
    this.resolve(
      decision === 'approve'
        ? this.correctionsService.approve(correction.id, comment)
        : this.correctionsService.reject(correction.id, comment)
    );
  }

  cancelDecision(): void {
    this.confirmDialog()?.nativeElement.close();
    this.pendingDecision.set(null);
  }

  statusLabel(status: string): string {
    return STATUS_LABELS[status] ?? status;
  }

  filterLabel(status: string): string {
    return FILTER_LABELS[status] ?? status;
  }

  private ask(decision: 'approve' | 'reject'): void {
    if (!this.selectedCorrection() || this.decisionLoading()) {
      return;
    }
    this.decisionError.set(null);
    this.actionMessage.set(null);
    this.pendingDecision.set(decision);
    this.confirmDialog()?.nativeElement.showModal();
  }

  private loadEmployees(): void {
    this.employeesService.list(0, 100).subscribe({
      next: (result) => this.employees.set(result.content),
      error: () => this.employees.set([])
    });
  }

  private load(): void {
    this.loading.set(true);
    this.correctionsService.list(0, 20, this.selectedStatus() || undefined).subscribe({
      next: (result) => {
        this.result.set(result);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.decisionError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  private resolve(request: Observable<Correction>): void {
    const correction = this.selectedCorrection();
    if (!correction) {
      return;
    }
    this.decisionLoading.set(true);
    this.decisionError.set(null);
    request.subscribe({
      next: (updated) => {
        this.selectedCorrection.set(updated);
        this.actionMessage.set(
          `Corrección de ${this.employeeName(updated.requestedBy)} ${
            updated.status === 'APPROVED' ? 'aprobada' : 'rechazada'
          }.`
        );
        this.decisionLoading.set(false);
        this.load();
        this.selectCorrection(updated);
      },
      error: (error) => {
        this.decisionLoading.set(false);
        this.decisionError.set(this.errorMessagesService.fromProblem(error.error));
        // Otra persona pudo resolverla mientras tanto: se recarga el estado
        // real en lugar de dejar en pantalla una decisión que ya no cabe.
        this.load();
        this.correctionsService.get(correction.id).subscribe({
          next: (updated) => this.selectCorrection(updated),
          error: () => {
            this.selectedCorrection.set(null);
            this.currentWorkday.set(null);
          }
        });
      }
    });
  }
}
