import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { CorrectionsComponent } from './corrections.component';

describe('CorrectionsComponent', () => {
  let component: CorrectionsComponent;
  let fixture: ComponentFixture<CorrectionsComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CorrectionsComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CorrectionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    httpMock.expectOne((request) => request.url === '/api/v1/corrections').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });
    expect(component).toBeTruthy();
  });

  it('validates proposed time ranges before submit', () => {
    httpMock.expectOne((request) => request.url === '/api/v1/corrections').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });

    component.selectedWorkday.set({
      id: 'workday-1',
      status: 'CLOSED',
      startedAt: '2026-01-15T09:00:00Z',
      endedAt: '2026-01-15T18:00:00Z',
      breaks: [],
      workedDuration: 'PT8H'
    });
    component.form.setValue({
      reason: 'Necesito corregir la salida',
      startedAt: '2026-01-15T18:00',
      endedAt: '2026-01-15T09:00',
      breaks: []
    });

    component.submit();

    expect(component.form.errors?.['invalidRange']).toBeTrue();
  });

  it('reloads requests and shows conflict message on duplicate pending request', () => {
    httpMock.expectOne((request) => request.url === '/api/v1/corrections').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });

    component.selectedWorkday.set({
      id: 'workday-1',
      status: 'CLOSED',
      startedAt: '2026-01-15T09:00:00Z',
      endedAt: '2026-01-15T18:00:00Z',
      breaks: [],
      workedDuration: 'PT8H'
    });
    component.form.setValue({
      reason: 'Ajuste',
      startedAt: '2026-01-15T09:00',
      endedAt: '2026-01-15T18:00',
      breaks: []
    });

    component.submit();

    httpMock.expectOne('/api/v1/workdays/workday-1/corrections').flush(
      { errorCode: 'CORRECTION_ALREADY_PENDING' },
      { status: 409, statusText: 'Conflict' }
    );
    httpMock.expectOne((request) => request.url === '/api/v1/corrections').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });
    httpMock.expectOne('/api/v1/workdays/workday-1').flush({
      id: 'workday-1',
      status: 'CLOSED',
      startedAt: '2026-01-15T09:00:00Z',
      endedAt: '2026-01-15T18:00:00Z',
      breaks: [],
      workedDuration: 'PT8H'
    });

    expect(component.formError()).toContain('pendiente');
  });
});
