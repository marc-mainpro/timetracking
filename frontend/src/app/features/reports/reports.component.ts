import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { IsoDurationPipe } from '../../core/pipes/iso-duration.pipe';
import { ReportsService, TenantEmployeeSummary } from './reports.service';

@Component({
  selector: 'app-reports',
  imports: [ReactiveFormsModule, IsoDurationPipe],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss'
})
export class ReportsComponent {
  private readonly reportsService = inject(ReportsService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly exporting = signal(false);
  readonly results = signal<TenantEmployeeSummary[] | null>(null);
  readonly formError = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      from: [firstDayOfCurrentMonth(), [Validators.required]],
      to: [today(), [Validators.required]]
    },
    { validators: [rangeValidator] }
  );

  constructor() {
    this.load();
  }

  load(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.formError.set('El rango de fechas no es válido: "desde" debe ser anterior o igual a "hasta".');
      return;
    }

    this.loading.set(true);
    this.formError.set(null);
    const { from, to } = this.isoRange();
    this.reportsService.tenantSummary(from, to).subscribe({
      next: (results) => {
        this.results.set(results);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.results.set(null);
        this.formError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  exportCsv(): void {
    if (this.form.invalid || this.exporting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.exporting.set(true);
    this.formError.set(null);
    const { from, to } = this.isoRange();
    this.reportsService.exportTenantCsv(from, to).subscribe({
      next: (blob) => {
        this.exporting.set(false);
        this.triggerDownload(blob, 'tenant-summary.csv');
      },
      error: (error) => {
        this.exporting.set(false);
        this.formError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  private isoRange(): { from: string; to: string } {
    const { from, to } = this.form.getRawValue();
    return { from: `${from}T00:00:00Z`, to: `${to}T23:59:59Z` };
  }

  private triggerDownload(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }
}

function rangeValidator(control: AbstractControl): ValidationErrors | null {
  const from = control.get('from')?.value as string;
  const to = control.get('to')?.value as string;
  if (!from || !to) {
    return null;
  }
  return from <= to ? null : { invalidRange: true };
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function firstDayOfCurrentMonth(): string {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
}
