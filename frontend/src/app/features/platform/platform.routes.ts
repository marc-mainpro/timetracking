import { Routes } from '@angular/router';

import { PlatformRegistrationsComponent } from './platform-registrations.component';
import { PlatformTenantsComponent } from './platform-tenants.component';

export const PLATFORM_ROUTES: Routes = [
  {
    path: 'registrations',
    component: PlatformRegistrationsComponent
  },
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
