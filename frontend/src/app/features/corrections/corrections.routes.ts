import { Routes } from '@angular/router';

import { AdminCorrectionsComponent } from './admin-corrections.component';
import { CorrectionsComponent } from './corrections.component';

export const CORRECTIONS_ROUTES: Routes = [
  {
    path: '',
    component: CorrectionsComponent
  }
];

export const ADMIN_CORRECTIONS_ROUTES: Routes = [
  {
    path: '',
    component: AdminCorrectionsComponent
  }
];
