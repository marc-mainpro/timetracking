import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AdminAbsencesComponent } from './admin-absences.component';
import { Absence } from './absences.service';

describe('AdminAbsencesComponent', () => {
  let component: AdminAbsencesComponent;
  let fixture: ComponentFixture<AdminAbsencesComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminAbsencesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminAbsencesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  const pending: Absence = {
    id: 'abs-1',
    employeeId: 'user-1',
    absenceTypeId: 'type-1',
    startDate: '2026-08-10',
    endDate: '2026-08-14',
    status: 'PENDING',
    reason: 'Viaje',
    resolutionComment: null,
    resolvedBy: null,
    resolvedAt: null,
    createdAt: '2026-08-01T09:00:00Z'
  };

  /** Las tres peticiones que dispara el constructor. */
  function flushInitial(absences: Absence[]): void {
    httpMock
      .expectOne('/api/v1/app/absence-types')
      .flush([{ id: 'type-1', code: 'VAC', name: 'Vacaciones', requiresApproval: true, allowsAttachment: false, active: true }]);
    httpMock.expectOne((request) => request.url === '/api/v1/admin/absences').flush(absences);
    httpMock
      .expectOne((request) => request.url === '/api/v1/employees')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  }

  it('loads types and admin absences on init', () => {
    flushInitial([pending]);

    expect(component.absences().length).toBe(1);
    expect(component.typeName('type-1')).toBe('Vacaciones');
  });

  it('traduce los estados en lugar de mostrar el enum', () => {
    flushInitial([]);

    expect(component.statusLabel('PENDING')).toBe('Pendiente');
    expect(component.filterLabel('APPROVED')).toBe('Aprobadas');
  });

  it('cuenta los días de la solicitud con los extremos incluidos', () => {
    flushInitial([]);

    expect(component.dayCountLabel(pending)).toBe('5 días');
    expect(component.dayCountLabel({ ...pending, endDate: pending.startDate })).toBe('1 día');
  });

  it('no rechaza sin comentario ni abre la confirmación', () => {
    flushInitial([pending]);
    component.selectAbsence(pending);

    component.reject();

    expect(component.commentControl.invalid).toBeTrue();
    expect(component.pendingDecision()).toBeNull();
    httpMock.expectNone('/api/v1/admin/absences/abs-1/reject');
  });

  it('aprueba tras confirmar', () => {
    flushInitial([pending]);
    component.selectAbsence(pending);

    component.approve();
    expect(component.pendingDecision()).toBe('approve');
    component.confirmDecision();

    httpMock
      .expectOne('/api/v1/admin/absences/abs-1/approve')
      .flush({ ...pending, status: 'APPROVED' });
    httpMock.expectOne((request) => request.url === '/api/v1/admin/absences').flush([]);

    expect(component.actionMessage()).toContain('aprobada');
  });
});
