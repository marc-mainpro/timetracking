import { Routes } from '@angular/router';

import { TenantRegistrationComponent } from './tenant-registration.component';
import { VerifyRegistrationEmailComponent } from './verify-registration-email.component';

/**
 * Rutas públicas del alta de organizaciones (T53). No llevan guardas: son
 * previas a tener cuenta. La ruta de verificación es la que apunta el enlace
 * del correo (`registration.verification-url-template` en el backend).
 */
export const REGISTRATION_ROUTES: Routes = [
  {
    path: 'verificar',
    component: VerifyRegistrationEmailComponent
  },
  {
    path: '',
    pathMatch: 'full',
    component: TenantRegistrationComponent
  }
];
