import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { AuthService } from '../services/auth.service';

export function roleGuard(requiredRoles: string[]): CanActivateFn {
  return (): boolean | UrlTree => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
      return router.createUrlTree(['/auth/login']);
    }

    if (requiredRoles.some((role) => authService.hasRole(role))) {
      return true;
    }

    return router.createUrlTree(['/employee-dashboard']);
  };
}
