import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PlatformTenantsComponent } from './platform-tenants.component';
import { PlatformTenantSummary } from './platform-tenants.service';

describe('PlatformTenantsComponent', () => {
  let component: PlatformTenantsComponent;
  let fixture: ComponentFixture<PlatformTenantsComponent>;
  let httpMock: HttpTestingController;

  const tenant: PlatformTenantSummary = {
    id: 't-1',
    name: 'Acme',
    status: 'ACTIVE',
    timezone: 'Europe/Madrid',
    createdAt: '2026-01-15T10:00:00Z',
    activatedAt: '2026-01-15T10:00:00Z',
    suspendedAt: null,
    userCount: 3,
    lastAccessAt: '2026-01-20T08:30:00Z'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformTenantsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlatformTenantsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    flushInitial([tenant]);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitial(content: PlatformTenantSummary[]): void {
    httpMock.expectOne((req) => req.url === '/api/v1/platform/tenants').flush({
      content,
      page: 0,
      size: 20,
      totalElements: content.length,
      totalPages: 1
    });
    httpMock.expectOne((req) => req.url === '/api/v1/platform/audit').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });
  }

  it('loads tenants on init', () => {
    expect(component.result()?.content.length).toBe(1);
  });

  it('applies a status filter and reloads', () => {
    component.applyStatus('SUSPENDED');
    const request = httpMock.expectOne(
      (req) => req.url === '/api/v1/platform/tenants' && req.params.get('status') === 'SUSPENDED'
    );
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(component.selectedStatus()).toBe('SUSPENDED');
  });

  it('suspends a tenant with a reason', () => {
    spyOn(window, 'prompt').and.returnValue('Impago');
    component.suspend(tenant);

    const suspend = httpMock.expectOne('/api/v1/platform/tenants/t-1/suspend');
    expect(suspend.request.body).toEqual({ reason: 'Impago' });
    suspend.flush({ ...tenant, status: 'SUSPENDED', updatedAt: tenant.createdAt, archivedAt: null, suspensionReason: 'Impago' });
    flushInitial([{ ...tenant, status: 'SUSPENDED' }]);

    expect(component.message()).toContain('suspendido');
  });

  it('does not suspend when the reason prompt is cancelled', () => {
    spyOn(window, 'prompt').and.returnValue(null);
    component.suspend(tenant);
    httpMock.expectNone('/api/v1/platform/tenants/t-1/suspend');
  });

  it('rejects an empty suspension reason', () => {
    spyOn(window, 'prompt').and.returnValue('   ');
    component.suspend(tenant);
    httpMock.expectNone('/api/v1/platform/tenants/t-1/suspend');
    expect(component.error()).toContain('obligatorio');
  });

  it('activates a tenant after confirmation', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    component.activate({ ...tenant, status: 'PENDING' });

    httpMock
      .expectOne('/api/v1/platform/tenants/t-1/activate')
      .flush({ ...tenant, updatedAt: tenant.createdAt, archivedAt: null, suspensionReason: null });
    flushInitial([tenant]);
    expect(component.message()).toContain('activado');
  });

  it('creates a tenant and reloads the list', () => {
    component.form.setValue({
      tenantName: 'Nueva Org',
      timezone: 'Europe/Madrid',
      adminEmail: 'owner@acme.test',
      adminPassword: 'supersecretpwd',
      firstName: 'Owner',
      lastName: 'Nuevo'
    });
    component.createTenant();

    const create = httpMock.expectOne('/api/v1/platform/tenants');
    expect(create.request.method).toBe('POST');
    create.flush({ tenantId: 't-2', adminUserId: 'u-2' });

    // Tras crear, recarga el listado.
    httpMock
      .expectOne((req) => req.url === '/api/v1/platform/tenants' && req.method === 'GET')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    expect(component.message()).toContain('creado');
  });

  it('does not submit an invalid create form', () => {
    component.form.reset({ tenantName: '', timezone: '', adminEmail: '', adminPassword: '', firstName: '', lastName: '' });
    component.createTenant();
    httpMock.expectNone((req) => req.method === 'POST' && req.url === '/api/v1/platform/tenants');
  });

  it('loads a tenant detail', () => {
    component.viewDetail(tenant);
    httpMock
      .expectOne('/api/v1/platform/tenants/t-1')
      .flush({ ...tenant, updatedAt: tenant.createdAt, archivedAt: null, suspensionReason: null });
    expect(component.selectedTenant()?.id).toBe('t-1');
  });
  it('muestra el número de usuarios y el último acceso del tenant', () => {
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('3 usuarios');
    expect(text).toContain('último acceso');
  });

  it('indica cuando un tenant nunca se ha usado', () => {
    component.load();
    httpMock.expectOne((req) => req.url === '/api/v1/platform/tenants').flush({
      content: [{ ...tenant, userCount: 1, lastAccessAt: null }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('1 usuario');
    expect(text).toContain('sin accesos');
  });

});
