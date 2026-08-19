import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { AuthService } from './core/services/auth.service';

describe('routing con sesión iniciada', () => {
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()]
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    localStorage.clear();
  });

  afterEach(() => localStorage.clear());

  it('manda al panel de admin al pedir /auth/login', async () => {
    authService['accessToken'].set(sampleToken(['TENANT_ADMIN']));
    await router.navigateByUrl('/auth/login');
    expect(router.url).toBe('/admin/employees');
  });

  it('manda al panel al pedir la raíz', async () => {
    authService['accessToken'].set(sampleToken(['TENANT_ADMIN']));
    await router.navigateByUrl('/');
    expect(router.url).toBe('/admin/employees');
  });

  it('manda al panel al pedir una URL desconocida', async () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));
    await router.navigateByUrl('/no-existe');
    expect(router.url).toBe('/employee-dashboard');
  });
});

function sampleToken(roles: string[]): string {
  const payload = btoa(
    JSON.stringify({ sub: 'user-id', tenantId: 'tenant-id', roles, exp: Math.floor(Date.now() / 1000) + 3600 })
  )
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${payload}.signature`;
}
