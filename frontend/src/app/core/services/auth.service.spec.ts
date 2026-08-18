import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { firstValueFrom } from 'rxjs';

import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  /**
   * El refresh se serializa entre pestañas con un cerrojo que concede de forma
   * asíncrona, así que la petición no existe todavía al volver de la llamada.
   */
  async function waitForRefresh() {
    for (let attempt = 0; attempt < 50; attempt += 1) {
      const matches = httpMock.match('/api/v1/auth/refresh');
      if (matches.length > 0) {
        return matches[0];
      }
      await new Promise((resolve) => setTimeout(resolve, 5));
    }
    throw new Error('No se pidió el refresh');
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // El refresh abandonado deja su petición cancelada a propósito; lo que se
    // vigila aquí son las que quedaron sin respuesta.
    httpMock.verify({ ignoreCancelled: true });
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

  it('refreshes and replaces the in-memory token', async () => {
    service.refresh().subscribe();

    const request = await waitForRefresh();
    request.flush({
      accessToken: sampleToken(['TENANT_ADMIN']),
      expiresAt: new Date(Date.now() + 60_000).toISOString()
    });

    expect(service.hasRole('TENANT_ADMIN')).toBeTrue();
  });

  it('reuses a single refresh request while one is in flight', async () => {
    let firstToken = '';
    let secondToken = '';
    service.refresh().subscribe();
    service.refresh().subscribe((token) => {
      firstToken = token;
    });
    service.refresh().subscribe((token) => {
      secondToken = token;
    });

    const request = await waitForRefresh();
    request.flush({
      accessToken: sampleToken(['EMPLOYEE']),
      expiresAt: new Date(Date.now() + 60_000).toISOString()
    });
    // El cerrojo devuelve el valor a través de una promesa: los suscriptores lo
    // reciben en el siguiente turno, no al hacer flush.
    await new Promise((resolve) => setTimeout(resolve, 0));

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

    it('no llama a la API cuando consta que aquí no hay sesión', async () => {
      localStorage.setItem('tfp.session', '0');

      const restored = await firstValueFrom(service.restoreSession());

      httpMock.expectNone('/api/v1/auth/refresh');
      expect(restored).toBeFalse();
      expect(service.isAuthenticated()).toBeFalse();
    });

    it('lo intenta cuando no consta nada, para no tirar sesiones ya abiertas', async () => {
      // Es el navegador que ya estaba autenticado antes de existir la marca:
      // su cookie sigue siendo válida y descartarla forzaría un login de más.
      const restored = firstValueFrom(service.restoreSession());

      (await waitForRefresh()).flush({
        accessToken: sampleToken(['EMPLOYEE']),
        expiresAt: new Date(Date.now() + 60_000).toISOString()
      });

      expect(await restored).toBeTrue();
      expect(service.isAuthenticated()).toBeTrue();
    });

    it('conserva la marca si el refresh falla por algo pasajero', async () => {
      localStorage.setItem('tfp.session', '1');
      const restored = firstValueFrom(service.restoreSession());

      // Un 500 no prueba que la sesión no exista: la cookie puede seguir viva y
      // hay que poder reintentarlo en la siguiente carga.
      (await waitForRefresh()).flush({}, { status: 500, statusText: 'Server Error' });

      expect(await restored).toBeFalse();
      expect(localStorage.getItem('tfp.session')).toBe('1');
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
      expect(localStorage.getItem('tfp.session')).toBe('0');
    });

    it('descarta la respuesta del refresh que se dio por perdido', async () => {
      // Lo que hace el `timeout` del arranque cuando el backend no contesta a
      // tiempo. Sin cancelar, esa petición seguiría viva y su respuesta tardía
      // pisaría el token de la sesión que se abriera entre medias.
      localStorage.setItem('tfp.session', '1');
      let refreshError: unknown = null;
      service.refresh().subscribe({ error: (error: unknown) => (refreshError = error) });
      const request = await waitForRefresh();

      service['abandonRefresh']();
      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(request.cancelled).toBeTrue();
      expect(refreshError).toBeTruthy();
      expect(service.getAccessToken()).toBeNull();

      // Y el siguiente refresh arranca limpio, sin quedarse pegado al anterior.
      service.refresh().subscribe();
      (await waitForRefresh()).flush({
        accessToken: sampleToken(['EMPLOYEE']),
        expiresAt: new Date(Date.now() + 60_000).toISOString()
      });
      await new Promise((resolve) => setTimeout(resolve, 0));
      expect(service.isAuthenticated()).toBeTrue();
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
      expect(localStorage.getItem('tfp.session')).toBe('0');
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
