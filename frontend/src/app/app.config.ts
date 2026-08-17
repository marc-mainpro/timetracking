import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import { ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

/* Toda la interfaz está en español, pero sin registrar el locale Angular
   formatea con `en-US`: horas en 12 h con AM/PM y fechas mes/día. Con `es`,
   `date: 'short'` pasa a `14/8/26, 8:41` y `shortTime` a 24 h, que es como se
   leen las horas en el resto del producto. */
registerLocaleData(localeEs);

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: LOCALE_ID, useValue: 'es' },
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
};
