import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

/**
 * Rutas declaradas `permitAll` en el backend (`IdentityPublicEndpoints` y
 * `TenantPublicEndpoints`).
 *
 * <p>No deben llevar `Authorization`. El filtro de bearer de Spring se ejecuta
 * antes que la autorización, así que un token caducado o corrupto en memoria se
 * rechaza con 401 sin llegar a evaluarse el `permitAll`: mandarlo rompería el
 * login, la recuperación de contraseña y el alta pública justo para quien viene
 * de una sesión expirada.
 *
 * <p>Son prefijos exactos a propósito. `/api/v1/auth/` arrastraría `logout`, que
 * sí exige token.
 */
const PUBLIC_PATHS = [
  '/api/v1/auth/login',
  '/api/v1/auth/refresh',
  '/api/v1/auth/password/',
  '/api/v1/public/'
];

function isPublicRequest(url: string): boolean {
  return PUBLIC_PATHS.some((path) => url.includes(path));
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const token = isPublicRequest(req.url) ? null : authService.getAccessToken();
  const withAuth = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(withAuth).pipe(
    catchError((error: HttpErrorResponse) => {
      const alreadyRetried = req.headers.has('X-Auth-Retry');
      // Como las rutas públicas ya no llevan token, su 401 nunca habla de la
      // sesión: son las credenciales del login, el enlace de recuperación
      // caducado o el propio refresh agotado. Refrescar ahí sustituiría el
      // motivo real por el error del refresh («Refresh token inválido o
      // expirado») y redirigiría al login ocultándoselo al usuario.
      if (error.status !== 401 || alreadyRetried || isPublicRequest(req.url)) {
        return throwError(() => error);
      }

      return authService.refresh().pipe(
        switchMap((freshToken) =>
          next(
            req.clone({
              setHeaders: {
                Authorization: `Bearer ${freshToken}`,
                'X-Auth-Retry': '1'
              }
            })
          )
        ),
        catchError((refreshError) => {
          authService.clearSession();
          void router.navigate(['/auth/login']);
          return throwError(() => refreshError);
        })
      );
    })
  );
};
