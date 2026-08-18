import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { AdminEmployeesService, Employee, PagedEmployees } from './admin-employees.service';
import { Role } from '../../core/models/role';

const STATUS_FILTERS = ['', 'ACTIVE', 'INACTIVE'] as const;

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activo',
  INACTIVE: 'Inactivo'
};

const FILTER_LABELS: Record<string, string> = {
  '': 'Todos',
  ACTIVE: 'Activos',
  INACTIVE: 'Inactivos'
};

/** Los roles se guardan con el nombre del backend y se muestran traducidos. */
const ROLE_LABELS: Record<string, string> = {
  EMPLOYEE: 'Empleado',
  TENANT_ADMIN: 'Administrador'
};

@Component({
  selector: 'app-admin-employees',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-employees.component.html',
  styleUrl: './admin-employees.component.scss'
})
export class AdminEmployeesComponent {
  private readonly employeesService = inject(AdminEmployeesService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  private readonly formDialog = viewChild<ElementRef<HTMLDialogElement>>('formDialog');
  private readonly confirmDialog = viewChild<ElementRef<HTMLDialogElement>>('confirmDialog');

  readonly statusFilters = STATUS_FILTERS;
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly page = signal(0);
  readonly result = signal<PagedEmployees | null>(null);
  readonly selectedStatus = signal<string>('');
  readonly search = signal('');
  readonly editingEmployee = signal<Employee | null>(null);
  readonly formError = signal<string | null>(null);
  readonly actionMessage = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly pendingToggle = signal<Employee | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(10)]],
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    tenantAdmin: [false],
    employee: [true]
  });

  /**
   * Filtro en cliente sobre la página cargada. El endpoint no acepta búsqueda,
   * así que esto acota lo que ya está en pantalla; el campo lo dice para no
   * prometer una búsqueda sobre toda la plantilla.
   */
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
    this.error.set(null);
    this.employeesService.list(this.page(), 20, this.selectedStatus() || undefined).subscribe({
      next: (result) => {
        this.result.set(result);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.error.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  applyStatus(status: string): void {
    this.selectedStatus.set(status);
    this.page.set(0);
    this.load();
  }

  // --- Alta y edición ------------------------------------------------------

  create(): void {
    this.resetForm();
    this.formDialog()?.nativeElement.showModal();
  }

  edit(employee: Employee): void {
    this.editingEmployee.set(employee);
    this.formError.set(null);
    this.form.setValue({
      email: employee.email,
      password: '',
      firstName: employee.firstName,
      lastName: employee.lastName,
      tenantAdmin: employee.roles.includes('TENANT_ADMIN'),
      employee: employee.roles.includes('EMPLOYEE')
    });
    // Ni el correo ni la contraseña se cambian desde aquí: deshabilitarlos los
    // deja fuera de la validación y del payload.
    this.form.controls.email.disable();
    this.form.controls.password.disable();
    this.formDialog()?.nativeElement.showModal();
  }

  closeForm(): void {
    this.formDialog()?.nativeElement.close();
    this.resetForm();
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
      this.formError.set('Asigna al menos un rol.');
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
              next: () => this.afterMutation(`${updated.firstName} ${updated.lastName} actualizado.`),
              error: (error) => this.handleMutationError(error.error)
            });
          },
          error: (error) => this.handleMutationError(error.error)
        });
      return;
    }

    const name = this.form.controls.firstName.getRawValue();
    this.employeesService
      .create({
        email: this.form.controls.email.getRawValue(),
        password: this.form.controls.password.getRawValue(),
        firstName: name,
        lastName: this.form.controls.lastName.getRawValue(),
        roles
      })
      .subscribe({
        next: () => this.afterMutation(`${name} dado de alta.`),
        error: (error) => this.handleMutationError(error.error)
      });
  }

  // --- Activar y desactivar ------------------------------------------------

  toggleStatus(employee: Employee): void {
    this.error.set(null);
    this.actionMessage.set(null);
    this.pendingToggle.set(employee);
    this.confirmDialog()?.nativeElement.showModal();
  }

  confirmToggle(): void {
    const employee = this.pendingToggle();
    if (!employee) {
      return;
    }
    const deactivating = employee.status === 'ACTIVE';
    const request = deactivating
      ? this.employeesService.deactivate(employee.id)
      : this.employeesService.activate(employee.id);

    this.cancelToggle();
    request.subscribe({
      next: () =>
        this.afterMutation(
          `${employee.firstName} ${employee.lastName} ${deactivating ? 'desactivado' : 'activado'}.`
        ),
      error: (error) => this.error.set(this.errorMessagesService.fromProblem(error.error))
    });
  }

  cancelToggle(): void {
    this.confirmDialog()?.nativeElement.close();
    this.pendingToggle.set(null);
  }

  // --- Presentación --------------------------------------------------------

  statusLabel(status: string): string {
    return STATUS_LABELS[status] ?? status;
  }

  filterLabel(status: string): string {
    return FILTER_LABELS[status] ?? status;
  }

  rolesLabel(roles: string[]): string {
    return roles.map((role) => ROLE_LABELS[role] ?? role).join(' · ');
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

  private currentRoles(): Role[] {
    return [
      this.form.controls.employee.value ? 'EMPLOYEE' : null,
      this.form.controls.tenantAdmin.value ? 'TENANT_ADMIN' : null
    ].filter((role): role is Role => !!role);
  }

  private afterMutation(message: string): void {
    this.saving.set(false);
    this.actionMessage.set(message);
    this.formDialog()?.nativeElement.close();
    this.resetForm();
    this.load();
  }

  private handleMutationError(problem: unknown): void {
    this.saving.set(false);
    this.formError.set(this.errorMessagesService.fromProblem(problem as never));
  }
}
