import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { CalendarsService, SaveCalendarPayload } from './calendars.service';

describe('CalendarsService', () => {
  let service: CalendarsService;
  let httpMock: HttpTestingController;

  const payload: SaveCalendarPayload = {
    name: 'General',
    timezone: 'Europe/Madrid',
    validFrom: '2026-01-01',
    validTo: null,
    dayRules: [{ dayOfWeek: 'MONDAY', working: true, expectedMinutes: 480 }],
    holidays: [{ date: '2026-01-06', name: 'Reyes' }],
    specialDays: [{ date: '2026-12-24', name: 'Intensiva', expectedMinutes: 300 }]
  };

  const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CalendarsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists calendars with pagination and optional status filter', () => {
    service.list(1, 10, 'ARCHIVED').subscribe();
    const request = httpMock.expectOne('/api/v1/admin/calendars?page=1&size=10&status=ARCHIVED');
    expect(request.request.method).toBe('GET');
    request.flush(emptyPage);
  });

  it('omits the status query param when no filter is set', () => {
    service.list(0, 20).subscribe();
    const request = httpMock.expectOne('/api/v1/admin/calendars?page=0&size=20');
    expect(request.request.params.has('status')).toBeFalse();
    request.flush(emptyPage);
  });

  it('creates, reads, updates and archives a calendar', () => {
    service.create(payload).subscribe();
    const created = httpMock.expectOne('/api/v1/admin/calendars');
    expect(created.request.method).toBe('POST');
    expect(created.request.body.name).toBe('General');
    expect(created.request.body.validTo).toBeNull();
    created.flush({ id: 'c-1' });

    service.get('c-1').subscribe();
    const detail = httpMock.expectOne('/api/v1/admin/calendars/c-1');
    expect(detail.request.method).toBe('GET');
    detail.flush({ id: 'c-1' });

    service.update('c-1', payload).subscribe();
    const updated = httpMock.expectOne('/api/v1/admin/calendars/c-1');
    expect(updated.request.method).toBe('PUT');
    expect(updated.request.body.holidays.length).toBe(1);
    updated.flush({ id: 'c-1' });

    service.archive('c-1').subscribe();
    const archived = httpMock.expectOne('/api/v1/admin/calendars/c-1');
    expect(archived.request.method).toBe('DELETE');
    archived.flush(null);
  });

  it('lists assignments and filters them by calendar', () => {
    service.listAssignments(0, 50).subscribe();
    const all = httpMock.expectOne('/api/v1/admin/calendar-assignments?page=0&size=50');
    expect(all.request.params.has('calendarId')).toBeFalse();
    all.flush(emptyPage);

    service.listAssignments(0, 50, 'c-1').subscribe();
    const filtered = httpMock.expectOne(
      '/api/v1/admin/calendar-assignments?page=0&size=50&calendarId=c-1'
    );
    expect(filtered.request.method).toBe('GET');
    filtered.flush(emptyPage);
  });

  it('assigns and removes a calendar assignment', () => {
    service.assign({ calendarId: 'c-1', scope: 'EMPLOYEE', targetId: 'e-1' }).subscribe();
    const assigned = httpMock.expectOne('/api/v1/admin/calendar-assignments');
    expect(assigned.request.method).toBe('POST');
    expect(assigned.request.body).toEqual({ calendarId: 'c-1', scope: 'EMPLOYEE', targetId: 'e-1' });
    assigned.flush({ id: 'a-1' });

    service.removeAssignment('a-1').subscribe();
    const removed = httpMock.expectOne('/api/v1/admin/calendar-assignments/a-1');
    expect(removed.request.method).toBe('DELETE');
    removed.flush(null);
  });

  it('resolves the effective calendar with and without a team', () => {
    service.resolveEffective('e-1', '2026-03-02', 't-1').subscribe();
    const withTeam = httpMock.expectOne(
      '/api/v1/admin/calendar-assignments/effective?employeeId=e-1&date=2026-03-02&teamId=t-1'
    );
    expect(withTeam.request.method).toBe('GET');
    withTeam.flush({});

    // Sin equipo el parámetro se omite: el backend descarta entonces las
    // asignaciones de ámbito TEAM.
    service.resolveEffective('e-1', '2026-03-02').subscribe();
    const withoutTeam = httpMock.expectOne(
      '/api/v1/admin/calendar-assignments/effective?employeeId=e-1&date=2026-03-02'
    );
    expect(withoutTeam.request.params.has('teamId')).toBeFalse();
    withoutTeam.flush({});
  });
});
