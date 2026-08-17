import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
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

/**
 * Ámbitos que se pueden asignar desde la aplicación. El backend admite además
 * `TEAM`, pero se queda fuera a propósito: el sistema no gestiona equipos
 * (ADR-0017), su identificador es opaco y nadie lo resuelve, así que ofrecerlo
 * asignaba algo que nunca llegaba a aplicarse.
 */
const SCOPES: { value: AssignmentScope; label: string }[] = [
  { value: 'TENANT', label: 'Toda la organización' },
  { value: 'EMPLOYEE', label: 'Empleado' }
];

/** Solo para leer asignaciones de equipo creadas antes de retirar el ámbito. */
const SCOPE_LABELS: Record<string, string> = {
  TENANT: 'Toda la organización',
  TEAM: 'Equipo',
  EMPLOYEE: 'Empleado'
};

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activo',
  ARCHIVED: 'Archivado'
};

const FILTER_LABELS: Record<string, string> = {
  '': 'Todos',
  ACTIVE: 'Activos',
  ARCHIVED: 'Archivados'
};

/** Retirada pendiente de confirmar: un calendario o una asignación. */
interface PendingRemoval {
  readonly kind: 'calendar' | 'assignment';
  readonly id: string;
  readonly title: string;
  readonly consequence: string;
  readonly confirmLabel: string;
}

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

  private readonly editorDialog = viewChild<ElementRef<HTMLDialogElement>>('editorDialog');
  private readonly effectiveDialog = viewChild<ElementRef<HTMLDialogElement>>('effectiveDialog');
  private readonly confirmDialog = viewChild<ElementRef<HTMLDialogElement>>('confirmDialog');

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
  readonly pendingRemoval = signal<PendingRemoval | null>(null);

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
    return employee ? `${employee.firstName} ${employee.lastName}` : employeeId;
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

  openCreate(): void {
    this.startCreate();
    this.editorDialog()?.nativeElement.showModal();
  }

  closeEditor(): void {
    this.editorDialog()?.nativeElement.close();
  }

  openEffective(): void {
    this.effectiveError.set(null);
    this.effective.set(null);
    this.effectiveDialog()?.nativeElement.showModal();
  }

  closeEffective(): void {
    this.effectiveDialog()?.nativeElement.close();
  }

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
      next: (detail) => {
        this.fillForm(detail);
        this.editorDialog()?.nativeElement.showModal();
      },
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
        this.message.set(`Calendario «${detail.name}» guardado.`);
        this.closeEditor();
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
    this.ask({
      kind: 'calendar',
      id: calendar.id,
      title: `Archivar ${calendar.name}`,
      consequence:
        'Deja de poder asignarse. Las jornadas ya calculadas con él no cambian.',
      confirmLabel: 'Archivar'
    });
  }

  confirmRemoval(): void {
    const pending = this.pendingRemoval();
    if (!pending) {
      return;
    }
    this.cancelRemoval();
    this.error.set(null);

    if (pending.kind === 'calendar') {
      this.calendarsService.archive(pending.id).subscribe({
        next: () => {
          this.message.set(`${pending.title.replace('Archivar ', '')} archivado.`);
          if (this.editingId() === pending.id) {
            this.startCreate();
          }
          this.load();
        },
        error: (err) => this.error.set(this.errorMessagesService.fromProblem(err.error))
      });
      return;
    }

    this.calendarsService.removeAssignment(pending.id).subscribe({
      next: () => {
        this.message.set('Asignación retirada.');
        this.loadAssignments();
      },
      error: (err) => this.error.set(this.errorMessagesService.fromProblem(err.error))
    });
  }

  cancelRemoval(): void {
    this.confirmDialog()?.nativeElement.close();
    this.pendingRemoval.set(null);
  }

  private ask(removal: PendingRemoval): void {
    this.error.set(null);
    this.message.set(null);
    this.pendingRemoval.set(removal);
    this.confirmDialog()?.nativeElement.showModal();
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
          this.message.set(`${this.calendarName(raw.calendarId)} asignado.`);
          this.assignForm.reset({ calendarId: '', scope: 'TENANT', targetId: '' });
          this.loadAssignments();
        },
        error: (err) => this.assignError.set(this.errorMessagesService.fromProblem(err.error))
      });
  }

  removeAssignment(assignmentId: string): void {
    this.ask({
      kind: 'assignment',
      id: assignmentId,
      title: 'Retirar la asignación',
      consequence:
        'Quien dependiera de ella pasa a regirse por el ámbito más general que quede.',
      confirmLabel: 'Retirar'
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
    this.calendarsService.resolveEffective(raw.employeeId, raw.date).subscribe({
      next: (effective) => this.effective.set(effective),
      error: (err) => this.effectiveError.set(this.errorMessagesService.fromProblem(err.error))
    });
  }

  // --- Utilidades de plantilla ----------------------------------------------

  calendarName(calendarId: string): string {
    return this.result()?.content.find((calendar) => calendar.id === calendarId)?.name ?? calendarId;
  }

  statusLabel(status: string): string {
    return STATUS_LABELS[status] ?? status;
  }

  filterLabel(status: string): string {
    return FILTER_LABELS[status] ?? status;
  }

  sourceLabel(source: string): string {
    return SOURCE_LABELS[source] ?? source;
  }

  scopeLabel(scope: string): string {
    return SCOPE_LABELS[scope] ?? scope;
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
