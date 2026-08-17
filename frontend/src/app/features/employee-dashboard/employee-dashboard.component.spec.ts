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
    expect(component.currentWorkday()).not.toBeNull();
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

  it('congela el tiempo trabajado mientras la pausa sigue abierta', () => {
    const startedAt = new Date(Date.now() - 3 * 3600 * 1000).toISOString();
    const breakStartedAt = new Date(Date.now() - 3600 * 1000).toISOString();

    httpMock.expectOne('/api/v1/workdays/current').flush({
      id: 'workday-3',
      status: 'ON_BREAK',
      startedAt,
      endedAt: null,
      breaks: [{ id: 'break-1', startedAt: breakStartedAt, endedAt: null }],
      workedDuration: 'PT2H'
    });

    // Tres horas desde la entrada menos la hora de pausa que sigue corriendo:
    // antes la pausa abierta no se descontaba y el contador seguía subiendo.
    // Se compara con holgura: el reloj compartido fija su instante al crearse.
    expect(component.workedMillis()).toBeCloseTo(2 * 3600 * 1000, -4);
    expect(component.breakMillis()).toBeCloseTo(3600 * 1000, -4);
  });

  it('descuenta también las pausas ya cerradas', () => {
    const startedAt = new Date(Date.now() - 4 * 3600 * 1000).toISOString();

    httpMock.expectOne('/api/v1/workdays/current').flush({
      id: 'workday-4',
      status: 'OPEN',
      startedAt,
      endedAt: null,
      breaks: [
        {
          id: 'break-1',
          startedAt: new Date(Date.now() - 3 * 3600 * 1000).toISOString(),
          endedAt: new Date(Date.now() - 2 * 3600 * 1000).toISOString()
        }
      ],
      workedDuration: 'PT3H'
    });

    expect(component.workedMillis()).toBeCloseTo(3 * 3600 * 1000, -4);
    expect(component.closedBreaks()).toBe(1);
    expect(component.breakLabel()).toBe('0 min');
  });
});
