import { Routes } from '@angular/router';

import { PlatformTenantsComponent } from './platform-tenants.component';

export const PLATFORM_ROUTES: Routes = [
  {
    path: 'tenants',
    component: PlatformTenantsComponent
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'tenants'
  }
];
