import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { Absence, AbsenceType, AbsencesService } from './absences.service';

@Component({
  selector: 'app-absences',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './absences.component.html',
  styleUrl: './absences.component.scss'
})
export class AbsencesComponent {
  private readonly absencesService = inject(AbsencesService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly types = signal<AbsenceType[]>([]);
  readonly absences = signal<Absence[]>([]);
  readonly actionMessage = signal<string | null>(null);
  readonly formError = signal<string | null>(null);

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
        this.actionMessage.set('Solicitud de ausencia enviada correctamente.');
        this.form.reset({ absenceTypeId: '', startDate: '', endDate: '', reason: '' });
        this.loadAbsences();
      },
      error: (error) => {
        this.saving.set(false);
        this.formError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  cancel(absenceId: string): void {
    if (!window.confirm('¿Cancelar esta solicitud de ausencia?')) {
      return;
    }
    this.absencesService.cancel(absenceId).subscribe({
      next: () => {
        this.actionMessage.set('Solicitud cancelada.');
        this.loadAbsences();
      },
      error: (error) => {
        this.formError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  typeName(absenceTypeId: string): string {
    return this.types().find((type) => type.id === absenceTypeId)?.name ?? absenceTypeId;
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
