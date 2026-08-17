import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { DatePipe, LowerCasePipe } from '@angular/common';
import { Observable } from 'rxjs';

import { AdminEmployeesService, Employee } from '../admin-employees/admin-employees.service';
import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { Absence, AbsenceStatus, AbsenceType, AbsencesService } from './absences.service';

const STATUS_FILTERS: readonly (AbsenceStatus | '')[] = ['PENDING', 'APPROVED', 'REJECTED', ''];

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

const MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

@Component({
  selector: 'app-admin-absences',
  imports: [DatePipe, LowerCasePipe, ReactiveFormsModule],
  templateUrl: './admin-absences.component.html',
  styleUrl: './admin-absences.component.scss'
})
export class AdminAbsencesComponent {
  private readonly absencesService = inject(AbsencesService);
  private readonly employeesService = inject(AdminEmployeesService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  private readonly confirmDialog = viewChild<ElementRef<HTMLDialogElement>>('confirmDialog');

  readonly statusFilters = STATUS_FILTERS;
  readonly loading = signal(false);
  readonly decisionLoading = signal(false);
  readonly absences = signal<Absence[]>([]);
  readonly selectedAbsence = signal<Absence | null>(null);
  readonly actionMessage = signal<string | null>(null);
  readonly decisionError = signal<string | null>(null);
  readonly types = signal<Record<string, string>>({});
  readonly selectedStatus = signal<AbsenceStatus | ''>('PENDING');
  readonly employees = signal<Employee[]>([]);
  readonly pendingDecision = signal<'approve' | 'reject' | null>(null);

  /** Un solo comentario para las dos decisiones: opcional al aprobar, obligatorio al rechazar. */
  readonly commentControl = this.fb.nonNullable.control('');

  constructor() {
    this.loadTypes();
    this.load();
    this.loadEmployees();
  }

  employeeName(employeeId: string): string {
    const employee = this.employees().find((candidate) => candidate.id === employeeId);
    return employee ? `${employee.firstName} ${employee.lastName}` : employeeId;
  }

  typeName(absenceTypeId: string): string {
    return this.types()[absenceTypeId] ?? absenceTypeId;
  }

  applyStatus(status: AbsenceStatus | ''): void {
    this.selectedStatus.set(status);
    this.selectedAbsence.set(null);
    this.load();
  }

  selectAbsence(absence: Absence): void {
    this.selectedAbsence.set(absence);
    this.commentControl.reset('');
    this.commentControl.setErrors(null);
  }

  approve(): void {
    this.ask('approve');
  }

  reject(): void {
    // Quien pidió la ausencia lee este motivo: sin él se queda sin saber por qué.
    if (this.commentControl.value.trim().length === 0) {
      this.commentControl.markAsTouched();
      this.commentControl.setErrors({ required: true });
      return;
    }
    this.ask('reject');
  }

  confirmDecision(): void {
    const decision = this.pendingDecision();
    const absence = this.selectedAbsence();
    if (!decision || !absence || this.decisionLoading()) {
      return;
    }
    const comment = this.commentControl.value.trim();
    this.cancelDecision();
    this.resolve(
      decision === 'approve'
        ? this.absencesService.approve(absence.id, comment)
        : this.absencesService.reject(absence.id, comment)
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

  /** Días naturales que cubre la solicitud, extremos incluidos. */
  dayCount(absence: Absence): number {
    const from = Date.parse(absence.startDate);
    const to = Date.parse(absence.endDate);
    return Math.round((to - from) / MILLIS_PER_DAY) + 1;
  }

  dayCountLabel(absence: Absence): string {
    const days = this.dayCount(absence);
    return `${days} ${days === 1 ? 'día' : 'días'}`;
  }

  private ask(decision: 'approve' | 'reject'): void {
    const absence = this.selectedAbsence();
    if (!absence || absence.status !== 'PENDING' || this.decisionLoading()) {
      return;
    }
    this.decisionError.set(null);
    this.actionMessage.set(null);
    this.pendingDecision.set(decision);
    this.confirmDialog()?.nativeElement.showModal();
  }

  private load(): void {
    this.loading.set(true);
    const now = new Date();
    const year = now.getUTCFullYear();
    this.absencesService.listAdmin(`${year}-01-01`, `${year}-12-31`).subscribe({
      next: (absences) => {
        const filtered = this.selectedStatus()
          ? absences.filter((absence) => absence.status === this.selectedStatus())
          : absences;
        this.absences.set(filtered);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.decisionError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  private loadEmployees(): void {
    this.employeesService.list(0, 100).subscribe({
      next: (result) => this.employees.set(result.content),
      error: () => this.employees.set([])
    });
  }

  private loadTypes(): void {
    this.absencesService.listTypes().subscribe({
      next: (types) => {
        this.types.set(Object.fromEntries(types.map((type: AbsenceType) => [type.id, type.name])));
      }
    });
  }

  private resolve(request: Observable<Absence>): void {
    this.decisionLoading.set(true);
    this.decisionError.set(null);
    request.subscribe({
      next: (updated) => {
        this.selectedAbsence.set(updated);
        this.actionMessage.set(
          `Ausencia de ${this.employeeName(updated.employeeId)} ${
            updated.status === 'APPROVED' ? 'aprobada' : 'rechazada'
          }.`
        );
        this.decisionLoading.set(false);
        this.load();
      },
      error: (error) => {
        this.decisionLoading.set(false);
        this.decisionError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }
}
