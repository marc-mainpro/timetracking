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

  it('pide confirmación antes de aprobar, sin lanzar la petición', () => {
    component.approve(registration);

    httpMock.expectNone('/api/v1/platform/registrations/r-1/approve');
    expect(component.pendingDecision()?.kind).toBe('approve');
  });

  it('aprueba y remite a Organizaciones para activar el alta', () => {
    component.approve(registration);
    component.confirmDecision();

    httpMock
      .expectOne('/api/v1/platform/registrations/r-1/approve')
      .flush({ ...registration, status: 'CONSUMED', createdTenantId: 't-9' });
    flushList([]);

    expect(component.message()).toContain('Acme aprobada');
    expect(component.message()).toContain('Organizaciones');
    expect(component.pendingDecision()).toBeNull();
  });

  it('cancelar descarta la decisión pendiente', () => {
    component.approve(registration);
    component.cancelDecision();

    httpMock.expectNone('/api/v1/platform/registrations/r-1/approve');
    expect(component.pendingDecision()).toBeNull();
  });

  it('rechaza con el motivo escrito en el diálogo', () => {
    component.reject(registration);
    component.reasonControl.setValue('Dominio desechable');
    component.confirmDecision();

    const request = httpMock.expectOne('/api/v1/platform/registrations/r-1/reject');
    expect(request.request.body).toEqual({ reason: 'Dominio desechable' });
    request.flush({ ...registration, status: 'REJECTED', decisionReason: 'Dominio desechable' });
    flushList([]);

    expect(component.message()).toContain('rechazada');
  });

  it('no rechaza sin motivo', () => {
    component.reject(registration);
    component.reasonControl.setValue('   ');
    component.confirmDecision();

    httpMock.expectNone('/api/v1/platform/registrations/r-1/reject');
    expect(component.reasonControl.invalid).toBeTrue();
  });

  it('translates a backend error', () => {
    component.approve(registration);
    component.confirmDecision();

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

  it('traduce los estados en lugar de mostrar el enum', () => {
    expect(component.filterLabel('')).toBe('Todas');
    expect(component.filterLabel('PENDING_EMAIL_VERIFICATION')).toBe('Sin verificar');
    expect(component.statusLabel('EXPIRED')).toBe('Caducada');
    expect(component.statusLabel('CONSUMED')).toBe('Alta creada');
  });

  it('marca la solicitud que lleva días esperando revisión', () => {
    const old = new Date(Date.now() - 5 * 24 * 3600 * 1000).toISOString();
    const fresh = new Date(Date.now() - 3600 * 1000).toISOString();

    expect(component.isStale({ ...registration, createdAt: old })).toBeTrue();
    expect(component.isStale({ ...registration, createdAt: fresh })).toBeFalse();
    // Lo ya decidido no hace esperar a nadie, por antiguo que sea.
    expect(component.isStale({ ...registration, status: 'REJECTED', createdAt: old })).toBeFalse();
  });
});
