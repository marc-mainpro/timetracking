import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';

import { AuthService } from '../../core/services/auth.service';
import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { formatIsoDuration } from './duration.util';
import { EmployeeDaySummary, ReportsService } from './reports.service';

@Component({
  selector: 'app-employee-report',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './employee-report.component.html',
  styleUrl: './employee-report.component.scss'
})
export class EmployeeReportComponent {
  private readonly reportsService = inject(ReportsService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly results = signal<EmployeeDaySummary[] | null>(null);
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

  get formatIsoDuration(): (value: string) => string {
    return formatIsoDuration;
  }

  load(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.formError.set('El rango de fechas no es válido: "desde" debe ser anterior o igual a "hasta".');
      return;
    }

    const employeeId = this.authService.currentUserId();
    if (!employeeId) {
      this.formError.set('No se pudo identificar tu usuario. Vuelve a iniciar sesión.');
      return;
    }

    this.loading.set(true);
    this.formError.set(null);
    const { from, to } = this.isoRange();
    this.reportsService.employeeSummary(employeeId, from, to).subscribe({
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

  private isoRange(): { from: string; to: string } {
    const { from, to } = this.form.getRawValue();
    return { from: `${from}T00:00:00Z`, to: `${to}T23:59:59Z` };
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
