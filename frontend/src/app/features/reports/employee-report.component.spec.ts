import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthService } from '../../core/services/auth.service';
import { EmployeeReportComponent } from './employee-report.component';

const EMPLOYEE_ID = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

describe('EmployeeReportComponent', () => {
  let component: EmployeeReportComponent;
  let fixture: ComponentFixture<EmployeeReportComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeeReportComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { currentUserId: () => EMPLOYEE_ID } }
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads its own daily summary using the id from the token', () => {
    const request = httpMock.expectOne((req) => req.url === `/api/v1/reports/employees/${EMPLOYEE_ID}/summary`);
    request.flush([
      {
        day: '2026-02-10',
        worked: 'PT4H',
        paused: 'PT0S',
        workdayCount: 1,
        adjustedWorkdayCount: 0,
        openWorkdays: 0
      }
    ]);

    expect(component).toBeTruthy();
    expect(component.results()?.length).toBe(1);
  });

  it('renders the daily rows returned by the backend', () => {
    httpMock.expectOne((req) => req.url === `/api/v1/reports/employees/${EMPLOYEE_ID}/summary`).flush([
      {
        day: '2026-02-10',
        worked: 'PT4H',
        paused: 'PT0S',
        workdayCount: 1,
        adjustedWorkdayCount: 0,
        openWorkdays: 0
      }
    ]);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('2026-02-10');
    expect(rows[0].textContent).toContain('04:00');
  });

  it('shows the empty state when there is no data in the range', () => {
    httpMock.expectOne((req) => req.url === `/api/v1/reports/employees/${EMPLOYEE_ID}/summary`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sin datos en el rango seleccionado.');
  });

  it('rejects an inverted date range on the client', () => {
    httpMock.expectOne((req) => req.url === `/api/v1/reports/employees/${EMPLOYEE_ID}/summary`).flush([]);

    component.form.setValue({ from: '2026-02-28', to: '2026-02-01' });
    component.load();

    expect(component.form.invalid).toBe(true);
    expect(component.formError()).toContain('desde');
  });
});
