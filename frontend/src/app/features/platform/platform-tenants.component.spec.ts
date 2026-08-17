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

  it('pide confirmación antes de suspender, sin lanzar la petición', () => {
    component.suspend(tenant);

    httpMock.expectNone('/api/v1/platform/tenants/t-1/suspend');
    expect(component.pendingAction()?.kind).toBe('suspend');
    expect(component.pendingAction()?.reason).toBe('required');
  });

  it('suspende con el motivo escrito en el diálogo', () => {
    component.suspend(tenant);
    component.reasonControl.setValue('Impago');
    component.confirmAction();

    const suspend = httpMock.expectOne('/api/v1/platform/tenants/t-1/suspend');
    expect(suspend.request.body).toEqual({ reason: 'Impago' });
    suspend.flush({ ...tenant, status: 'SUSPENDED', updatedAt: tenant.createdAt, archivedAt: null, suspensionReason: 'Impago' });
    flushInitial([{ ...tenant, status: 'SUSPENDED' }]);

    expect(component.message()).toContain('suspendida');
    expect(component.pendingAction()).toBeNull();
  });

  it('no suspende sin motivo', () => {
    component.suspend(tenant);
    component.reasonControl.setValue('   ');
    component.confirmAction();

    httpMock.expectNone('/api/v1/platform/tenants/t-1/suspend');
    expect(component.reasonControl.invalid).toBeTrue();
  });

  it('cancelar descarta la acción pendiente', () => {
    component.suspend(tenant);
    component.cancelAction();

    httpMock.expectNone('/api/v1/platform/tenants/t-1/suspend');
    expect(component.pendingAction()).toBeNull();
  });

  it('archiva con el motivo vacío, que es opcional', () => {
    component.archive(tenant);
    expect(component.pendingAction()?.reason).toBe('optional');
    component.confirmAction();

    const archive = httpMock.expectOne('/api/v1/platform/tenants/t-1/archive');
    expect(archive.request.body).toEqual({ reason: undefined });
    archive.flush({ ...tenant, status: 'ARCHIVED', updatedAt: tenant.createdAt, archivedAt: tenant.createdAt, suspensionReason: null });
    flushInitial([{ ...tenant, status: 'ARCHIVED' }]);

    expect(component.message()).toContain('archivada');
  });

  it('activa tras confirmar', () => {
    component.activate({ ...tenant, status: 'PENDING' });
    component.confirmAction();

    httpMock
      .expectOne('/api/v1/platform/tenants/t-1/activate')
      .flush({ ...tenant, updatedAt: tenant.createdAt, archivedAt: null, suspensionReason: null });
    flushInitial([tenant]);
    expect(component.message()).toContain('activada');
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

    expect(component.message()).toContain('creada');
  });

  it('does not submit an invalid create form', () => {
    component.form.reset({ tenantName: '', timezone: '', adminEmail: '', adminPassword: '', firstName: '', lastName: '' });
    component.createTenant();
    httpMock.expectNone((req) => req.method === 'POST' && req.url === '/api/v1/platform/tenants');
    expect(component.form.controls.tenantName.touched).toBeTrue();
  });

  it('loads a tenant detail', () => {
    component.viewDetail(tenant);
    httpMock
      .expectOne('/api/v1/platform/tenants/t-1')
      .flush({ ...tenant, updatedAt: tenant.createdAt, archivedAt: null, suspensionReason: null });
    expect(component.selectedTenant()?.id).toBe('t-1');
  });
  it('muestra el número de usuarios y el último acceso del tenant', () => {
    component.load();
    httpMock.expectOne((req) => req.url === '/api/v1/platform/tenants').flush({
      content: [{ ...tenant, lastAccessAt: new Date(Date.now() - 3600 * 1000).toISOString() }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('3 usuarios');
    expect(text).toContain('Último acceso');
  });

  it('avisa cuando una organización activa lleva meses sin usarse', () => {
    fixture.detectChanges();

    // El tenant del fixture accedió por última vez hace más de un mes.
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Sin accesos desde');
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
    expect(text).toContain('Nunca ha accedido');
  });

  it('traduce los estados en lugar de mostrar el enum', () => {
    expect(component.statusLabel('PENDING')).toBe('Pendiente');
    expect(component.statusLabel('ARCHIVED')).toBe('Archivada');
    expect(component.filterLabel('')).toBe('Todas');
    expect(component.filterLabel('SUSPENDED')).toBe('Suspendidas');
  });

  it('marca en silencio una organización activa sin accesos recientes', () => {
    const old = new Date(Date.now() - 45 * 24 * 3600 * 1000).toISOString();
    expect(component.isSilent({ ...tenant, lastAccessAt: old })).toBeTrue();
    expect(component.isSilent({ ...tenant, lastAccessAt: new Date().toISOString() })).toBeFalse();
    // Una suspendida no está «en silencio»: su inactividad ya está explicada.
    expect(component.isSilent({ ...tenant, status: 'SUSPENDED', lastAccessAt: old })).toBeFalse();
  });

});
