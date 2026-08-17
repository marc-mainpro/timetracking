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

  /**
   * El refresh se serializa entre pestañas con un cerrojo que concede de forma
   * asíncrona, así que la petición no aparece en el mismo turno que el 401.
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

  it('adds bearer token to authenticated requests', () => {
    authService['clearSession']();
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));

    httpClient.get('/api/v1/workdays/current').subscribe();

    const request = httpMock.expectOne('/api/v1/workdays/current');
    expect(request.request.headers.get('Authorization')).toContain('Bearer ');
    request.flush({});
  });

  it('retries once after refresh on 401', async () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));

    httpClient.get('/api/v1/workdays/current').subscribe();

    const first = httpMock.expectOne('/api/v1/workdays/current');
    first.flush({}, { status: 401, statusText: 'Unauthorized' });

    const refresh = await waitForRefresh();
    refresh.flush({ accessToken: sampleToken(['EMPLOYEE']), expiresAt: new Date(Date.now() + 60_000).toISOString() });
    await new Promise((resolve) => setTimeout(resolve, 0));

    const retried = httpMock.expectOne('/api/v1/workdays/current');
    expect(retried.request.headers.get('X-Auth-Retry')).toBe('1');
    retried.flush({});
  });

  it('does not retry refresh requests and clears the session when refresh fails', async () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);

    httpClient.get('/api/v1/workdays/current').subscribe({ error: () => undefined });

    const first = httpMock.expectOne('/api/v1/workdays/current');
    first.flush({}, { status: 401, statusText: 'Unauthorized' });

    const refresh = await waitForRefresh();
    refresh.flush({}, { status: 401, statusText: 'Unauthorized' });
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(authService.getAccessToken()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/auth/login']);
  });

  it('no envía el token a las rutas públicas aunque quede uno caducado en memoria', () => {
    // Escenario real: la sesión expira, el token sigue en el signal (no hay
    // recarga de página) y el usuario entra en «he olvidado mi contraseña».
    // Con la cabecera puesta, Spring rechaza la petición con 401 antes de
    // evaluar el permitAll y el usuario se queda sin poder pedir el enlace.
    authService['accessToken'].set(expiredToken());

    httpClient.post('/api/v1/auth/password/forgot', { email: 'ana@empresa.test' }).subscribe();

    const request = httpMock.expectOne('/api/v1/auth/password/forgot');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({ message: 'ok' }, { status: 202, statusText: 'Accepted' });
  });

  it('tampoco lo envía al login ni al alta pública', () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));

    httpClient.post('/api/v1/auth/login', {}).subscribe();
    httpClient.post('/api/v1/public/tenant-registrations', {}).subscribe();

    const login = httpMock.expectOne('/api/v1/auth/login');
    const registration = httpMock.expectOne('/api/v1/public/tenant-registrations');
    expect(login.request.headers.has('Authorization')).toBeFalse();
    expect(registration.request.headers.has('Authorization')).toBeFalse();
    login.flush({});
    registration.flush({});
  });

  it('sigue enviando el token a logout, que no es una ruta pública', () => {
    authService['accessToken'].set(sampleToken(['EMPLOYEE']));

    httpClient.post('/api/v1/auth/logout', {}).subscribe();

    const request = httpMock.expectOne('/api/v1/auth/logout');
    expect(request.request.headers.get('Authorization')).toContain('Bearer ');
    request.flush({});
  });

  it('no intenta refrescar la sesión ante el 401 de un enlace de recuperación caducado', () => {
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    let failed = false;

    httpClient
      .post('/api/v1/auth/password/reset', { token: 'caducado', newPassword: 'contrasenanueva' })
      .subscribe({ error: () => { failed = true; } });

    httpMock
      .expectOne('/api/v1/auth/password/reset')
      .flush({ errorCode: 'INVALID_PASSWORD_RESET_TOKEN' }, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone('/api/v1/auth/refresh');
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(failed).toBeTrue();
  });

  it('no intenta refrescar ante el 401 de credenciales incorrectas del login', () => {
    // Sin esto, el error del refresh («Refresh token inválido o expirado»)
    // sustituía al INVALID_CREDENTIALS que el formulario debe mostrar.
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    let errorCode: string | undefined;

    httpClient
      .post('/api/v1/auth/login', { email: 'ana@empresa.test', password: 'mala' })
      .subscribe({ error: (error) => { errorCode = error.error?.errorCode; } });

    httpMock
      .expectOne('/api/v1/auth/login')
      .flush({ errorCode: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone('/api/v1/auth/refresh');
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(errorCode).toBe('INVALID_CREDENTIALS');
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

/** Token vencido pero todavía en memoria: el signal no se limpia solo. */
function expiredToken(): string {
  return sampleToken(['EMPLOYEE'], -3600);
}
