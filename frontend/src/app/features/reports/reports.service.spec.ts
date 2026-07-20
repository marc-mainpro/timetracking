import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ReportsService } from './reports.service';
import { formatIsoDuration } from './duration.util';

describe('ReportsService', () => {
  let service: ReportsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ReportsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('requests employee and tenant summaries with range params', () => {
    service.employeeSummary('emp-1', 'from', 'to').subscribe();
    service.tenantSummary('from', 'to').subscribe();

    const employeeRequest = httpMock.expectOne((req) => req.url === '/api/v1/reports/employees/emp-1/summary');
    expect(employeeRequest.request.params.get('from')).toBe('from');
    expect(employeeRequest.request.params.get('to')).toBe('to');
    employeeRequest.flush([]);

    const tenantRequest = httpMock.expectOne((req) => req.url === '/api/v1/reports/tenant/summary');
    expect(tenantRequest.request.params.get('from')).toBe('from');
    expect(tenantRequest.request.params.get('to')).toBe('to');
    tenantRequest.flush([]);
  });

  it('exports tenant csv as blob and formats ISO durations', () => {
    service.exportTenantCsv('from', 'to').subscribe((blob) => expect(blob).toBeInstanceOf(Blob));

    const request = httpMock.expectOne((req) => req.url === '/api/v1/reports/tenant/export.csv');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['csv']));

    expect(formatIsoDuration('PT8H30M')).toBe('08:30');
    expect(formatIsoDuration('bad-value')).toBe('00:00');
  });
});
