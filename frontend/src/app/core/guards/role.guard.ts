import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { ViewModeService } from '../services/view-mode.service';

/**
 * Autoriza por los roles reales del JWT, nunca por la vista activa: quien
 * administra y además ficha conserva el acceso a las dos zonas aunque el menú
 * solo muestre una, así que un enlace guardado de la otra sigue funcionando.
 *
 * <p>A quien no le corresponde la ruta se le lleva al inicio de su vista, que
 * decide `ViewModeService` —antes esa prioridad estaba repetida aquí, en la
 * cabecera y en el login, y no coincidía entre ellos—.
 */
export function roleGuard(requiredRoles: string[]): CanActivateFn {
  return (): boolean | UrlTree => {
    const authService = inject(AuthService);
    const viewMode = inject(ViewModeService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
      return router.createUrlTree(['/auth/login']);
    }

    if (requiredRoles.some((role) => authService.hasRole(role))) {
      return true;
    }

    return router.createUrlTree([viewMode.homeRoute()]);
  };
}
