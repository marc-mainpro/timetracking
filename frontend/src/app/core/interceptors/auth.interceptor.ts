import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const token = authService.getAccessToken();
  const withAuth = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(withAuth).pipe(
    catchError((error: HttpErrorResponse) => {
      const alreadyRetried = req.headers.has('X-Auth-Retry');
      const isRefreshRequest = req.url.includes('/api/v1/auth/refresh');
      if (error.status !== 401 || alreadyRetried || isRefreshRequest) {
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
