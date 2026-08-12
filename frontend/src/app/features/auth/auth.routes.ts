import { Routes } from '@angular/router';

import { ForgotPasswordComponent } from './forgot-password.component';
import { LoginComponent } from './login.component';

/**
 * Rutas públicas de acceso. La de restablecer contraseña no vive aquí sino en la
 * raíz (`/restablecer-contrasena`), porque es la que genera el enlace del correo
 * (`auth.password-reset.reset-url-template` en el backend).
 */
export const AUTH_ROUTES: Routes = [
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: 'recuperar-contrasena',
    component: ForgotPasswordComponent
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'login'
  }
];
