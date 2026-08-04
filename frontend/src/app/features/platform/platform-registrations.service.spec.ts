import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PlatformRegistrationsService } from './platform-registrations.service';

describe('PlatformRegistrationsService', () => {
  let service: PlatformRegistrationsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PlatformRegistrationsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists registrations with an optional status filter', () => {
    service.list(1, 10, 'PENDING_REVIEW').subscribe();

    const request = httpMock.expectOne(
      '/api/v1/platform/registrations?page=1&size=10&status=PENDING_REVIEW'
    );
    expect(request.request.method).toBe('GET');
    request.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('omits the status query param when no filter is set', () => {
    service.list(0, 20).subscribe();

    const request = httpMock.expectOne('/api/v1/platform/registrations?page=0&size=20');
    expect(request.request.params.has('status')).toBeFalse();
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('approves and rejects through the platform endpoints', () => {
    service.approve('r-1').subscribe();
    service.reject('r-1', 'Dominio desechable').subscribe();

    const approve = httpMock.expectOne('/api/v1/platform/registrations/r-1/approve');
    expect(approve.request.method).toBe('POST');
    approve.flush({});

    const reject = httpMock.expectOne('/api/v1/platform/registrations/r-1/reject');
    expect(reject.request.body).toEqual({ reason: 'Dominio desechable' });
    reject.flush({});
  });
});
