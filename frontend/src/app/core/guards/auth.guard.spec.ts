import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { authGuard } from './auth.guard';
import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';
import { ViewModeService } from '../services/view-mode.service';

describe('authGuard and roleGuard', () => {
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('redirects unauthenticated users to login', () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result?.toString()).toContain('/auth/login');
  });

  it('allows authenticated users with required role', () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));

    const result = TestBed.runInInjectionContext(() => roleGuard(['EMPLOYEE'])({} as never, {} as never));
    expect(result).toBeTrue();
  });

  it('allows authenticated users through authGuard', () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).toBeTrue();
  });

  it('redirects unauthenticated role-guarded users to login', () => {
    const result = TestBed.runInInjectionContext(() => roleGuard(['TENANT_ADMIN'])({} as never, {} as never));
    expect(result).toEqual(router.createUrlTree(['/auth/login']));
  });

  it('redirects authenticated users without role to employee dashboard', () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));
    const result = TestBed.runInInjectionContext(() => roleGuard(['TENANT_ADMIN'])({} as never, {} as never));
    expect(result).toEqual(router.createUrlTree(['/employee-dashboard']));
  });

  // Regresión: un TENANT_ADMIN que aterriza en una ruta de EMPLOYEE debe ir a
  // su panel de admin, no de vuelta a /employee-dashboard (que reevaluaría el
  // mismo guard y provocaría el bucle de redirección infinito).
  it('redirects a TENANT_ADMIN hitting an employee route to the admin area', () => {
    authService['accessToken'].set(sampleToken(['TENANT_ADMIN']));
    const result = TestBed.runInInjectionContext(() => roleGuard(['EMPLOYEE'])({} as never, {} as never));
    expect(result).toEqual(router.createUrlTree(['/admin/employees']));
  });

  it('redirects a PLATFORM_ADMIN hitting another route to the platform area', () => {
    authService['accessToken'].set(sampleToken(['PLATFORM_ADMIN']));
    const result = TestBed.runInInjectionContext(() => roleGuard(['TENANT_ADMIN'])({} as never, {} as never));
    expect(result).toEqual(router.createUrlTree(['/platform/tenants']));
  });

  // La vista es presentación, no permiso: quien administra y además ficha
  // conserva el acceso a la zona de administración aunque esté viendo el menú
  // de empleado, para que un enlace guardado siga funcionando.
  it('lets a user in the employee view still reach the admin area', () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE', 'TENANT_ADMIN']));
    TestBed.inject(ViewModeService).switchTo('EMPLOYEE');

    const result = TestBed.runInInjectionContext(() => roleGuard(['TENANT_ADMIN'])({} as never, {} as never));
    expect(result).toBeTrue();
  });

  it('sends a user without the role to the home of their active view', () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE', 'TENANT_ADMIN']));
    TestBed.inject(ViewModeService).switchTo('EMPLOYEE');

    const result = TestBed.runInInjectionContext(() => roleGuard(['PLATFORM_ADMIN'])({} as never, {} as never));
    expect(result).toEqual(router.createUrlTree(['/employee-dashboard']));
  });

  it('allows a PLATFORM_ADMIN through its own guard', () => {
    authService['accessToken'].set(sampleToken(['PLATFORM_ADMIN']));
    const result = TestBed.runInInjectionContext(() => roleGuard(['PLATFORM_ADMIN'])({} as never, {} as never));
    expect(result).toBeTrue();
  });
});

function sampleToken(roles: string[]): string {
  const payload = btoa(JSON.stringify({ sub: 'user-id', tenantId: 'tenant-id', roles, exp: Math.floor(Date.now() / 1000) + 3600 }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${payload}.signature`;
}
