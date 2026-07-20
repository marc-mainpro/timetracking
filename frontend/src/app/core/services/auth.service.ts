import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, map, shareReplay, tap } from 'rxjs';

type AuthTokenResponse = {
  accessToken: string;
  expiresAt: string;
};

type LoginRequest = {
  email: string;
  password: string;
};

type RegisterRequest = {
  tenantName: string;
  timezone: string;
  adminEmail: string;
  adminPassword: string;
  firstName: string;
  lastName: string;
};

type JwtPayload = {
  sub: string;
  tenantId: string;
  roles: string[];
  exp: number;
};

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

  register(request: RegisterRequest): Observable<void> {
    return this.http.post('/api/v1/auth/register', request).pipe(map(() => void 0));
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
  }

  private setAccessToken(token: string): void {
    this.accessToken.set(token);
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
