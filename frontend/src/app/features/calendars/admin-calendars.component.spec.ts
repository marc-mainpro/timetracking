import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AdminCalendarsComponent } from './admin-calendars.component';

describe('AdminCalendarsComponent', () => {
  let component: AdminCalendarsComponent;
  let fixture: ComponentFixture<AdminCalendarsComponent>;
  let httpMock: HttpTestingController;

  const calendarSummary = {
    id: 'c-1',
    name: 'General',
    timezone: 'Europe/Madrid',
    validFrom: '2026-01-01',
    validTo: null,
    status: 'ACTIVE',
    holidayCount: 1,
    specialDayCount: 0
  };

  /** Resuelve las peticiones que el componente lanza en su constructor. */
  function flushInitialLoad(content: unknown[] = []): void {
    httpMock
      .expectOne((request) => request.url === '/api/v1/admin/calendars')
      .flush({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });
    httpMock
      .expectOne((request) => request.url === '/api/v1/admin/calendar-assignments')
      .flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });
    httpMock
      .expectOne((request) => request.url === '/api/v1/employees')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminCalendarsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminCalendarsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads calendars and assignments on creation', () => {
    flushInitialLoad([calendarSummary]);

    expect(component).toBeTruthy();
    expect(component.result()?.content.length).toBe(1);
    expect(component.assignments()?.totalElements).toBe(0);
  });

  it('starts with a monday-to-friday 8h week', () => {
    flushInitialLoad();

    const rules = component.dayRules();
    expect(rules.length).toBe(7);
    expect(rules.find((rule) => rule.dayOfWeek === 'MONDAY')).toEqual({
      dayOfWeek: 'MONDAY',
      working: true,
      expectedMinutes: 480
    });
    expect(rules.find((rule) => rule.dayOfWeek === 'SUNDAY')).toEqual({
      dayOfWeek: 'SUNDAY',
      working: false,
      expectedMinutes: 0
    });
  });

  it('keeps working flag and minutes coherent when toggling a day', () => {
    flushInitialLoad();

    component.toggleWorking('SATURDAY');
    expect(component.dayRules().find((rule) => rule.dayOfWeek === 'SATURDAY')).toEqual({
      dayOfWeek: 'SATURDAY',
      working: true,
      expectedMinutes: 480
    });

    component.toggleWorking('SATURDAY');
    expect(component.dayRules().find((rule) => rule.dayOfWeek === 'SATURDAY')?.expectedMinutes).toBe(0);
  });

  it('adds and removes holidays and special days locally before saving', () => {
    flushInitialLoad();

    component.holidayForm.setValue({ date: '2026-01-06', name: 'Reyes' });
    component.addHoliday();
    expect(component.holidays()).toEqual([{ date: '2026-01-06', name: 'Reyes' }]);

    component.specialDayForm.setValue({ date: '2026-12-24', name: 'Intensiva', expectedMinutes: 300 });
    component.addSpecialDay();
    expect(component.specialDays().length).toBe(1);

    component.removeHoliday('2026-01-06');
    component.removeSpecialDay('2026-12-24');
    expect(component.holidays()).toEqual([]);
    expect(component.specialDays()).toEqual([]);
  });

  it('does not add an incomplete holiday', () => {
    flushInitialLoad();

    component.holidayForm.setValue({ date: '', name: '' });
    component.addHoliday();

    expect(component.holidays()).toEqual([]);
  });

  it('creates a calendar sending an empty validTo as null', () => {
    flushInitialLoad();
    component.form.setValue({
      name: 'Nuevo',
      timezone: 'Europe/Madrid',
      validFrom: '2026-01-01',
      validTo: ''
    });

    component.save();

    const request = httpMock.expectOne('/api/v1/admin/calendars');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.validTo).toBeNull();
    expect(request.request.body.dayRules.length).toBe(7);
    request.flush({ id: 'c-9', name: 'Nuevo' });

    // Tras guardar recarga el listado.
    httpMock
      .expectOne((r) => r.url === '/api/v1/admin/calendars')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(component.message()).toContain('Nuevo');
  });

  it('updates the calendar being edited instead of creating a new one', () => {
    flushInitialLoad([calendarSummary]);

    component.startEdit(calendarSummary as never);
    httpMock.expectOne('/api/v1/admin/calendars/c-1').flush({
      ...calendarSummary,
      dayRules: [{ dayOfWeek: 'MONDAY', working: true, expectedMinutes: 420 }],
      holidays: [{ date: '2026-01-06', name: 'Reyes' }],
      specialDays: [],
      version: 1,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z'
    });

    expect(component.editingId()).toBe('c-1');
    expect(component.form.controls.name.value).toBe('General');
    // Los días sin regla en el backend se muestran como no laborables.
    expect(component.dayRules().find((rule) => rule.dayOfWeek === 'TUESDAY')?.working).toBeFalse();
    expect(component.dayRules().find((rule) => rule.dayOfWeek === 'MONDAY')?.expectedMinutes).toBe(420);

    component.save();
    const request = httpMock.expectOne('/api/v1/admin/calendars/c-1');
    expect(request.request.method).toBe('PUT');
    request.flush({ id: 'c-1', name: 'General' });
    httpMock
      .expectOne((r) => r.url === '/api/v1/admin/calendars')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('does not submit an invalid calendar form', () => {
    flushInitialLoad();
    component.form.setValue({ name: '', timezone: '', validFrom: '', validTo: '' });

    component.save();

    httpMock.expectNone('/api/v1/admin/calendars');
    expect(component.form.controls.name.touched).toBeTrue();
    expect(component.saving()).toBeFalse();
  });

  it('shows a translated error when saving fails', () => {
    flushInitialLoad();
    component.form.setValue({
      name: 'Repetido',
      timezone: 'Europe/Madrid',
      validFrom: '2026-01-01',
      validTo: ''
    });

    component.save();
    httpMock
      .expectOne('/api/v1/admin/calendars')
      .flush({ errorCode: 'RESOURCE_NOT_FOUND' }, { status: 409, statusText: 'Conflict' });

    expect(component.formError()).toBe('No se encontró el recurso solicitado.');
    expect(component.saving()).toBeFalse();
  });

  it('sends a null target for tenant-wide assignments', () => {
    flushInitialLoad();
    component.assignForm.setValue({ calendarId: 'c-1', scope: 'TENANT', targetId: 'se-ignora' });

    component.assign();

    const request = httpMock.expectOne('/api/v1/admin/calendar-assignments');
    expect(request.request.body).toEqual({ calendarId: 'c-1', scope: 'TENANT', targetId: null });
    request.flush({ id: 'a-1' });
    httpMock
      .expectOne((r) => r.url === '/api/v1/admin/calendar-assignments')
      .flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('sends the target for employee assignments', () => {
    flushInitialLoad();
    component.assignForm.setValue({ calendarId: 'c-1', scope: 'EMPLOYEE', targetId: 'e-1' });

    component.assign();

    const request = httpMock.expectOne('/api/v1/admin/calendar-assignments');
    expect(request.request.body).toEqual({ calendarId: 'c-1', scope: 'EMPLOYEE', targetId: 'e-1' });
    request.flush({ id: 'a-2' });
    httpMock
      .expectOne((r) => r.url === '/api/v1/admin/calendar-assignments')
      .flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('resolves the effective calendar and reports when none applies', () => {
    flushInitialLoad();
    component.effectiveForm.setValue({ employeeId: 'e-1', date: '2026-03-02' });

    component.resolveEffective();
    httpMock
      .expectOne('/api/v1/admin/calendar-assignments/effective?employeeId=e-1&date=2026-03-02')
      .flush({
        calendarId: 'c-1',
        calendarName: 'General',
        timezone: 'Europe/Madrid',
        assignmentId: 'a-1',
        scope: 'TENANT',
        targetId: null,
        date: '2026-03-02',
        working: true,
        expectedMinutes: 480,
        source: 'WEEKLY_RULE'
      });
    expect(component.effective()?.calendarName).toBe('General');

    component.resolveEffective();
    httpMock
      .expectOne('/api/v1/admin/calendar-assignments/effective?employeeId=e-1&date=2026-03-02')
      .flush({ errorCode: 'RESOURCE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    expect(component.effective()).toBeNull();
    expect(component.effectiveError()).toBe('No se encontró el recurso solicitado.');
  });

  it('archives a calendar after confirmation and skips it otherwise', () => {
    flushInitialLoad([calendarSummary]);

    // Pedir archivar solo abre la confirmación.
    component.archive(calendarSummary as never);
    httpMock.expectNone('/api/v1/admin/calendars/c-1');
    expect(component.pendingRemoval()?.kind).toBe('calendar');

    component.cancelRemoval();
    httpMock.expectNone('/api/v1/admin/calendars/c-1');
    expect(component.pendingRemoval()).toBeNull();

    component.archive(calendarSummary as never);
    component.confirmRemoval();
    const request = httpMock.expectOne('/api/v1/admin/calendars/c-1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    httpMock
      .expectOne((r) => r.url === '/api/v1/admin/calendars')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(component.message()).toContain('archivado');
  });

  it('changes the status filter and reloads from the first page', () => {
    flushInitialLoad();

    component.applyStatus('ARCHIVED');

    const request = httpMock.expectOne('/api/v1/admin/calendars?page=0&size=20&status=ARCHIVED');
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(component.selectedStatus()).toBe('ARCHIVED');
  });

  it('exposes readable labels and durations', () => {
    flushInitialLoad([calendarSummary]);

    expect(component.filterLabel('')).toBe('Todos');
    expect(component.filterLabel('ACTIVE')).toBe('Activos');
    expect(component.statusLabel('ACTIVE')).toBe('Activo');
    expect(component.statusLabel('ARCHIVED')).toBe('Archivado');
    expect(component.dayLabel('MONDAY')).toBe('Lunes');
    expect(component.dayLabel('OTRO')).toBe('OTRO');
    expect(component.scopeLabel('EMPLOYEE')).toBe('Empleado');
    expect(component.scopeLabel('OTRO')).toBe('OTRO');
    expect(component.sourceLabel('HOLIDAY')).toBe('Festivo');
    expect(component.sourceLabel('OTRO')).toBe('OTRO');
    expect(component.formatMinutes(480)).toBe('8h 00m');
    expect(component.formatMinutes(305)).toBe('5h 05m');
    expect(component.calendarName('c-1')).toBe('General');
    expect(component.calendarName('desconocido')).toBe('desconocido');
  });

  it('does not paginate past the boundaries', () => {
    flushInitialLoad();

    component.previousPage();
    component.nextPage();

    httpMock.expectNone((request) => request.url === '/api/v1/admin/calendars');
    expect(component.page()).toBe(0);
  });
});
