import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { Workday } from '../workdays/workdays.service';
import { CorrectionsService, PagedCorrections } from './corrections.service';

@Component({
  selector: 'app-corrections',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './corrections.component.html',
  styleUrl: './corrections.component.scss'
})
export class CorrectionsComponent {
  private readonly correctionsService = inject(CorrectionsService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute, { optional: true });

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly selectedWorkday = signal<Workday | null>(null);
  readonly result = signal<PagedCorrections | null>(null);
  readonly actionMessage = signal<string | null>(null);
  readonly formError = signal<string | null>(null);

  readonly form = this.fb.group({
    reason: this.fb.nonNullable.control('', [Validators.required]),
    startedAt: this.fb.nonNullable.control('', [Validators.required]),
    endedAt: this.fb.nonNullable.control('', [Validators.required]),
    breaks: this.fb.array([])
  }, { validators: [proposalValidator] });

  constructor() {
    this.loadCorrections();
    this.route?.queryParamMap.subscribe((params) => {
      const workdayId = params.get('workdayId');
      if (workdayId) {
        this.selectWorkday(workdayId);
      }
    });
  }

  get breaks(): FormArray {
    return this.form.controls.breaks;
  }

  selectWorkday(workdayId: string): void {
    this.loading.set(true);
    this.actionMessage.set(null);
    this.correctionsService.getOwnWorkday(workdayId).subscribe({
      next: (workday) => {
        this.selectedWorkday.set(workday);
        this.fillFromWorkday(workday);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.formError.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  addBreak(): void {
    this.breaks.push(this.createBreakGroup());
  }

  removeBreak(index: number): void {
    this.breaks.removeAt(index);
    this.form.updateValueAndValidity();
  }

  submit(): void {
    const workday = this.selectedWorkday();
    if (!workday) {
      this.formError.set('Selecciona una jornada cerrada desde el historial para preparar la solicitud.');
      return;
    }
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.formError.set(null);
    this.actionMessage.set(null);
    const value = this.form.getRawValue();
    const breaks = value.breaks as { startedAt: string; endedAt: string }[];
    this.correctionsService.request(workday.id, {
      reason: value.reason,
      proposedChanges: {
        startedAt: toIso(value.startedAt),
        endedAt: toIso(value.endedAt),
        breaks: breaks.map((breakEntry) => ({
          startedAt: toIso(breakEntry.startedAt),
          endedAt: toIso(breakEntry.endedAt)
        }))
      }
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.actionMessage.set('Solicitud enviada. El administrador ya puede revisarla.');
        this.loadCorrections();
      },
      error: (error) => {
        this.saving.set(false);
        this.formError.set(this.errorMessagesService.fromProblem(error.error));
        this.loadCorrections();
        this.selectWorkday(workday.id);
      }
    });
  }

  private loadCorrections(): void {
    this.loading.set(true);
    this.correctionsService.list(0, 20).subscribe({
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

  private fillFromWorkday(workday: Workday): void {
    this.breaks.clear();
    for (const breakEntry of workday.breaks) {
      this.breaks.push(this.createBreakGroup(
        toLocalInputValue(breakEntry.startedAt),
        breakEntry.endedAt ? toLocalInputValue(breakEntry.endedAt) : ''
      ));
    }
    this.form.reset({
      reason: '',
      startedAt: toLocalInputValue(workday.startedAt),
      endedAt: workday.endedAt ? toLocalInputValue(workday.endedAt) : '',
      breaks: this.breaks.getRawValue()
    });
  }

  private createBreakGroup(startedAt = '', endedAt = '') {
    return this.fb.nonNullable.group({
      startedAt: [startedAt, [Validators.required]],
      endedAt: [endedAt, [Validators.required]]
    });
  }
}

function proposalValidator(control: AbstractControl): ValidationErrors | null {
  const startedAt = control.get('startedAt')?.value as string;
  const endedAt = control.get('endedAt')?.value as string;
  const breaks = (control.get('breaks')?.value as { startedAt: string; endedAt: string }[]) ?? [];

  if (!startedAt || !endedAt) {
    return null;
  }

  const start = Date.parse(startedAt);
  const end = Date.parse(endedAt);
  if (Number.isNaN(start) || Number.isNaN(end) || start >= end) {
    return { invalidRange: true };
  }

  const normalizedBreaks = breaks
    .filter((breakEntry) => breakEntry.startedAt && breakEntry.endedAt)
    .map((breakEntry) => ({
      start: Date.parse(breakEntry.startedAt),
      end: Date.parse(breakEntry.endedAt)
    }))
    .sort((left, right) => left.start - right.start);

  for (let index = 0; index < normalizedBreaks.length; index += 1) {
    const current = normalizedBreaks[index];
    if (Number.isNaN(current.start) || Number.isNaN(current.end) || current.start >= current.end) {
      return { invalidBreakRange: true };
    }
    if (current.start < start || current.end > end) {
      return { breakOutsideWorkday: true };
    }
    const previous = normalizedBreaks[index - 1];
    if (previous && current.start < previous.end) {
      return { overlappingBreaks: true };
    }
  }

  return null;
}

function toIso(value: string): string {
  return new Date(value).toISOString();
}

function toLocalInputValue(value: string): string {
  const date = new Date(value);
  const offset = date.getTimezoneOffset();
  const normalized = new Date(date.getTime() - offset * 60_000);
  return normalized.toISOString().slice(0, 16);
}
