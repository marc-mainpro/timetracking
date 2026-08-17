import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { firstValueFrom } from 'rxjs';

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

  describe('restoreSession', () => {
    beforeEach(() => {
      localStorage.removeItem('tfp.session');
    });

    /**
     * El refresh de arranque pasa por el cerrojo entre pestañas, que concede de
     * forma asíncrona, así que la petición no existe todavía al volver de
     * `restoreSession()`: hay que esperar a que aparezca.
     */
    async function waitForRefresh() {
      for (let attempt = 0; attempt < 50; attempt += 1) {
        const matches = httpMock.match('/api/v1/auth/refresh');
        if (matches.length > 0) {
          return matches[0];
        }
        await new Promise((resolve) => setTimeout(resolve, 5));
      }
      throw new Error('El arranque no pidió el refresh');
    }

    it('canjea la cookie de refresh por un token nuevo al arrancar', async () => {
      // Quien entró alguna vez deja la marca; es lo que autoriza a intentarlo.
      localStorage.setItem('tfp.session', '1');
      const restored = firstValueFrom(service.restoreSession());

      const request = await waitForRefresh();
      expect(request.request.withCredentials).toBeTrue();
      request.flush({
        accessToken: sampleToken(['EMPLOYEE']),
        expiresAt: new Date(Date.now() + 60_000).toISOString()
      });

      expect(await restored).toBeTrue();
      expect(service.isAuthenticated()).toBeTrue();
    });

    it('no llama a la API si nunca hubo sesión en este navegador', async () => {
      const restored = await firstValueFrom(service.restoreSession());

      httpMock.expectNone('/api/v1/auth/refresh');
      expect(restored).toBeFalse();
      expect(service.isAuthenticated()).toBeFalse();
    });

    it('no llama a la API si el token ya está en memoria', async () => {
      localStorage.setItem('tfp.session', '1');
      service['accessToken'].set(sampleToken(['EMPLOYEE']));

      const restored = await firstValueFrom(service.restoreSession());

      httpMock.expectNone('/api/v1/auth/refresh');
      expect(restored).toBeTrue();
    });

    it('se queda sin sesión y olvida la marca si la cookie ya no vale', async () => {
      localStorage.setItem('tfp.session', '1');
      const restored = firstValueFrom(service.restoreSession());

      (await waitForRefresh()).flush(
        { errorCode: 'UNAUTHORIZED' },
        { status: 401, statusText: 'Unauthorized' }
      );

      // Un 401 aquí es «no hay sesión», no un error que deba propagarse.
      expect(await restored).toBeFalse();
      expect(service.isAuthenticated()).toBeFalse();
      expect(localStorage.getItem('tfp.session')).toBeNull();
    });

    it('deja la marca puesta al iniciar sesión y la borra al salir', () => {
      service.login({ email: 'ana@acme.test', password: 'secret' }).subscribe();
      httpMock.expectOne('/api/v1/auth/login').flush({
        accessToken: sampleToken(['EMPLOYEE']),
        expiresAt: new Date(Date.now() + 60_000).toISOString()
      });
      expect(localStorage.getItem('tfp.session')).toBe('1');

      service.logout().subscribe();
      httpMock.expectOne('/api/v1/auth/logout').flush({});
      expect(localStorage.getItem('tfp.session')).toBeNull();
    });
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
