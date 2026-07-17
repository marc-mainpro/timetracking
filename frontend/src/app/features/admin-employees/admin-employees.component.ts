import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { AdminEmployeesService, Employee, PagedEmployees } from './admin-employees.service';

@Component({
  selector: 'app-admin-employees',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './admin-employees.component.html',
  styleUrl: './admin-employees.component.scss'
})
export class AdminEmployeesComponent {
  private readonly employeesService = inject(AdminEmployeesService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly page = signal(0);
  readonly result = signal<PagedEmployees | null>(null);
  readonly selectedStatus = signal<string>('');
  readonly search = signal('');
  readonly editingEmployee = signal<Employee | null>(null);
  readonly formError = signal<string | null>(null);
  readonly actionMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(10)]],
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    tenantAdmin: [false],
    employee: [true]
  });

  readonly visibleEmployees = computed(() => {
    const query = this.search().trim().toLowerCase();
    const employees = this.result()?.content ?? [];
    if (!query) {
      return employees;
    }
    return employees.filter(
      (employee) =>
        employee.email.toLowerCase().includes(query) ||
        `${employee.firstName} ${employee.lastName}`.toLowerCase().includes(query)
    );
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.actionMessage.set(null);
    this.employeesService.list(this.page(), 20, this.selectedStatus() || undefined).subscribe({
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

  applyStatus(status: string): void {
    this.selectedStatus.set(status);
    this.page.set(0);
    this.load();
  }

  edit(employee: Employee): void {
    this.editingEmployee.set(employee);
    this.formError.set(null);
    this.form.setValue({
      email: employee.email,
      password: '**********',
      firstName: employee.firstName,
      lastName: employee.lastName,
      tenantAdmin: employee.roles.includes('TENANT_ADMIN'),
      employee: employee.roles.includes('EMPLOYEE')
    });
    this.form.controls.email.disable();
    this.form.controls.password.disable();
  }

  resetForm(): void {
    this.editingEmployee.set(null);
    this.formError.set(null);
    this.form.reset({
      email: '',
      password: '',
      firstName: '',
      lastName: '',
      tenantAdmin: false,
      employee: true
    });
    this.form.controls.email.enable();
    this.form.controls.password.enable();
  }

  submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const roles = this.currentRoles();
    if (roles.length === 0) {
      this.formError.set('Debes asignar al menos un rol.');
      return;
    }

    this.saving.set(true);
    this.formError.set(null);
    const editing = this.editingEmployee();

    if (editing) {
      this.employeesService
        .update(editing.id, {
          firstName: this.form.controls.firstName.getRawValue(),
          lastName: this.form.controls.lastName.getRawValue()
        })
        .subscribe({
          next: (updated) => {
            this.employeesService.assignRoles(updated.id, roles).subscribe({
              next: () => this.afterMutation('Empleado actualizado.'),
              error: (error) => this.handleMutationError(error.error)
            });
          },
          error: (error) => this.handleMutationError(error.error)
        });
      return;
    }

    this.employeesService
      .create({
        email: this.form.controls.email.getRawValue(),
        password: this.form.controls.password.getRawValue(),
        firstName: this.form.controls.firstName.getRawValue(),
        lastName: this.form.controls.lastName.getRawValue(),
        roles
      })
      .subscribe({
        next: () => this.afterMutation('Empleado creado.'),
        error: (error) => this.handleMutationError(error.error)
      });
  }

  toggleStatus(employee: Employee): void {
    const action = employee.status === 'ACTIVE' ? 'desactivar' : 'activar';
    if (!window.confirm(`¿Seguro que quieres ${action} a ${employee.firstName}?`)) {
      return;
    }

    const request = employee.status === 'ACTIVE'
      ? this.employeesService.deactivate(employee.id)
      : this.employeesService.activate(employee.id);

    request.subscribe({
      next: () => this.afterMutation(`Empleado ${action}do correctamente.`),
      error: (error) => {
        this.actionMessage.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
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

  private currentRoles(): string[] {
    return [
      this.form.controls.employee.value ? 'EMPLOYEE' : null,
      this.form.controls.tenantAdmin.value ? 'TENANT_ADMIN' : null
    ].filter((role): role is string => !!role);
  }

  private afterMutation(message: string): void {
    this.saving.set(false);
    this.actionMessage.set(message);
    this.resetForm();
    this.load();
  }

  private handleMutationError(problem: unknown): void {
    this.saving.set(false);
    this.formError.set(this.errorMessagesService.fromProblem(problem as never));
  }
}
