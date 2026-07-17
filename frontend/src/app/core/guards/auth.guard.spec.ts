import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { authGuard } from './auth.guard';
import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard and roleGuard', () => {
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    authService = TestBed.inject(AuthService);
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
});

function sampleToken(roles: string[]): string {
  const payload = btoa(JSON.stringify({ sub: 'user-id', tenantId: 'tenant-id', roles, exp: Math.floor(Date.now() / 1000) + 3600 }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${payload}.signature`;
}
