import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { IsoDurationPipe } from '../../core/pipes/iso-duration.pipe';
import { AppShift, ShiftsService } from './shifts.service';

@Component({
  selector: 'app-shifts',
  imports: [ReactiveFormsModule, IsoDurationPipe],
  templateUrl: './shifts.component.html',
  styleUrl: './shifts.component.scss'
})
export class ShiftsComponent {
  private readonly shiftsService = inject(ShiftsService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly shifts = signal<AppShift[]>([]);
  readonly form = this.fb.nonNullable.group({
    date: [today(), [Validators.required]]
  });

  constructor() {
    this.load();
  }

  load(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    this.shiftsService.listOwn(this.form.controls.date.getRawValue()).subscribe({
      next: (shifts) => {
        this.shifts.set(shifts);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}
