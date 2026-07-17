import { Injectable, signal } from '@angular/core';

/**
 * Servicio de autenticación placeholder.
 *
 * TODO T204: guardar el access token en memoria (nunca el refresh token,
 * que viaja solo por cookie HttpOnly), exponer login/logout/refresh reales
 * contra /api/v1/auth/* según CONTEXT-API.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authenticated = signal(false);

  isAuthenticated(): boolean {
    return this.authenticated();
  }
}
