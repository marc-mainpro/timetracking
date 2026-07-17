import { CanActivateFn } from '@angular/router';

/**
 * Guard de autenticación placeholder.
 *
 * TODO T204: comprobar el estado de autenticación real (AuthService) y
 * redirigir a /auth/login si el usuario no está autenticado.
 * TODO T404: aplicar guard de rol (TENANT_ADMIN vs EMPLOYEE) según ruta.
 *
 * De momento deja pasar siempre para permitir la navegación entre
 * rutas placeholder durante el scaffolding inicial.
 */
export const authGuard: CanActivateFn = () => {
  return true;
};
