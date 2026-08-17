import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import {
  Observable,
  catchError,
  defer,
  finalize,
  firstValueFrom,
  from,
  map,
  of,
  shareReplay,
  tap,
  timeout
} from 'rxjs';

/**
 * Marca de que este navegador llegó a tener sesión. No es el token ni ningún
 * dato de ella —solo un booleano—, así que no contradice ADR-0004: sirve para
 * no pedir un refresh en cada visita de quien nunca ha entrado.
 */
const SESSION_HINT_KEY = 'tfp.session';

/** Nombre del cerrojo entre pestañas; ver `restoreSession`. */
const REFRESH_LOCK = 'tfp.auth-refresh';

/** Tope de espera del arranque para recuperar la sesión. */
const RESTORE_TIMEOUT_MS = 8000;

interface AuthTokenResponse {
  accessToken: string;
  expiresAt: string;
}

interface LoginRequest {
  email: string;
  password: string;
}

export interface PasswordResetAccepted {
  message: string;
}

interface JwtPayload {
  sub: string;
  tenantId: string;
  roles: string[];
  exp: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly accessToken = signal<string | null>(null);
  private readonly refreshInFlight = signal<Observable<string> | null>(null);
  private readonly claims = computed(() => this.parseToken(this.accessToken()));

  login(request: LoginRequest): Observable<void> {
    return this.http
      .post<AuthTokenResponse>('/api/v1/auth/login', request, { withCredentials: true })
      .pipe(tap((response) => this.setAccessToken(response.accessToken)), map(() => void 0));
  }

  /**
   * Recupera la sesión al arrancar la aplicación.
   *
   * <p>El access token vive en memoria (ADR-0004), así que una recarga lo
   * pierde; la cookie de refresh, en cambio, dura 14 días y sigue ahí. Esto la
   * canjea por un token nuevo antes de que el router evalúe los guards, que es
   * lo que evita acabar en el login teniendo sesión.
   *
   * <p>Nunca falla: un 401 aquí solo significa que no hay sesión que recuperar.
   */
  restoreSession(): Observable<boolean> {
    if (this.accessToken()) {
      return of(true);
    }
    if (!this.hasSessionHint()) {
      return of(false);
    }

    return this.withRefreshLock(() =>
      this.refresh().pipe(
        map(() => true),
        catchError(() => {
          this.clearSession();
          return of(false);
        })
      )
    ).pipe(
      // El arranque espera a esto, así que no puede quedarse colgado: si el
      // backend no responde —o el cerrojo lo retiene otra pestaña atascada—, la
      // aplicación sigue sin sesión en lugar de dejar la pantalla en blanco.
      timeout({ first: RESTORE_TIMEOUT_MS }),
      catchError(() => of(false))
    );
  }

  /**
   * Serializa el refresh entre las pestañas del mismo navegador.
   *
   * <p>El refresh token es rotatorio con detección de reutilización: si dos
   * pestañas arrancan a la vez, ambas mandan la misma cookie, la segunda llega
   * con un valor ya rotado y el backend revoca la cadena entera, cerrando la
   * sesión en todas partes. Con el cerrojo, la segunda espera y para cuando le
   * toca el navegador ya guardó la cookie nueva.
   *
   * <p>Si el navegador no trae Web Locks se llama directamente: queda la carrera
   * que había antes, no una peor.
   */
  private withRefreshLock(work: () => Observable<boolean>): Observable<boolean> {
    const locks = navigator.locks;
    if (!locks) {
      return work();
    }
    return defer(() => from(locks.request(REFRESH_LOCK, () => firstValueFrom(work()))));
  }

  refresh(): Observable<string> {
    const inFlight = this.refreshInFlight();
    if (inFlight) {
      return inFlight;
    }

    const request = this.http
      .post<AuthTokenResponse>('/api/v1/auth/refresh', {}, { withCredentials: true })
      .pipe(
        tap((response) => this.setAccessToken(response.accessToken)),
        map((response) => response.accessToken),
        finalize(() => this.refreshInFlight.set(null)),
        shareReplay(1)
      );

    this.refreshInFlight.set(request);
    return request;
  }

  logout(): Observable<void> {
    return this.http.post('/api/v1/auth/logout', {}, { withCredentials: true }).pipe(
      tap(() => this.clearSession()),
      map(() => void 0)
    );
  }

  /**
   * Solicita el correo de recuperación (RF-USR-006). El backend responde 202 con
   * un mensaje neutro exista o no la cuenta: la pantalla debe mostrarlo tal cual,
   * sin deducir nada de él.
   */
  requestPasswordReset(email: string): Observable<PasswordResetAccepted> {
    return this.http.post<PasswordResetAccepted>('/api/v1/auth/password/forgot', { email });
  }

  /**
   * Consume el token del correo y fija la contraseña nueva. Va con
   * `withCredentials` porque la respuesta borra la cookie de refresh: el backend
   * revoca todas las sesiones al restablecer.
   */
  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http
      .post('/api/v1/auth/password/reset', { token, newPassword }, { withCredentials: true })
      .pipe(
        tap(() => this.clearSession()),
        map(() => void 0)
      );
  }

  isAuthenticated(): boolean {
    const claims = this.claims();
    return !!claims && claims.exp * 1000 > Date.now();
  }

  hasRole(role: string): boolean {
    return this.claims()?.roles.includes(role) ?? false;
  }

  currentRoles(): string[] {
    return this.claims()?.roles ?? [];
  }

  currentUserId(): string | null {
    return this.claims()?.sub ?? null;
  }

  getAccessToken(): string | null {
    return this.accessToken();
  }

  clearSession(): void {
    this.accessToken.set(null);
    this.forgetSessionHint();
  }

  private setAccessToken(token: string): void {
    this.accessToken.set(token);
    this.rememberSessionHint();
  }

  private hasSessionHint(): boolean {
    return this.readStorage()?.getItem(SESSION_HINT_KEY) === '1';
  }

  private rememberSessionHint(): void {
    this.readStorage()?.setItem(SESSION_HINT_KEY, '1');
  }

  private forgetSessionHint(): void {
    this.readStorage()?.removeItem(SESSION_HINT_KEY);
  }

  /** `localStorage` lanza si el navegador lo tiene bloqueado (modo privado). */
  private readStorage(): Storage | null {
    try {
      return localStorage;
    } catch {
      return null;
    }
  }

  private parseToken(token: string | null): JwtPayload | null {
    if (!token) {
      return null;
    }

    try {
      const [, payload] = token.split('.');
      if (!payload) {
        return null;
      }
      const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
      const json = decodeURIComponent(
        atob(normalized)
          .split('')
          .map((char) => `%${`00${char.charCodeAt(0).toString(16)}`.slice(-2)}`)
          .join('')
      );
      return JSON.parse(json) as JwtPayload;
    } catch {
      return null;
    }
  }
}
