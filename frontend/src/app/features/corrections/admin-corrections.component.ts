import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { Workday } from '../workdays/workdays.service';
import { Correction, CorrectionsService, CorrectionStatus, PagedCorrections } from './corrections.service';

@Component({
  selector: 'app-admin-corrections',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './admin-corrections.component.html',
  styleUrl: './admin-corrections.component.scss'
})
export class AdminCorrectionsComponent {
  private readonly correctionsService = inject(CorrectionsService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly decisionLoading = signal(false);
  readonly selectedStatus = signal<CorrectionStatus | ''>('PENDING');
  readonly result = signal<PagedCorrections | null>(null);
  readonly selectedCorrection = signal<Correction | null>(null);
  readonly currentWorkday = signal<Workday | null>(null);
  readonly actionMessage = signal<string | null>(null);
  readonly decisionError = signal<string | null>(null);

  readonly approveForm = this.fb.nonNullable.group({
    resolutionComment: ['']
  });

  readonly rejectForm = this.fb.nonNullable.group({
    resolutionComment: ['', [Validators.required]]
  });

  constructor() {
    this.load();
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
    this.correctionsService.getAdminWorkday(correction.workdayId).subscribe({
      next: (workday) => this.currentWorkday.set(workday),
      error: (error) => this.decisionError.set(this.errorMessagesService.fromProblem(error.error))
    });
  }

  approve(): void {
    const correction = this.selectedCorrection();
    if (!correction || this.decisionLoading() || !window.confirm('¿Aprobar esta corrección?')) {
      return;
    }
    this.resolve(
      this.correctionsService.approve(correction.id, this.approveForm.controls.resolutionComment.getRawValue())
    );
  }

  reject(): void {
    const correction = this.selectedCorrection();
    if (!correction || this.decisionLoading()) {
      return;
    }
    if (this.rejectForm.invalid) {
      this.rejectForm.markAllAsTouched();
      return;
    }
    if (!window.confirm('¿Rechazar esta corrección?')) {
      return;
    }
    this.resolve(this.correctionsService.reject(correction.id, this.rejectForm.controls.resolutionComment.getRawValue()));
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
        this.actionMessage.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  private resolve(request: ReturnType<CorrectionsService['approve']>): void {
    const correction = this.selectedCorrection();
    if (!correction) {
      return;
    }
    this.decisionLoading.set(true);
    this.decisionError.set(null);
    request.subscribe({
      next: (updated) => {
        this.selectedCorrection.set(updated);
        this.actionMessage.set(`Corrección ${updated.status === 'APPROVED' ? 'aprobada' : 'rechazada'} correctamente.`);
        this.decisionLoading.set(false);
        this.load();
        this.selectCorrection(updated);
      },
      error: (error) => {
        this.decisionLoading.set(false);
        this.decisionError.set(this.errorMessagesService.fromProblem(error.error));
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
