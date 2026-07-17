import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AdminEmployeesComponent } from './admin-employees.component';

describe('AdminEmployeesComponent', () => {
  let component: AdminEmployeesComponent;
  let fixture: ComponentFixture<AdminEmployeesComponent>;
  let httpMock: HttpTestingController;

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
});
