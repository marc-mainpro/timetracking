import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { Absence, AbsenceStatus, AbsenceType, AbsencesService } from './absences.service';

@Component({
  selector: 'app-admin-absences',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './admin-absences.component.html',
  styleUrl: './admin-absences.component.scss'
})
export class AdminAbsencesComponent {
  private readonly absencesService = inject(AbsencesService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly decisionLoading = signal(false);
  readonly absences = signal<Absence[]>([]);
  readonly selectedAbsence = signal<Absence | null>(null);
  readonly actionMessage = signal<string | null>(null);
  readonly decisionError = signal<string | null>(null);
  readonly types = signal<Record<string, string>>({});
  readonly selectedStatus = signal<AbsenceStatus | ''>('PENDING');

  readonly rejectForm = this.fb.nonNullable.group({
    resolutionComment: ['', [Validators.required, Validators.maxLength(500)]]
  });

  readonly approveForm = this.fb.nonNullable.group({
    resolutionComment: ['', [Validators.maxLength(500)]]
  });

  constructor() {
    this.loadTypes();
    this.load();
  }

  applyStatus(status: AbsenceStatus | ''): void {
    this.selectedStatus.set(status);
    this.selectedAbsence.set(null);
    this.load();
  }

  selectAbsence(absence: Absence): void {
    this.selectedAbsence.set(absence);
  }

  approve(): void {
    const absence = this.selectedAbsence();
    if (!absence || absence.status !== 'PENDING' || this.decisionLoading()) {
      return;
    }
    this.resolve(this.absencesService.approve(absence.id, this.approveForm.controls.resolutionComment.getRawValue()));
  }

  reject(): void {
    const absence = this.selectedAbsence();
    if (!absence || absence.status !== 'PENDING' || this.decisionLoading()) {
      return;
    }
    if (this.rejectForm.invalid) {
      this.rejectForm.markAllAsTouched();
      return;
    }
    this.resolve(this.absencesService.reject(absence.id, this.rejectForm.controls.resolutionComment.getRawValue()));
  }

  typeName(absenceTypeId: string): string {
    return this.types()[absenceTypeId] ?? absenceTypeId;
  }

  approvedAbsences(): Absence[] {
    return this.absences().filter((absence) => absence.status === 'APPROVED');
  }

  private load(): void {
    this.loading.set(true);
    const now = new Date();
    const year = now.getUTCFullYear();
    this.absencesService.listAdmin(`${year}-01-01`, `${year}-12-31`).subscribe({
      next: (absences) => {
        const filtered = this.selectedStatus() ? absences.filter((absence) => absence.status === this.selectedStatus()) : absences;
        this.absences.set(filtered);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.decisionError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  private loadTypes(): void {
    this.absencesService.listTypes().subscribe({
      next: (types) => {
        this.types.set(Object.fromEntries(types.map((type: AbsenceType) => [type.id, type.name])));
      }
    });
  }

  private resolve(request: ReturnType<AbsencesService['approve']>): void {
    this.decisionLoading.set(true);
    this.decisionError.set(null);
    request.subscribe({
      next: (updated) => {
        this.selectedAbsence.set(updated);
        this.actionMessage.set(`Solicitud ${updated.status === 'APPROVED' ? 'aprobada' : 'rechazada'} correctamente.`);
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
