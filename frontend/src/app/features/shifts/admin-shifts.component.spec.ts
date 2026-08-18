import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AdminShiftsComponent } from './admin-shifts.component';

describe('AdminShiftsComponent', () => {
  let component: AdminShiftsComponent;
  let fixture: ComponentFixture<AdminShiftsComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminShiftsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminShiftsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  function paged(content: unknown[]) {
    return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
  }

  function employee(id: string, roles: string[]) {
    return {
      id,
      email: `${id}@acme.test`,
      firstName: 'Nombre',
      lastName: id,
      status: 'ACTIVE',
      roles,
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z'
    };
  }

  it('loads templates and employees on init', () => {
    httpMock.expectOne('/api/v1/admin/shifts/templates').flush([]);
    const requests = httpMock.match((request) => request.url === '/api/v1/employees');
    expect(requests.length).toBe(2);
    requests.forEach((request) => request.flush(paged([])));
    expect(component.templates().length).toBe(0);
  });

  it('solo ofrece empleados en el desplegable, pero resuelve el nombre de quien ya no lo es', () => {
    httpMock.expectOne('/api/v1/admin/shifts/templates').flush([]);
    const requests = httpMock.match((request) => request.url === '/api/v1/employees');

    // El listado completo alimenta la resolución de nombres; el filtrado por
    // rol, el desplegable.
    const all = requests.find((request) => !request.request.params.has('role'));
    const assignable = requests.find((request) => request.request.params.get('role') === 'EMPLOYEE');
    expect(assignable?.request.params.get('status')).toBe('ACTIVE');

    all?.flush(paged([employee('admin', ['TENANT_ADMIN']), employee('curra', ['EMPLOYEE'])]));
    assignable?.flush(paged([employee('curra', ['EMPLOYEE'])]));
    fixture.detectChanges();

    expect(component.assignableEmployees().map((candidate) => candidate.id)).toEqual(['curra']);
    // Un turno asignado antes a quien hoy no es empleado sigue mostrando su
    // nombre y no su UUID.
    expect(component.employeeName('admin')).toContain('admin');
  });
});
