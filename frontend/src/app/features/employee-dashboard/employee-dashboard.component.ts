import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { interval } from 'rxjs';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { Workday, WorkdaysService } from '../workdays/workdays.service';

@Component({
  selector: 'app-employee-dashboard',
  imports: [CommonModule, RouterLink],
  templateUrl: './employee-dashboard.component.html',
  styleUrl: './employee-dashboard.component.scss'
})
export class EmployeeDashboardComponent {
  private readonly workdaysService = inject(WorkdaysService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly actionLoading = signal(false);
  readonly currentWorkday = signal<Workday | null>(null);
  readonly infoMessage = signal<string | null>(null);
  readonly now = signal(Date.now());

  constructor() {
    const subscription = interval(1000).subscribe(() => this.now.set(Date.now()));
    this.destroyRef.onDestroy(() => subscription.unsubscribe());
    this.reloadCurrent();
  }

  reloadCurrent(): void {
    this.loading.set(true);
    this.infoMessage.set(null);
    this.workdaysService.getCurrent().subscribe({
      next: (workday) => {
        this.currentWorkday.set(workday);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        if (error.status === 404) {
          this.currentWorkday.set(null);
          return;
        }
        this.infoMessage.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  startWorkday(): void {
    this.runAction(() => this.workdaysService.start());
  }

  startBreak(): void {
    this.runAction(() => this.workdaysService.startBreak());
  }

  endBreak(): void {
    this.runAction(() => this.workdaysService.endBreak());
  }

  endWorkday(): void {
    this.runAction(() => this.workdaysService.endWorkday());
  }

  workedDuration(): string {
    const workday = this.currentWorkday();
    if (!workday) {
      return '00:00:00';
    }
    if (workday.endedAt) {
      return this.formatDuration(workday.startedAt, workday.endedAt, workday);
    }
    return this.formatDuration(workday.startedAt, new Date(this.now()).toISOString(), workday);
  }

  hasOpenWorkday(): boolean {
    return !!this.currentWorkday();
  }

  isOnBreak(): boolean {
    return this.currentWorkday()?.status === 'ON_BREAK';
  }

  canStartBreak(): boolean {
    return this.currentWorkday()?.status === 'OPEN';
  }

  private runAction(factory: () => import('rxjs').Observable<Workday>): void {
    if (this.actionLoading()) {
      return;
    }
    this.actionLoading.set(true);
    this.infoMessage.set(null);
    factory().subscribe({
      next: (workday) => {
        this.currentWorkday.set(workday.status === 'CLOSED' ? null : workday);
        this.actionLoading.set(false);
      },
      error: (error) => {
        this.actionLoading.set(false);
        this.infoMessage.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  private formatDuration(startedAt: string, endedAt: string, workday: Workday): string {
    const total = new Date(endedAt).getTime() - new Date(startedAt).getTime();
    const breaks = workday.breaks.reduce((acc, breakEntry) => {
      if (!breakEntry.endedAt) {
        return acc;
      }
      return acc + (new Date(breakEntry.endedAt).getTime() - new Date(breakEntry.startedAt).getTime());
    }, 0);
    const diff = Math.max(total - breaks, 0);
    const hours = Math.floor(diff / 3_600_000).toString().padStart(2, '0');
    const minutes = Math.floor((diff % 3_600_000) / 60_000).toString().padStart(2, '0');
    const seconds = Math.floor((diff % 60_000) / 1000).toString().padStart(2, '0');
    return `${hours}:${minutes}:${seconds}`;
  }
}
