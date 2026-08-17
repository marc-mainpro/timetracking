import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { AdminEmployeesService, Employee } from '../admin-employees/admin-employees.service';
import { ShiftAssignment, ShiftTemplate, ShiftsService } from './shifts.service';

@Component({
  selector: 'app-admin-shifts',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-shifts.component.html',
  styleUrl: './admin-shifts.component.scss'
})
export class AdminShiftsComponent {
  private readonly shiftsService = inject(ShiftsService);
  private readonly employeesService = inject(AdminEmployeesService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  private readonly templateDialog = viewChild<ElementRef<HTMLDialogElement>>('templateDialog');
  private readonly assignDialog = viewChild<ElementRef<HTMLDialogElement>>('assignDialog');

  readonly loading = signal(false);
  readonly savingTemplate = signal(false);
  readonly savingAssignment = signal(false);
  readonly templates = signal<ShiftTemplate[]>([]);
  readonly employees = signal<Employee[]>([]);
  /**
   * Lo asignado durante esta visita, no las asignaciones vigentes: la API no
   * expone un listado. La pantalla lo dice en lugar de aparentar un histórico.
   */
  readonly assignments = signal<ShiftAssignment[]>([]);
  readonly actionMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly formError = signal<string | null>(null);

  readonly templateForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    startTime: ['08:00', [Validators.required]],
    endTime: ['16:00', [Validators.required]],
    plannedBreakMinutes: [30, [Validators.required, Validators.min(0), Validators.max(1440)]]
  });

  readonly assignmentForm = this.fb.nonNullable.group({
    employeeId: ['', [Validators.required]],
    shiftTemplateId: ['', [Validators.required]],
    validFrom: ['', [Validators.required]],
    validTo: ['']
  });

  constructor() {
    this.loadTemplates();
    this.loadEmployees();
  }

  // --- Paneles -------------------------------------------------------------

  openTemplate(): void {
    this.formError.set(null);
    this.templateDialog()?.nativeElement.showModal();
  }

  closeTemplate(): void {
    this.templateDialog()?.nativeElement.close();
  }

  openAssign(templateId?: string): void {
    this.formError.set(null);
    if (templateId) {
      this.assignmentForm.controls.shiftTemplateId.setValue(templateId);
    }
    this.assignDialog()?.nativeElement.showModal();
  }

  closeAssign(): void {
    this.assignDialog()?.nativeElement.close();
  }

  // --- Acciones ------------------------------------------------------------

  createTemplate(): void {
    if (this.templateForm.invalid || this.savingTemplate()) {
      this.templateForm.markAllAsTouched();
      return;
    }
    this.savingTemplate.set(true);
    this.formError.set(null);
    this.errorMessage.set(null);
    this.shiftsService.createTemplate(this.templateForm.getRawValue()).subscribe({
      next: (template) => {
        this.templates.update((current) => [...current, template]);
        this.templateForm.reset({ name: '', startTime: '08:00', endTime: '16:00', plannedBreakMinutes: 30 });
        this.actionMessage.set(`Plantilla «${template.name}» creada.`);
        this.savingTemplate.set(false);
        this.closeTemplate();
      },
      error: (error) => {
        this.formError.set(this.errorMessagesService.fromProblem(error.error));
        this.savingTemplate.set(false);
      }
    });
  }

  assignShift(): void {
    if (this.assignmentForm.invalid || this.savingAssignment()) {
      this.assignmentForm.markAllAsTouched();
      return;
    }
    const value = this.assignmentForm.getRawValue();
    if (value.validTo && value.validTo < value.validFrom) {
      this.formError.set('La vigencia no puede terminar antes de empezar.');
      return;
    }
    this.savingAssignment.set(true);
    this.formError.set(null);
    this.errorMessage.set(null);
    this.shiftsService
      .assign({
        employeeId: value.employeeId,
        shiftTemplateId: value.shiftTemplateId,
        validFrom: value.validFrom,
        validTo: value.validTo || null
      })
      .subscribe({
        next: (assignment) => {
          this.assignments.update((current) => [...current, assignment]);
          this.actionMessage.set(
            `${this.templateName(assignment.shiftTemplateId)} asignado a ${this.employeeName(assignment.employeeId)}.`
          );
          this.assignmentForm.reset({ employeeId: '', shiftTemplateId: '', validFrom: '', validTo: '' });
          this.savingAssignment.set(false);
          this.closeAssign();
        },
        error: (error) => {
          this.formError.set(this.errorMessagesService.fromProblem(error.error));
          this.savingAssignment.set(false);
        }
      });
  }

  // --- Presentación --------------------------------------------------------

  employeeName(employeeId: string): string {
    const employee = this.employees().find((candidate) => candidate.id === employeeId);
    return employee ? `${employee.firstName} ${employee.lastName}` : employeeId;
  }

  templateName(templateId: string): string {
    return this.templates().find((template) => template.id === templateId)?.name ?? templateId;
  }

  /** «8 h 30 min» a partir del horario, descontando la pausa prevista. */
  workedLabel(template: ShiftTemplate): string {
    const [startHour, startMinute] = template.startTime.split(':').map(Number);
    const [endHour, endMinute] = template.endTime.split(':').map(Number);
    let minutes = endHour * 60 + endMinute - (startHour * 60 + startMinute);
    if (minutes <= 0) {
      minutes += 24 * 60;
    }
    minutes -= template.plannedBreakMinutes;
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
  }

  private loadTemplates(): void {
    this.loading.set(true);
    this.shiftsService.listTemplates().subscribe({
      next: (templates) => {
        this.templates.set(templates);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.errorMessagesService.fromProblem(error.error));
        this.loading.set(false);
      }
    });
  }

  private loadEmployees(): void {
    this.employeesService.list(0, 100, 'ACTIVE').subscribe({
      next: (result) => this.employees.set(result.content),
      error: (error) => this.errorMessage.set(this.errorMessagesService.fromProblem(error.error))
    });
  }
}
