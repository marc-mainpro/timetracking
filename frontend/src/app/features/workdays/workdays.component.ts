import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { IsoDurationPipe } from '../../core/pipes/iso-duration.pipe';
import { PagedWorkdays, WorkdaysService } from './workdays.service';

@Component({
  selector: 'app-workdays',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, IsoDurationPipe],
  templateUrl: './workdays.component.html',
  styleUrl: './workdays.component.scss'
})
export class WorkdaysComponent {
  private readonly workdaysService = inject(WorkdaysService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly page = signal(0);
  readonly result = signal<PagedWorkdays | null>(null);
  readonly filters = this.fb.nonNullable.group({
    from: [''],
    to: ['']
  });

  constructor() {
    this.load();
  }

  submitFilters(): void {
    this.page.set(0);
    this.load();
  }

  nextPage(): void {
    const current = this.result();
    if (!current || this.page() + 1 >= current.totalPages) {
      return;
    }
    this.page.update((page) => page + 1);
    this.load();
  }

  previousPage(): void {
    if (this.page() === 0) {
      return;
    }
    this.page.update((page) => page - 1);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    const filters = this.filters.getRawValue();
    this.workdaysService
      .list(this.page(), 10, this.toIso(filters.from), this.toIso(filters.to))
      .subscribe({
      next: (result) => {
        this.result.set(result);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  private toIso(value: string): string | undefined {
    return value ? new Date(value).toISOString() : undefined;
  }
}
