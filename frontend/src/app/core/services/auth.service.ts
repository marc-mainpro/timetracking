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
    // Solo se descarta cuando consta que aquí no hay sesión. Si nunca se ha
    // escrito la marca —primera visita, o navegador que ya estaba autenticado
    // antes de que esta marca existiera— se intenta igual: al desplegar, esas
    // sesiones son válidas y perderlas obligaría a todo el mundo a entrar de
    // nuevo una vez.
    if (this.sessionHint() === 'absent') {
      return of(false);
    }

    return this.refresh().pipe(
      map(() => true),
      catchError((error: unknown) => {
        // Solo un rechazo del servidor prueba que no hay sesión. Ante un fallo
        // de red o un 5xx la cookie puede seguir siendo válida, así que se deja
        // la marca como está para poder reintentarlo en la siguiente carga.
        if (this.isSessionRejected(error)) {
          this.clearSession();
        }
        return of(false);
      }),
      // El arranque espera a esto, así que no puede quedarse colgado: si el
      // backend no responde —o el cerrojo lo retiene otra pestaña atascada—, la
      // aplicación sigue sin sesión en lugar de dejar la pantalla en blanco.
      timeout({ first: RESTORE_TIMEOUT_MS }),
      catchError(() => of(false))
    );
  }

  /**
   * Canjea la cookie de refresh por un access token nuevo.
   *
   * <p>Serializado entre las pestañas del mismo navegador: el refresh token es
   * rotatorio con detección de reutilización, así que si dos pestañas mandan la
   * misma cookie a la vez, la segunda llega con un valor ya rotado y el backend
   * revoca la cadena entera, cerrando la sesión en todas partes. El cerrojo
   * cubre este método —y no solo el arranque— porque el interceptor también
   * refresca al recibir un 401, y esas dos vías pueden coincidir.
   *
   * <p>`refreshInFlight` sigue colapsando las llamadas de una misma pestaña; el
   * cerrojo resuelve lo que aquello no ve, que es el resto de pestañas.
   */
  refresh(): Observable<string> {
    const inFlight = this.refreshInFlight();
    if (inFlight) {
      return inFlight;
    }

    const request = this.withRefreshLock(() => this.requestRefresh()).pipe(
      finalize(() => this.refreshInFlight.set(null)),
      shareReplay(1)
    );

    this.refreshInFlight.set(request);
    return request;
  }

  private requestRefresh(): Observable<string> {
    return this.http
      .post<AuthTokenResponse>('/api/v1/auth/refresh', {}, { withCredentials: true })
      .pipe(
        tap((response) => this.setAccessToken(response.accessToken)),
        map((response) => response.accessToken)
      );
  }

  /** Si el navegador no trae Web Locks se llama directo: queda la carrera que ya había. */
  private withRefreshLock<T>(work: () => Observable<T>): Observable<T> {
    const locks = navigator.locks;
    if (!locks) {
      return work();
    }
    return defer(() => from(locks.request(REFRESH_LOCK, () => firstValueFrom(work()))));
  }

  /** Un 401/403 del refresh significa que esa sesión ya no existe. */
  private isSessionRejected(error: unknown): boolean {
    const status = (error as { status?: number } | null)?.status;
    return status === 401 || status === 403;
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
    this.writeSessionHint('0');
  }

  private setAccessToken(token: string): void {
    this.accessToken.set(token);
    this.writeSessionHint('1');
  }

  /**
   * Tres estados, no dos: `present` y `absent` son lo que consta, y `unknown`
   * cubre tanto la primera visita como el navegador que ya tenía sesión antes
   * de que esta marca existiera. Ante la duda se intenta el refresh, que como
   * mucho cuesta un 401.
   */
  private sessionHint(): 'present' | 'absent' | 'unknown' {
    const stored = this.readHint();
    if (stored === '1') {
      return 'present';
    }
    return stored === '0' ? 'absent' : 'unknown';
  }

  /*
   * El almacenamiento es una optimización, nunca un motivo de fallo: en modo
   * privado o con la cuota agotada, tanto el acceso como la escritura pueden
   * lanzar. Como esto se ejecuta dentro del `tap` del login, dejar escapar la
   * excepción convertiría una autenticación correcta en un error en pantalla.
   */
  private readHint(): string | null {
    try {
      return localStorage.getItem(SESSION_HINT_KEY);
    } catch {
      return null;
    }
  }

  private writeSessionHint(value: '0' | '1'): void {
    try {
      localStorage.setItem(SESSION_HINT_KEY, value);
    } catch {
      // Sin marca se intentará el refresh en cada arranque: es el peor caso
      // aceptable, y no rompe nada.
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
