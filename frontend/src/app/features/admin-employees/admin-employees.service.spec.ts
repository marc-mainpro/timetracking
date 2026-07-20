import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AdminEmployeesService } from './admin-employees.service';

describe('AdminEmployeesService', () => {
  let service: AdminEmployeesService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AdminEmployeesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('calls employee management endpoints', () => {
    service.list(0, 10, 'ACTIVE').subscribe();
    service.create({ email: 'a@acme.test', password: 'secret1234', firstName: 'Ana', lastName: 'Doe', roles: ['EMPLOYEE'] }).subscribe();
    service.update('emp-1', { firstName: 'Ana', lastName: 'Smith' }).subscribe();
    service.activate('emp-1').subscribe();
    service.deactivate('emp-1').subscribe();
    service.assignRoles('emp-1', ['TENANT_ADMIN']).subscribe();

    const listRequest = httpMock.expectOne('/api/v1/employees?page=0&size=10&status=ACTIVE');
    listRequest.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    httpMock.expectOne('/api/v1/employees').flush({});
    httpMock.expectOne('/api/v1/employees/emp-1').flush({});
    httpMock.expectOne('/api/v1/employees/emp-1/activate').flush({});
    httpMock.expectOne('/api/v1/employees/emp-1/deactivate').flush({});
    httpMock.expectOne('/api/v1/employees/emp-1/roles').flush({});
    expect().nothing();
  });
});
