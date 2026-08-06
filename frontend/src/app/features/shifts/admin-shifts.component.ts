import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { AdminEmployeesService, Employee } from '../admin-employees/admin-employees.service';
import { ShiftAssignment, ShiftTemplate, ShiftsService } from './shifts.service';

@Component({
  selector: 'app-admin-shifts',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './admin-shifts.component.html',
  styleUrl: './admin-shifts.component.scss'
})
export class AdminShiftsComponent {
  private readonly shiftsService = inject(ShiftsService);
  private readonly employeesService = inject(AdminEmployeesService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly savingTemplate = signal(false);
  readonly savingAssignment = signal(false);
  readonly templates = signal<ShiftTemplate[]>([]);
  readonly employees = signal<Employee[]>([]);
  readonly assignments = signal<ShiftAssignment[]>([]);
  readonly actionMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

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

  createTemplate(): void {
    if (this.templateForm.invalid || this.savingTemplate()) {
      this.templateForm.markAllAsTouched();
      return;
    }
    this.savingTemplate.set(true);
    this.errorMessage.set(null);
    this.shiftsService.createTemplate(this.templateForm.getRawValue()).subscribe({
      next: (template) => {
        this.templates.update((current) => [...current, template]);
        this.templateForm.reset({ name: '', startTime: '08:00', endTime: '16:00', plannedBreakMinutes: 30 });
        this.actionMessage.set('Plantilla de turno creada correctamente.');
        this.savingTemplate.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.errorMessagesService.fromProblem(error.error));
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
      this.errorMessage.set('La vigencia no puede terminar antes de empezar.');
      return;
    }
    this.savingAssignment.set(true);
    this.errorMessage.set(null);
    this.shiftsService.assign({
      employeeId: value.employeeId,
      shiftTemplateId: value.shiftTemplateId,
      validFrom: value.validFrom,
      validTo: value.validTo || null
    }).subscribe({
      next: (assignment) => {
        this.assignments.update((current) => [...current, assignment]);
        this.assignmentForm.reset({ employeeId: '', shiftTemplateId: '', validFrom: '', validTo: '' });
        this.actionMessage.set('Turno asignado correctamente.');
        this.savingAssignment.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.errorMessagesService.fromProblem(error.error));
        this.savingAssignment.set(false);
      }
    });
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
