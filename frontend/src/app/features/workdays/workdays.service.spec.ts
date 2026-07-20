import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { WorkdaysService } from './workdays.service';

describe('WorkdaysService', () => {
  let service: WorkdaysService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(WorkdaysService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('calls workday transition endpoints', () => {
    service.getCurrent().subscribe();
    service.start().subscribe();
    service.startBreak().subscribe();
    service.endBreak().subscribe();
    service.endWorkday().subscribe();

    httpMock.expectOne('/api/v1/workdays/current').flush({});
    httpMock.expectOne('/api/v1/workdays/start').flush({});
    httpMock.expectOne('/api/v1/workdays/current/breaks/start').flush({});
    httpMock.expectOne('/api/v1/workdays/current/breaks/end').flush({});
    httpMock.expectOne('/api/v1/workdays/current/end').flush({});
    expect().nothing();
  });

  it('builds list query params', () => {
    service.list(1, 20, '2026-01-01T00:00:00Z', '2026-01-31T23:59:59Z').subscribe();

    const request = httpMock.expectOne((req) => req.url === '/api/v1/workdays');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.get('from')).toBe('2026-01-01T00:00:00Z');
    expect(request.request.params.get('to')).toBe('2026-01-31T23:59:59Z');
    request.flush({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
  });
});
