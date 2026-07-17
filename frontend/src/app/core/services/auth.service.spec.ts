import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('stores access token after login', () => {
    service.login({ email: 'ana@acme.test', password: 'secret' }).subscribe();

    const request = httpMock.expectOne('/api/v1/auth/login');
    request.flush({
      accessToken: sampleToken(['EMPLOYEE']),
      expiresAt: new Date(Date.now() + 60_000).toISOString()
    });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.hasRole('EMPLOYEE')).toBeTrue();
  });

  it('refreshes and replaces the in-memory token', () => {
    service.refresh().subscribe();

    const request = httpMock.expectOne('/api/v1/auth/refresh');
    request.flush({
      accessToken: sampleToken(['TENANT_ADMIN']),
      expiresAt: new Date(Date.now() + 60_000).toISOString()
    });

    expect(service.hasRole('TENANT_ADMIN')).toBeTrue();
  });
});

function sampleToken(roles: string[]): string {
  const payload = btoa(
    JSON.stringify({
      sub: 'user-id',
      tenantId: 'tenant-id',
      roles,
      exp: Math.floor(Date.now() / 1000) + 3600
    })
  )
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');

  return `header.${payload}.signature`;
}
