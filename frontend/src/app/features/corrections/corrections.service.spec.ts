import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { CorrectionsService } from './corrections.service';

describe('CorrectionsService', () => {
  let service: CorrectionsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CorrectionsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('calls correction endpoints and serializes optional approval comments', () => {
    const payload = {
      reason: 'Ajuste',
      proposedChanges: {
        startedAt: '2026-01-15T09:00:00Z',
        endedAt: '2026-01-15T18:00:00Z',
        breaks: []
      }
    };

    service.list(0, 20, 'PENDING').subscribe();
    service.get('corr-1').subscribe();
    service.request('workday-1', payload).subscribe();
    service.approve('corr-1').subscribe();
    service.reject('corr-1', 'No procede').subscribe();
    service.getOwnWorkday('workday-1').subscribe();
    service.getAdminWorkday('workday-1').subscribe();

    httpMock.expectOne('/api/v1/corrections?page=0&size=20&status=PENDING').flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    httpMock.expectOne('/api/v1/corrections/corr-1').flush({});
    httpMock.expectOne('/api/v1/workdays/workday-1/corrections').flush({});
    const approveRequest = httpMock.expectOne('/api/v1/corrections/corr-1/approve');
    expect(approveRequest.request.body).toEqual({ resolutionComment: null });
    approveRequest.flush({});
    httpMock.expectOne('/api/v1/corrections/corr-1/reject').flush({});
    httpMock.expectOne('/api/v1/workdays/workday-1').flush({});
    httpMock.expectOne('/api/v1/admin/workdays/workday-1').flush({});
  });
});
