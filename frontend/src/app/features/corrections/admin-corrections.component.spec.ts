import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AdminCorrectionsComponent } from './admin-corrections.component';
import { Correction } from './corrections.service';

describe('AdminCorrectionsComponent', () => {
  let component: AdminCorrectionsComponent;
  let fixture: ComponentFixture<AdminCorrectionsComponent>;
  let httpMock: HttpTestingController;

  const pendingCorrection: Correction = {
    id: 'cor-1',
    workdayId: 'workday-1',
    requestedBy: 'user-1',
    reason: 'Ajuste',
    proposedChanges: { startedAt: '2026-01-15T09:00:00Z', endedAt: '2026-01-15T18:00:00Z', breaks: [] },
    status: 'PENDING',
    resolvedBy: null,
    resolvedAt: null,
    resolutionComment: null,
    createdAt: '2026-01-15T19:00:00Z'
  };

  /** Las dos peticiones que dispara el constructor. */
  function flushInitial(): void {
    httpMock
      .expectOne((request) => request.url === '/api/v1/corrections')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    httpMock
      .expectOne((request) => request.url === '/api/v1/employees')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminCorrectionsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminCorrectionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    httpMock.expectOne((request) => request.url === '/api/v1/corrections').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });
    httpMock
      .expectOne((request) => request.url === '/api/v1/employees')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    expect(component).toBeTruthy();
  });

  it('requires a comment before rejecting', () => {
    httpMock.expectOne((request) => request.url === '/api/v1/corrections').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });
    httpMock
      .expectOne((request) => request.url === '/api/v1/employees')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    component.selectedCorrection.set({
      id: 'cor-1',
      workdayId: 'workday-1',
      requestedBy: 'user-1',
      reason: 'Ajuste',
      proposedChanges: { startedAt: '2026-01-15T09:00:00Z', endedAt: '2026-01-15T18:00:00Z', breaks: [] },
      status: 'PENDING',
      resolvedBy: null,
      resolvedAt: null,
      resolutionComment: null,
      createdAt: '2026-01-15T19:00:00Z'
    });

    component.reject();

    // Sin comentario no se abre siquiera la confirmación.
    expect(component.commentControl.invalid).toBeTrue();
    expect(component.pendingDecision()).toBeNull();
    httpMock.expectNone('/api/v1/corrections/cor-1/reject');
  });

  it('reloads state when approve hits a concurrent conflict', () => {
    httpMock.expectOne((request) => request.url === '/api/v1/corrections').flush({
      content: [
        {
          id: 'cor-1',
          workdayId: 'workday-1',
          requestedBy: 'user-1',
          reason: 'Ajuste',
          proposedChanges: { startedAt: '2026-01-15T09:00:00Z', endedAt: '2026-01-15T18:00:00Z', breaks: [] },
          status: 'PENDING',
          resolvedBy: null,
          resolvedAt: null,
          resolutionComment: null,
          createdAt: '2026-01-15T19:00:00Z'
        }
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1
    });
    httpMock
      .expectOne((request) => request.url === '/api/v1/employees')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    const correction = component.result()?.content[0];
    expect(correction).toBeTruthy();

    component.selectCorrection(correction!);
    httpMock.expectOne('/api/v1/admin/workdays/workday-1').flush({
      id: 'workday-1',
      status: 'CLOSED',
      startedAt: '2026-01-15T09:00:00Z',
      endedAt: '2026-01-15T18:00:00Z',
      breaks: [],
      workedDuration: 'PT8H'
    });

    component.approve();
    component.confirmDecision();

    httpMock.expectOne('/api/v1/corrections/cor-1/approve').flush(
      { errorCode: 'CONCURRENT_MODIFICATION' },
      { status: 409, statusText: 'Conflict' }
    );
    httpMock.expectOne((request) => request.url === '/api/v1/corrections').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });
    httpMock.expectOne('/api/v1/corrections/cor-1').flush({
      id: 'cor-1',
      workdayId: 'workday-1',
      requestedBy: 'user-1',
      reason: 'Ajuste',
      proposedChanges: { startedAt: '2026-01-15T09:00:00Z', endedAt: '2026-01-15T18:00:00Z', breaks: [] },
      status: 'APPROVED',
      resolvedBy: 'admin-1',
      resolvedAt: '2026-01-15T20:00:00Z',
      resolutionComment: 'Resuelta',
      createdAt: '2026-01-15T19:00:00Z'
    });
    httpMock.expectOne('/api/v1/admin/workdays/workday-1').flush({
      id: 'workday-1',
      status: 'ADJUSTED',
      startedAt: '2026-01-15T09:00:00Z',
      endedAt: '2026-01-15T18:00:00Z',
      breaks: [],
      workedDuration: 'PT8H'
    });

    expect(component.decisionError()).toContain('Otra persona modificó');
  });

  it('confirma antes de aprobar, sin lanzar la petición', () => {
    flushInitial();

    component.selectedCorrection.set(pendingCorrection);
    component.approve();

    httpMock.expectNone('/api/v1/corrections/cor-1/approve');
    expect(component.pendingDecision()).toBe('approve');
  });

  it('traduce los estados en lugar de mostrar el enum', () => {
    flushInitial();

    expect(component.statusLabel('PENDING')).toBe('Pendiente');
    expect(component.filterLabel('REJECTED')).toBe('Rechazadas');
  });
});
