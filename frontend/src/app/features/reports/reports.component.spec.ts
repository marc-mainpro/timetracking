import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ReportsComponent } from './reports.component';

describe('ReportsComponent', () => {
  let component: ReportsComponent;
  let fixture: ComponentFixture<ReportsComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReportsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReportsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create and load the tenant summary on init', () => {
    const request = httpMock.expectOne((req) => req.url === '/api/v1/reports/tenant/summary');
    expect(request.request.method).toBe('GET');
    request.flush([
      {
        employeeId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        worked: 'PT8H30M',
        paused: 'PT30M',
        workdayCount: 1,
        adjustedWorkdayCount: 0,
        openWorkdays: 0
      }
    ]);

    expect(component).toBeTruthy();
    expect(component.results()?.length).toBe(1);
  });

  it('renders the summary table with the data returned by the backend', () => {
    const request = httpMock.expectOne((req) => req.url === '/api/v1/reports/tenant/summary');
    request.flush([
      {
        employeeId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        worked: 'PT8H30M',
        paused: 'PT30M',
        workdayCount: 1,
        adjustedWorkdayCount: 0,
        openWorkdays: 0
      }
    ]);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
    expect(rows[0].textContent).toContain('08:30');
    expect(rows[0].textContent).toContain('00:30');
  });

  it('shows the empty state when the backend returns no data for the range', () => {
    const request = httpMock.expectOne((req) => req.url === '/api/v1/reports/tenant/summary');
    request.flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sin datos en el rango seleccionado.');
  });

  it('rejects an inverted date range on the client before calling the backend', () => {
    httpMock.expectOne((req) => req.url === '/api/v1/reports/tenant/summary').flush([]);

    component.form.setValue({ from: '2026-02-28', to: '2026-02-01' });
    component.load();

    expect(component.form.invalid).toBe(true);
    expect(component.formError()).toContain('desde');
    httpMock.expectNone((req) => req.url === '/api/v1/reports/tenant/summary' && req.params.get('from') === '2026-02-28');
  });

  it('shows the backend validation error for an invalid range', () => {
    httpMock.expectOne((req) => req.url === '/api/v1/reports/tenant/summary').flush([]);

    component.form.setValue({ from: '2026-02-01', to: '2026-02-28' });
    component.load();

    const request = httpMock.expectOne((req) => req.url === '/api/v1/reports/tenant/summary');
    request.flush({ errorCode: 'INVALID_ARGUMENT', detail: 'Rango invalido' }, { status: 400, statusText: 'Bad Request' });

    expect(component.formError()).toContain('Rango invalido');
  });

  it('requests the CSV export as a blob with the selected range and triggers a download', () => {
    httpMock.expectOne((req) => req.url === '/api/v1/reports/tenant/summary').flush([]);

    component.form.setValue({ from: '2026-02-01', to: '2026-02-28' });
    const clickSpy = spyOn(HTMLAnchorElement.prototype, 'click');
    const createObjectUrlSpy = spyOn(URL, 'createObjectURL').and.returnValue('blob:mock-url');
    const revokeObjectUrlSpy = spyOn(URL, 'revokeObjectURL');

    component.exportCsv();

    const request = httpMock.expectOne(
      (req) =>
        req.url === '/api/v1/reports/tenant/export.csv' &&
        req.params.get('from') === '2026-02-01T00:00:00Z' &&
        req.params.get('to') === '2026-02-28T23:59:59Z'
    );
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['employeeId,workedSeconds'], { type: 'text/csv' }));

    expect(createObjectUrlSpy).toHaveBeenCalled();
    expect(clickSpy).toHaveBeenCalled();
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:mock-url');
    expect(component.exporting()).toBe(false);
  });
});
