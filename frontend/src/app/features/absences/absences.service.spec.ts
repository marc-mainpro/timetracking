import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AbsencesService } from './absences.service';

describe('AbsencesService', () => {
  let service: AbsencesService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AbsencesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('calls all absence endpoints', () => {
    service.listTypes().subscribe();
    service.request({ absenceTypeId: 'type-1', startDate: '2026-08-10', endDate: '2026-08-12', reason: 'Vacaciones' }).subscribe();
    service.listOwn('2026-01-01', '2026-12-31').subscribe();
    service.cancel('abs-1').subscribe();
    service.listAdmin('2026-01-01', '2026-12-31').subscribe();
    service.approve('abs-1').subscribe();
    service.reject('abs-1', 'No procede').subscribe();

    httpMock.expectOne('/api/v1/app/absence-types').flush([]);
    httpMock.expectOne('/api/v1/app/absences').flush({});
    httpMock.expectOne('/api/v1/app/absences?from=2026-01-01&to=2026-12-31').flush([]);
    httpMock.expectOne('/api/v1/app/absences/abs-1/cancel').flush({});
    httpMock.expectOne('/api/v1/admin/absences?from=2026-01-01&to=2026-12-31').flush([]);
    const approveRequest = httpMock.expectOne('/api/v1/admin/absences/abs-1/approve');
    expect(approveRequest.request.body).toEqual({ resolutionComment: null });
    approveRequest.flush({});
    httpMock.expectOne('/api/v1/admin/absences/abs-1/reject').flush({});
  });
});
