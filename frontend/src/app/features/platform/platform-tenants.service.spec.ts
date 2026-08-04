import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PlatformTenantsService } from './platform-tenants.service';

describe('PlatformTenantsService', () => {
  let service: PlatformTenantsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PlatformTenantsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('calls the platform tenant lifecycle endpoints', () => {
    service.list(1, 10, 'SUSPENDED').subscribe();
    service.get('t-1').subscribe();
    service.activate('t-1').subscribe();
    service.suspend('t-1', 'Impago').subscribe();
    service.reactivate('t-1').subscribe();
    service.archive('t-1', 'Cierre').subscribe();
    service.audit(0, 20).subscribe();

    httpMock.expectOne('/api/v1/platform/tenants?page=1&size=10&status=SUSPENDED').flush({
      content: [],
      page: 1,
      size: 10,
      totalElements: 0,
      totalPages: 0
    });
    httpMock.expectOne('/api/v1/platform/tenants/t-1').flush({});
    const activate = httpMock.expectOne('/api/v1/platform/tenants/t-1/activate');
    expect(activate.request.method).toBe('POST');
    activate.flush({});
    const suspend = httpMock.expectOne('/api/v1/platform/tenants/t-1/suspend');
    expect(suspend.request.body).toEqual({ reason: 'Impago' });
    suspend.flush({});
    httpMock.expectOne('/api/v1/platform/tenants/t-1/reactivate').flush({});
    httpMock.expectOne('/api/v1/platform/tenants/t-1/archive').flush({});
    httpMock.expectOne('/api/v1/platform/audit?page=0&size=20').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });
  });

  it('creates a tenant via the platform endpoint', () => {
    service
      .create({
        tenantName: 'Acme',
        timezone: 'Europe/Madrid',
        adminEmail: 'a@acme.test',
        adminPassword: 'supersecretpwd',
        firstName: 'Ana',
        lastName: 'Doe'
      })
      .subscribe();
    const req = httpMock.expectOne('/api/v1/platform/tenants');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.tenantName).toBe('Acme');
    req.flush({ tenantId: 't-1', adminUserId: 'u-1' });
  });

  it('omits the status query param when no filter is set', () => {
    service.list(0, 20).subscribe();
    httpMock.expectOne('/api/v1/platform/tenants?page=0&size=20').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    });
  });
});
