import { Routes } from '@angular/router';

import { guestGuard } from '../../core/guards/guest.guard';
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
    // Con sesión abierta no se muestra: la raíz y las URL desconocidas
    // redirigen aquí, y quien ya ha entrado debe acabar en su pantalla.
    canActivate: [guestGuard],
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
