import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AdminEmployeesComponent } from './admin-employees.component';
import { Employee } from './admin-employees.service';

describe('AdminEmployeesComponent', () => {
  let component: AdminEmployeesComponent;
  let fixture: ComponentFixture<AdminEmployeesComponent>;
  let httpMock: HttpTestingController;

  const activeEmployee: Employee = {
    id: 'e-1',
    email: 'ana@acme.test',
    firstName: 'Ana',
    lastName: 'Ruiz',
    status: 'ACTIVE',
    roles: ['EMPLOYEE'],
    createdAt: '2026-01-10T09:00:00Z',
    updatedAt: '2026-01-10T09:00:00Z'
  };

  function flushList(content: Employee[]): void {
    httpMock.expectOne((request) => request.url === '/api/v1/employees' && request.method === 'GET').flush({
      content,
      page: 0,
      size: 20,
      totalElements: content.length,
      totalPages: content.length === 0 ? 0 : 1
    });
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminEmployeesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminEmployeesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    httpMock.expectOne((request) => request.url === '/api/v1/employees').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });
    expect(component).toBeTruthy();
  });

  it('shows validation error when no role is selected', () => {
    httpMock.expectOne((request) => request.url === '/api/v1/employees').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });
    component.form.setValue({
      email: 'new@acme.test',
      password: 'supersecretpwd',
      firstName: 'New',
      lastName: 'User',
      tenantAdmin: false,
      employee: false
    });

    component.submit();

    expect(component.formError()).toContain('al menos un rol');
  });

  it('shows conflict message on duplicate email', () => {
    httpMock.expectOne((request) => request.url === '/api/v1/employees').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });
    component.form.setValue({
      email: 'duplicate@acme.test',
      password: 'supersecretpwd',
      firstName: 'Dup',
      lastName: 'Licado',
      tenantAdmin: false,
      employee: true
    });

    component.submit();

    const request = httpMock.expectOne('/api/v1/employees');
    request.flush({ errorCode: 'EMAIL_ALREADY_IN_USE' }, { status: 409, statusText: 'Conflict' });

    expect(component.formError()).toContain('correo');
  });

  it('traduce estados y roles en lugar de mostrar el enum', () => {
    flushList([]);

    expect(component.statusLabel('ACTIVE')).toBe('Activo');
    expect(component.filterLabel('INACTIVE')).toBe('Inactivos');
    expect(component.rolesLabel(['EMPLOYEE', 'TENANT_ADMIN'])).toBe('Empleado · Administrador');
  });

  it('pide confirmación antes de desactivar, sin lanzar la petición', () => {
    flushList([activeEmployee]);

    component.toggleStatus(activeEmployee);

    httpMock.expectNone('/api/v1/employees/e-1/deactivate');
    expect(component.pendingToggle()?.id).toBe('e-1');
  });

  it('desactiva tras confirmar y lo dice con el nombre de la persona', () => {
    flushList([activeEmployee]);

    component.toggleStatus(activeEmployee);
    component.confirmToggle();

    httpMock.expectOne('/api/v1/employees/e-1/deactivate').flush({ ...activeEmployee, status: 'INACTIVE' });
    flushList([{ ...activeEmployee, status: 'INACTIVE' }]);

    expect(component.actionMessage()).toContain('Ana Ruiz desactivado');
    expect(component.pendingToggle()).toBeNull();
  });

  it('cancelar deja al empleado como estaba', () => {
    flushList([activeEmployee]);

    component.toggleStatus(activeEmployee);
    component.cancelToggle();

    httpMock.expectNone('/api/v1/employees/e-1/deactivate');
    expect(component.pendingToggle()).toBeNull();
  });

  it('busca en backend por nombre o correo y reinicia la paginación', () => {
    flushList([activeEmployee, { ...activeEmployee, id: 'e-2', firstName: 'Luis', lastName: 'Soto', email: 'luis@acme.test' }]);
    component.page.set(2);

    component.applySearch('luis');

    const request = httpMock.expectOne('/api/v1/employees?page=0&size=20&query=luis');
    request.flush({
      content: [{ ...activeEmployee, id: 'e-2', firstName: 'Luis', lastName: 'Soto', email: 'luis@acme.test' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1
    });

    expect(component.page()).toBe(0);
    expect(component.result()?.content[0].id).toBe('e-2');
  });
});
