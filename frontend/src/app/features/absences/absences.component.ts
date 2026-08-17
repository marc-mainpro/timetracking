import { DatePipe, LowerCasePipe } from '@angular/common';
import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { Absence, AbsenceType, AbsencesService } from './absences.service';

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendiente',
  APPROVED: 'Aprobada',
  REJECTED: 'Rechazada',
  CANCELLED: 'Cancelada'
};

const MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

@Component({
  selector: 'app-absences',
  imports: [DatePipe, LowerCasePipe, ReactiveFormsModule],
  templateUrl: './absences.component.html',
  styleUrl: './absences.component.scss'
})
export class AbsencesComponent {
  private readonly absencesService = inject(AbsencesService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  private readonly formDialog = viewChild<ElementRef<HTMLDialogElement>>('formDialog');
  private readonly confirmDialog = viewChild<ElementRef<HTMLDialogElement>>('confirmDialog');

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly types = signal<AbsenceType[]>([]);
  readonly absences = signal<Absence[]>([]);
  readonly actionMessage = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly pendingCancel = signal<Absence | null>(null);

  readonly form = this.fb.nonNullable.group({
    absenceTypeId: ['', [Validators.required]],
    startDate: ['', [Validators.required]],
    endDate: ['', [Validators.required]],
    reason: ['', [Validators.maxLength(500)]]
  });

  constructor() {
    this.loadTypes();
    this.loadAbsences();
  }

  openForm(): void {
    this.formError.set(null);
    this.form.reset({ absenceTypeId: '', startDate: '', endDate: '', reason: '' });
    this.formDialog()?.nativeElement.showModal();
  }

  closeForm(): void {
    this.formDialog()?.nativeElement.close();
  }

  submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const { startDate, endDate, absenceTypeId, reason } = this.form.getRawValue();
    if (endDate < startDate) {
      this.formError.set('La fecha de fin debe ser igual o posterior a la fecha de inicio.');
      return;
    }
    this.saving.set(true);
    this.formError.set(null);
    this.actionMessage.set(null);
    this.absencesService.request({
      absenceTypeId,
      startDate,
      endDate,
      reason: reason || null
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.actionMessage.set('Solicitud enviada. Te avisaremos cuando se resuelva.');
        this.form.reset({ absenceTypeId: '', startDate: '', endDate: '', reason: '' });
        this.closeForm();
        this.loadAbsences();
      },
      error: (error) => {
        this.saving.set(false);
        this.formError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  cancel(absence: Absence): void {
    this.actionMessage.set(null);
    this.formError.set(null);
    this.pendingCancel.set(absence);
    this.confirmDialog()?.nativeElement.showModal();
  }

  confirmCancel(): void {
    const absence = this.pendingCancel();
    if (!absence) {
      return;
    }
    this.cancelCancel();
    this.absencesService.cancel(absence.id).subscribe({
      next: () => {
        this.actionMessage.set('Solicitud cancelada.');
        this.loadAbsences();
      },
      error: (error) => {
        this.formError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  cancelCancel(): void {
    this.confirmDialog()?.nativeElement.close();
    this.pendingCancel.set(null);
  }

  typeName(absenceTypeId: string): string {
    return this.types().find((type) => type.id === absenceTypeId)?.name ?? absenceTypeId;
  }

  statusLabel(status: string): string {
    return STATUS_LABELS[status] ?? status;
  }

  /** Días naturales que cubre la solicitud, extremos incluidos. */
  dayCountLabel(absence: Absence): string {
    const days =
      Math.round((Date.parse(absence.endDate) - Date.parse(absence.startDate)) / MILLIS_PER_DAY) + 1;
    return `${days} ${days === 1 ? 'día' : 'días'}`;
  }

  private loadTypes(): void {
    this.absencesService.listTypes().subscribe({
      next: (types) => this.types.set(types),
      error: (error) => this.formError.set(this.errorMessagesService.fromProblem(error.error))
    });
  }

  private loadAbsences(): void {
    this.loading.set(true);
    const now = new Date();
    const year = now.getUTCFullYear();
    this.absencesService.listOwn(`${year}-01-01`, `${year}-12-31`).subscribe({
      next: (absences) => {
        this.absences.set(absences);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.formError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }
}
