import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AdminEmployeesService, Employee } from '../admin-employees/admin-employees.service';
import { ErrorMessagesService } from '../../core/services/error-messages.service';
import {
  AssignmentScope,
  CalendarDetail,
  CalendarSummary,
  CalendarsService,
  DayRule,
  EffectiveCalendar,
  Holiday,
  PagedAssignments,
  PagedCalendars,
  SaveCalendarPayload,
  SpecialDay,
  WeekDay
} from './calendars.service';

const STATUS_FILTERS = ['', 'ACTIVE', 'ARCHIVED'] as const;

const WEEK_DAYS: { value: WeekDay; label: string }[] = [
  { value: 'MONDAY', label: 'Lunes' },
  { value: 'TUESDAY', label: 'Martes' },
  { value: 'WEDNESDAY', label: 'Miércoles' },
  { value: 'THURSDAY', label: 'Jueves' },
  { value: 'FRIDAY', label: 'Viernes' },
  { value: 'SATURDAY', label: 'Sábado' },
  { value: 'SUNDAY', label: 'Domingo' }
];

const SCOPES: { value: AssignmentScope; label: string }[] = [
  { value: 'TENANT', label: 'Toda la organización' },
  { value: 'TEAM', label: 'Equipo' },
  { value: 'EMPLOYEE', label: 'Empleado' }
];

const SOURCE_LABELS: Record<string, string> = {
  WEEKLY_RULE: 'Regla semanal',
  HOLIDAY: 'Festivo',
  SPECIAL_DAY: 'Jornada especial',
  OUT_OF_VALIDITY: 'Fuera de vigencia'
};

/** Jornada estándar de partida al crear un calendario: 8 h de lunes a viernes. */
const DEFAULT_WORKING_MINUTES = 480;

/**
 * Administración de calendarios laborales (T70-05, RF-CAL-001..007): listado,
 * alta/edición con reglas semanales, festivos y jornadas especiales, asignación
 * por ámbito y consulta del calendario efectivo.
 */
@Component({
  selector: 'app-admin-calendars',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-calendars.component.html',
  styleUrl: './admin-calendars.component.scss'
})
export class AdminCalendarsComponent {
  private readonly calendarsService = inject(CalendarsService);
  private readonly employeesService = inject(AdminEmployeesService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  readonly statusFilters = STATUS_FILTERS;
  readonly weekDays = WEEK_DAYS;
  readonly scopes = SCOPES;

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly page = signal(0);
  readonly result = signal<PagedCalendars | null>(null);
  readonly selectedStatus = signal<string>('');
  readonly editingId = signal<string | null>(null);
  readonly assignments = signal<PagedAssignments | null>(null);
  readonly effective = signal<EffectiveCalendar | null>(null);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly assignError = signal<string | null>(null);
  readonly effectiveError = signal<string | null>(null);
  readonly employees = signal<Employee[]>([]);

  /** Reglas semanales en edición. Se manejan como signal y no como FormArray: son 7 filas fijas. */
  readonly dayRules = signal<DayRule[]>(this.defaultDayRules());
  readonly holidays = signal<Holiday[]>([]);
  readonly specialDays = signal<SpecialDay[]>([]);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    timezone: ['Europe/Madrid', [Validators.required]],
    validFrom: ['', [Validators.required]],
    validTo: ['']
  });

  readonly holidayForm = this.fb.nonNullable.group({
    date: ['', [Validators.required]],
    name: ['', [Validators.required]]
  });

  readonly specialDayForm = this.fb.nonNullable.group({
    date: ['', [Validators.required]],
    name: ['', [Validators.required]],
    expectedMinutes: [300, [Validators.required, Validators.min(0), Validators.max(1440)]]
  });

  readonly assignForm = this.fb.nonNullable.group({
    calendarId: ['', [Validators.required]],
    scope: ['TENANT', [Validators.required]],
    targetId: ['']
  });

  readonly effectiveForm = this.fb.nonNullable.group({
    employeeId: ['', [Validators.required]],
    teamId: [''],
    date: ['', [Validators.required]]
  });

  constructor() {
    this.load();
    this.loadAssignments();
    this.loadEmployees();
  }

  private loadEmployees(): void {
    this.employeesService.list(0, 100).subscribe({
      next: (result) => this.employees.set(result.content),
      error: () => this.employees.set([])
    });
  }

  employeeName(employeeId: string): string {
    const employee = this.employees().find((candidate) => candidate.id === employeeId);
    return employee ? `${employee.lastName} ${employee.firstName}` : employeeId;
  }

  private defaultDayRules(): DayRule[] {
    return WEEK_DAYS.map((day) => ({
      dayOfWeek: day.value,
      working: day.value !== 'SATURDAY' && day.value !== 'SUNDAY',
      expectedMinutes:
        day.value === 'SATURDAY' || day.value === 'SUNDAY' ? 0 : DEFAULT_WORKING_MINUTES
    }));
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.calendarsService.list(this.page(), 20, this.selectedStatus() || undefined).subscribe({
      next: (result) => {
        this.result.set(result);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.errorMessagesService.fromProblem(err.error));
      }
    });
  }

  loadAssignments(): void {
    this.calendarsService.listAssignments(0, 50).subscribe({
      next: (result) => this.assignments.set(result),
      error: () => this.assignments.set(null)
    });
  }

  applyStatus(status: string): void {
    this.selectedStatus.set(status);
    this.page.set(0);
    this.load();
  }

  // --- Alta y edición ---------------------------------------------------

  startCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.form.reset({ name: '', timezone: 'Europe/Madrid', validFrom: '', validTo: '' });
    this.dayRules.set(this.defaultDayRules());
    this.holidays.set([]);
    this.specialDays.set([]);
  }

  startEdit(calendar: CalendarSummary): void {
    this.formError.set(null);
    this.calendarsService.get(calendar.id).subscribe({
      next: (detail) => this.fillForm(detail),
      error: (err) => this.error.set(this.errorMessagesService.fromProblem(err.error))
    });
  }

  private fillForm(detail: CalendarDetail): void {
    this.editingId.set(detail.id);
    this.form.setValue({
      name: detail.name,
      timezone: detail.timezone,
      validFrom: detail.validFrom,
      validTo: detail.validTo ?? ''
    });
    // Las reglas ausentes en el backend significan "día no laborable".
    const byDay = new Map(detail.dayRules.map((rule) => [rule.dayOfWeek, rule]));
    this.dayRules.set(
      WEEK_DAYS.map(
        (day) => byDay.get(day.value) ?? { dayOfWeek: day.value, working: false, expectedMinutes: 0 }
      )
    );
    this.holidays.set([...detail.holidays]);
    this.specialDays.set([...detail.specialDays]);
  }

  toggleWorking(dayOfWeek: WeekDay): void {
    this.dayRules.update((rules) =>
      rules.map((rule) =>
        rule.dayOfWeek === dayOfWeek
          ? {
              ...rule,
              working: !rule.working,
              // El backend rechaza un día laborable con 0 minutos y uno no
              // laborable con minutos: se ajusta al alternar.
              expectedMinutes: !rule.working ? DEFAULT_WORKING_MINUTES : 0
            }
          : rule
      )
    );
  }

  changeMinutes(dayOfWeek: WeekDay, value: string): void {
    const minutes = Number(value);
    this.dayRules.update((rules) =>
      rules.map((rule) =>
        rule.dayOfWeek === dayOfWeek
          ? { ...rule, expectedMinutes: Number.isFinite(minutes) ? minutes : 0 }
          : rule
      )
    );
  }

  addHoliday(): void {
    if (this.holidayForm.invalid) {
      this.holidayForm.markAllAsTouched();
      return;
    }
    const holiday = this.holidayForm.getRawValue();
    this.holidays.update((current) => [...current, holiday]);
    this.holidayForm.reset({ date: '', name: '' });
  }

  removeHoliday(date: string): void {
    this.holidays.update((current) => current.filter((holiday) => holiday.date !== date));
  }

  addSpecialDay(): void {
    if (this.specialDayForm.invalid) {
      this.specialDayForm.markAllAsTouched();
      return;
    }
    const specialDay = this.specialDayForm.getRawValue();
    this.specialDays.update((current) => [...current, specialDay]);
    this.specialDayForm.reset({ date: '', name: '', expectedMinutes: 300 });
  }

  removeSpecialDay(date: string): void {
    this.specialDays.update((current) => current.filter((day) => day.date !== date));
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const payload: SaveCalendarPayload = {
      name: raw.name,
      timezone: raw.timezone,
      validFrom: raw.validFrom,
      validTo: raw.validTo ? raw.validTo : null,
      dayRules: this.dayRules(),
      holidays: this.holidays(),
      specialDays: this.specialDays()
    };

    this.saving.set(true);
    this.formError.set(null);
    this.message.set(null);
    const editingId = this.editingId();
    const request = editingId
      ? this.calendarsService.update(editingId, payload)
      : this.calendarsService.create(payload);

    request.subscribe({
      next: (detail) => {
        this.saving.set(false);
        this.message.set(`Calendario "${detail.name}" guardado.`);
        this.startCreate();
        this.load();
      },
      error: (err) => {
        this.saving.set(false);
        this.formError.set(this.errorMessagesService.fromProblem(err.error));
      }
    });
  }

  archive(calendar: CalendarSummary): void {
    if (!window.confirm(`¿Archivar el calendario "${calendar.name}"?`)) {
      return;
    }
    this.error.set(null);
    this.calendarsService.archive(calendar.id).subscribe({
      next: () => {
        this.message.set(`Calendario "${calendar.name}" archivado.`);
        if (this.editingId() === calendar.id) {
          this.startCreate();
        }
        this.load();
      },
      error: (err) => this.error.set(this.errorMessagesService.fromProblem(err.error))
    });
  }

  // --- Asignaciones ------------------------------------------------------

  assign(): void {
    if (this.assignForm.invalid) {
      this.assignForm.markAllAsTouched();
      return;
    }
    const raw = this.assignForm.getRawValue();
    this.assignError.set(null);
    this.calendarsService
      .assign({
        calendarId: raw.calendarId,
        scope: raw.scope as AssignmentScope,
        // El ámbito de organización no lleva destinatario.
        targetId: raw.scope === 'TENANT' ? null : raw.targetId || null
      })
      .subscribe({
        next: () => {
          this.message.set('Calendario asignado.');
          this.assignForm.reset({ calendarId: '', scope: 'TENANT', targetId: '' });
          this.loadAssignments();
        },
        error: (err) => this.assignError.set(this.errorMessagesService.fromProblem(err.error))
      });
  }

  removeAssignment(assignmentId: string): void {
    if (!window.confirm('¿Retirar esta asignación?')) {
      return;
    }
    this.calendarsService.removeAssignment(assignmentId).subscribe({
      next: () => {
        this.message.set('Asignación retirada.');
        this.loadAssignments();
      },
      error: (err) => this.error.set(this.errorMessagesService.fromProblem(err.error))
    });
  }

  // --- Calendario efectivo -------------------------------------------------

  resolveEffective(): void {
    if (this.effectiveForm.invalid) {
      this.effectiveForm.markAllAsTouched();
      return;
    }
    const raw = this.effectiveForm.getRawValue();
    this.effectiveError.set(null);
    this.effective.set(null);
    this.calendarsService.resolveEffective(raw.employeeId, raw.date, raw.teamId || undefined).subscribe({
      next: (effective) => this.effective.set(effective),
      error: (err) => this.effectiveError.set(this.errorMessagesService.fromProblem(err.error))
    });
  }

  // --- Utilidades de plantilla ----------------------------------------------

  calendarName(calendarId: string): string {
    return this.result()?.content.find((calendar) => calendar.id === calendarId)?.name ?? calendarId;
  }

  statusLabel(status: string): string {
    return status === '' ? 'Todos' : status === 'ACTIVE' ? 'Activos' : 'Archivados';
  }

  sourceLabel(source: string): string {
    return SOURCE_LABELS[source] ?? source;
  }

  scopeLabel(scope: string): string {
    return this.scopes.find((candidate) => candidate.value === scope)?.label ?? scope;
  }

  dayLabel(dayOfWeek: string): string {
    return this.weekDays.find((day) => day.value === dayOfWeek)?.label ?? dayOfWeek;
  }

  formatMinutes(minutes: number): string {
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return `${hours}h ${String(rest).padStart(2, '0')}m`;
  }

  nextPage(): void {
    const result = this.result();
    if (!result || result.page + 1 >= result.totalPages) {
      return;
    }
    this.page.update((value) => value + 1);
    this.load();
  }

  previousPage(): void {
    if (this.page() === 0) {
      return;
    }
    this.page.update((value) => value - 1);
    this.load();
  }
}
