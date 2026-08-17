import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { ClockService } from '../../core/services/clock.service';
import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { Workday, WorkdaysService } from '../workdays/workdays.service';

@Component({
  selector: 'app-employee-dashboard',
  imports: [DatePipe, RouterLink],
  templateUrl: './employee-dashboard.component.html',
  styleUrl: './employee-dashboard.component.scss'
})
export class EmployeeDashboardComponent {
  private readonly workdaysService = inject(WorkdaysService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  /** El reloj compartido de la app; ver `ClockService`. */
  private readonly clock = inject(ClockService);

  readonly loading = signal(true);
  readonly actionLoading = signal(false);
  readonly currentWorkday = signal<Workday | null>(null);
  readonly infoMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  /** Pausa sin cerrar, si la hay: es la que está corriendo ahora mismo. */
  readonly openBreak = computed(
    () => this.currentWorkday()?.breaks.find((entry) => !entry.endedAt) ?? null
  );

  /**
   * Tiempo trabajado, con la pausa en curso descontada.
   *
   * <p>Antes solo se restaban las pausas cerradas, así que durante una pausa el
   * contador seguía subiendo y al reanudar saltaba hacia atrás de golpe.
   */
  readonly workedMillis = computed(() => {
    const workday = this.currentWorkday();
    if (!workday) {
      return 0;
    }
    const end = workday.endedAt ? Date.parse(workday.endedAt) : this.clock.now();
    const paused = workday.breaks.reduce((total, entry) => {
      const breakEnd = entry.endedAt ? Date.parse(entry.endedAt) : end;
      return total + (breakEnd - Date.parse(entry.startedAt));
    }, 0);
    return Math.max(end - Date.parse(workday.startedAt) - paused, 0);
  });

  /** Lo que lleva la pausa actual; 0 si no hay ninguna abierta. */
  readonly breakMillis = computed(() => {
    const entry = this.openBreak();
    return entry ? Math.max(this.clock.now() - Date.parse(entry.startedAt), 0) : 0;
  });

  readonly workedLabel = computed(() => this.formatClock(this.workedMillis()));
  readonly breakLabel = computed(() => this.formatShort(this.breakMillis()));

  /** Pausas ya terminadas: las que cuentan como descanso consumido. */
  readonly closedBreaks = computed(
    () => this.currentWorkday()?.breaks.filter((entry) => entry.endedAt).length ?? 0
  );

  constructor() {
    this.reloadCurrent();
  }

  reloadCurrent(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.workdaysService.getCurrent().subscribe({
      next: (workday) => {
        this.currentWorkday.set(workday);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        // Sin jornada abierta el backend responde 404: es el estado normal de
        // quien todavía no ha fichado, no un fallo que haya que enseñar.
        if (error.status === 404) {
          this.currentWorkday.set(null);
          return;
        }
        this.errorMessage.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  startWorkday(): void {
    this.runAction(() => this.workdaysService.start(), 'Jornada iniciada.');
  }

  startBreak(): void {
    this.runAction(() => this.workdaysService.startBreak(), 'Pausa iniciada.');
  }

  endBreak(): void {
    this.runAction(() => this.workdaysService.endBreak(), 'Pausa terminada.');
  }

  endWorkday(): void {
    this.runAction(() => this.workdaysService.endWorkday(), 'Jornada cerrada.');
  }

  isOnBreak(): boolean {
    return this.currentWorkday()?.status === 'ON_BREAK';
  }

  canStartBreak(): boolean {
    return this.currentWorkday()?.status === 'OPEN';
  }

  private runAction(factory: () => Observable<Workday>, message: string): void {
    if (this.actionLoading()) {
      return;
    }
    this.actionLoading.set(true);
    this.infoMessage.set(null);
    this.errorMessage.set(null);
    factory().subscribe({
      next: (workday) => {
        this.currentWorkday.set(workday.status === 'CLOSED' ? null : workday);
        this.infoMessage.set(message);
        this.actionLoading.set(false);
      },
      error: (error) => {
        this.actionLoading.set(false);
        this.errorMessage.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  /** `HH:MM:SS`, el formato del cronómetro. */
  private formatClock(millis: number): string {
    const hours = Math.floor(millis / 3_600_000);
    const minutes = Math.floor((millis % 3_600_000) / 60_000);
    const seconds = Math.floor((millis % 60_000) / 1000);
    return [hours, minutes, seconds].map((part) => String(part).padStart(2, '0')).join(':');
  }

  /** «45 min», «1 h 05 min»: la pausa se lee de un vistazo, sin segundos. */
  private formatShort(millis: number): string {
    const totalMinutes = Math.floor(millis / 60_000);
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return hours === 0 ? `${minutes} min` : `${hours} h ${String(minutes).padStart(2, '0')} min`;
  }
}
