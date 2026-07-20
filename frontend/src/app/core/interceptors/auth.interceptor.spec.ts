import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });
    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('adds bearer token to authenticated requests', () => {
    authService['clearSession']();
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));

    httpClient.get('/api/v1/workdays/current').subscribe();

    const request = httpMock.expectOne('/api/v1/workdays/current');
    expect(request.request.headers.get('Authorization')).toContain('Bearer ');
    request.flush({});
  });

  it('retries once after refresh on 401', () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));

    httpClient.get('/api/v1/workdays/current').subscribe();

    const first = httpMock.expectOne('/api/v1/workdays/current');
    first.flush({}, { status: 401, statusText: 'Unauthorized' });

    const refresh = httpMock.expectOne('/api/v1/auth/refresh');
    refresh.flush({ accessToken: sampleToken(['EMPLOYEE']), expiresAt: new Date(Date.now() + 60_000).toISOString() });

    const retried = httpMock.expectOne('/api/v1/workdays/current');
    expect(retried.request.headers.get('X-Auth-Retry')).toBe('1');
    retried.flush({});
  });

  it('does not retry refresh requests and clears the session when refresh fails', () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);

    httpClient.get('/api/v1/workdays/current').subscribe({ error: () => undefined });

    const first = httpMock.expectOne('/api/v1/workdays/current');
    first.flush({}, { status: 401, statusText: 'Unauthorized' });

    const refresh = httpMock.expectOne('/api/v1/auth/refresh');
    refresh.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authService.getAccessToken()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/auth/login']);
  });

  it('does not retry a request already marked with X-Auth-Retry', () => {
    let failed = false;
    httpClient.get('/api/v1/workdays/current', { headers: { 'X-Auth-Retry': '1' } }).subscribe({ error: () => { failed = true; } });

    const request = httpMock.expectOne('/api/v1/workdays/current');
    expect(request.request.headers.get('X-Auth-Retry')).toBe('1');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectNone('/api/v1/auth/refresh');
    expect(failed).toBeTrue();
  });
});

function sampleToken(roles: string[]): string {
  const payload = btoa(JSON.stringify({ sub: 'user-id', tenantId: 'tenant-id', roles, exp: Math.floor(Date.now() / 1000) + 3600 }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${payload}.signature`;
}
