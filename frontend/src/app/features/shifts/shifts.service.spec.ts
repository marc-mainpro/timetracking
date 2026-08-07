import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ShiftsService } from './shifts.service';

describe('ShiftsService', () => {
  let service: ShiftsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ShiftsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('calls shift endpoints', () => {
    service.listTemplates().subscribe();
    service.createTemplate({ name: 'General', startTime: '08:00:00', endTime: '16:00:00', plannedBreakMinutes: 30 }).subscribe();
    service.assign({ employeeId: 'emp-1', shiftTemplateId: 'tpl-1', validFrom: '2026-09-01', validTo: '2026-09-30' }).subscribe();
    service.listOwn('2026-09-15').subscribe();

    const templateRequests = httpMock.match('/api/v1/admin/shifts/templates');
    expect(templateRequests.length).toBe(2);
    expect(templateRequests[0].request.method).toBe('GET');
    templateRequests[0].flush([]);
    expect(templateRequests[1].request.method).toBe('POST');
    templateRequests[1].flush({});
    httpMock.expectOne('/api/v1/admin/shifts/assignments').flush({});
    httpMock.expectOne('/api/v1/app/shifts?date=2026-09-15').flush([]);
  });
});
