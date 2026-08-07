import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PlatformRegistrationsComponent } from './platform-registrations.component';
import { TenantRegistration } from './platform-registrations.service';

describe('PlatformRegistrationsComponent', () => {
  let component: PlatformRegistrationsComponent;
  let fixture: ComponentFixture<PlatformRegistrationsComponent>;
  let httpMock: HttpTestingController;

  const registration: TenantRegistration = {
    id: 'r-1',
    companyName: 'Acme',
    ownerFirstName: 'Ana',
    ownerLastName: 'Ruiz',
    email: 'ana@acme.test',
    timezone: 'Europe/Madrid',
    status: 'PENDING_REVIEW',
    source: 'PUBLIC_WEB',
    decisionReason: null,
    createdTenantId: null,
    createdAt: '2026-08-01T10:00:00Z',
    verifiedAt: '2026-08-01T10:05:00Z',
    decidedAt: null
  };

  function flushList(content: TenantRegistration[]): void {
    httpMock.expectOne((req) => req.url === '/api/v1/platform/registrations' && req.method === 'GET').flush({
      content,
      page: 0,
      size: 20,
      totalElements: content.length,
      totalPages: content.length === 0 ? 0 : 1
    });
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformRegistrationsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlatformRegistrationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    flushList([registration]);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('opens on the pending-review inbox', () => {
    expect(component.selectedStatus()).toBe('PENDING_REVIEW');
    expect(component.result()?.content.length).toBe(1);
  });

  it('applies a status filter and reloads', () => {
    component.applyStatus('REJECTED');

    const request = httpMock.expectOne(
      (req) => req.url === '/api/v1/platform/registrations' && req.params.get('status') === 'REJECTED'
    );
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(component.selectedStatus()).toBe('REJECTED');
  });

  it('drops the status param when the "all" filter is selected', () => {
    component.applyStatus('');

    const request = httpMock.expectOne((req) => req.url === '/api/v1/platform/registrations');
    expect(request.request.params.has('status')).toBeFalse();
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('approves after confirmation and reports that the tenant is still pending', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    component.approve(registration);

    httpMock
      .expectOne('/api/v1/platform/registrations/r-1/approve')
      .flush({ ...registration, status: 'CONSUMED', createdTenantId: 't-9' });
    flushList([]);

    expect(component.message()).toContain('PENDING');
    expect(component.message()).toContain('t-9');
  });

  it('does not approve when the confirmation is cancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    component.approve(registration);

    httpMock.expectNone('/api/v1/platform/registrations/r-1/approve');
  });

  it('rejects with a reason', () => {
    spyOn(window, 'prompt').and.returnValue('Dominio desechable');
    component.reject(registration);

    const request = httpMock.expectOne('/api/v1/platform/registrations/r-1/reject');
    expect(request.request.body).toEqual({ reason: 'Dominio desechable' });
    request.flush({ ...registration, status: 'REJECTED', decisionReason: 'Dominio desechable' });
    flushList([]);

    expect(component.message()).toContain('rechazada');
  });

  it('refuses to reject without a reason', () => {
    spyOn(window, 'prompt').and.returnValue('   ');
    component.reject(registration);

    httpMock.expectNone('/api/v1/platform/registrations/r-1/reject');
    expect(component.error()).toContain('obligatorio');
  });

  it('does not reject when the prompt is cancelled', () => {
    spyOn(window, 'prompt').and.returnValue(null);
    component.reject(registration);

    httpMock.expectNone('/api/v1/platform/registrations/r-1/reject');
  });

  it('translates a backend error', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    component.approve(registration);

    httpMock
      .expectOne('/api/v1/platform/registrations/r-1/approve')
      .flush({ errorCode: 'EMAIL_ALREADY_IN_USE' }, { status: 409, statusText: 'Conflict' });

    expect(component.error()).toContain('ya está en uso');
  });

  it('does not page past the last page', () => {
    component.nextPage();
    expect(component.page()).toBe(0);

    component.previousPage();
    expect(component.page()).toBe(0);
  });

  it('labels the empty filter as "Todas"', () => {
    expect(component.statusLabel('')).toBe('Todas');
    expect(component.statusLabel('EXPIRED')).toBe('EXPIRED');
  });
});
