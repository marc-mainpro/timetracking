import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Interceptor HTTP placeholder.
 *
 * TODO T204: añadir cabecera Authorization Bearer con el access token en
 * memoria y, ante 401, intentar refresh una vez (ver CONTEXT-API §4).
 *
 * De momento es un paso a través (no modifica la petición) porque este
 * scaffolding no incluye llamadas HTTP reales.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req);
};
