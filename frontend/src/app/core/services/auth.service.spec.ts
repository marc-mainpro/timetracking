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

  it('reuses a single refresh request while one is in flight', () => {
    let firstToken = '';
    let secondToken = '';
    service.refresh().subscribe();
    service.refresh().subscribe((token) => {
      firstToken = token;
    });
    service.refresh().subscribe((token) => {
      secondToken = token;
    });

    const request = httpMock.expectOne('/api/v1/auth/refresh');
    request.flush({
      accessToken: sampleToken(['EMPLOYEE']),
      expiresAt: new Date(Date.now() + 60_000).toISOString()
    });

    expect(firstToken).toContain('header.');
    expect(secondToken).toBe(firstToken);
  });

  it('clears the session on logout and ignores malformed or expired tokens', () => {
    service['accessToken'].set(sampleToken(['EMPLOYEE']));
    expect(service.currentUserId()).toBe('user-id');
    expect(service.currentRoles()).toEqual(['EMPLOYEE']);

    service.logout().subscribe();
    const logoutRequest = httpMock.expectOne('/api/v1/auth/logout');
    logoutRequest.flush({});
    expect(service.isAuthenticated()).toBeFalse();

    service['accessToken'].set('bad-token');
    expect(service.isAuthenticated()).toBeFalse();

    service['accessToken'].set(sampleToken(['EMPLOYEE'], -10));
    expect(service.isAuthenticated()).toBeFalse();
  });
});

function sampleToken(roles: string[], expiresInSeconds = 3600): string {
  const payload = btoa(
    JSON.stringify({
      sub: 'user-id',
      tenantId: 'tenant-id',
      roles,
      exp: Math.floor(Date.now() / 1000) + expiresInSeconds
    })
  )
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');

  return `header.${payload}.signature`;
}
