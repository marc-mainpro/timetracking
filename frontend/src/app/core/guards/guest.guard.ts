import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { ViewModeService } from '../services/view-mode.service';

/**
 * Aparta del formulario de acceso a quien ya tiene sesión.
 *
 * <p>Sin esto, la raíz y las URL desconocidas —que redirigen a `auth/login`—
 * dejaban el formulario en pantalla teniendo sesión abierta, y volver atrás
 * desde cualquier página acababa en lo mismo.
 *
 * <p>Cubre solo el login: recuperar y restablecer contraseña siguen abiertas,
 * porque el enlace del correo debe funcionar aunque la sesión esté iniciada.
 *
 * <p>La comprobación puede ser síncrona porque el arranque espera a
 * `AuthService.restoreSession()` (ver `app.config.ts`): cuando el router evalúa
 * los guards, el token de una sesión válida ya está en memoria.
 *
 * <p>El destino lo decide `ViewModeService`, igual que en `role.guard.ts` y en
 * el login, para no volver a repetir aquí la prioridad de rol.
 */
export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const viewMode = inject(ViewModeService);
  const router = inject(Router);

  return authService.isAuthenticated() ? router.createUrlTree([viewMode.homeRoute()]) : true;
};
