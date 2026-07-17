import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AdminCorrectionsComponent } from './admin-corrections.component';

describe('AdminCorrectionsComponent', () => {
  let component: AdminCorrectionsComponent;
  let fixture: ComponentFixture<AdminCorrectionsComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    spyOn(window, 'confirm').and.returnValue(true);

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

    expect(component.rejectForm.controls.resolutionComment.invalid).toBeTrue();
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
});
