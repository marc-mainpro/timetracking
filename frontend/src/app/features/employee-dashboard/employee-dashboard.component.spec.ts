import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { EmployeeDashboardComponent } from './employee-dashboard.component';

describe('EmployeeDashboardComponent', () => {
  let component: EmployeeDashboardComponent;
  let fixture: ComponentFixture<EmployeeDashboardComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      imports: [EmployeeDashboardComponent]
    })
    .compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    httpMock.expectOne('/api/v1/workdays/current').flush({}, { status: 404, statusText: 'Not Found' });
    expect(component).toBeTruthy();
  });

  it('shows active workday state', () => {
    httpMock.expectOne('/api/v1/workdays/current').flush({
      id: 'workday-1',
      status: 'OPEN',
      startedAt: new Date().toISOString(),
      endedAt: null,
      breaks: [],
      workedDuration: 'PT1H'
    });
    expect(component.hasOpenWorkday()).toBeTrue();
    expect(component.canStartBreak()).toBeTrue();
  });

  it('shows on-break state', () => {
    httpMock.expectOne('/api/v1/workdays/current').flush({
      id: 'workday-2',
      status: 'ON_BREAK',
      startedAt: new Date().toISOString(),
      endedAt: null,
      breaks: [{ id: 'break-1', startedAt: new Date().toISOString(), endedAt: null }],
      workedDuration: 'PT2H'
    });
    expect(component.isOnBreak()).toBeTrue();
  });
});
