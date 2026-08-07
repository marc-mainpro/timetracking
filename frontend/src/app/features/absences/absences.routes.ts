import { Routes } from '@angular/router';

import { AbsencesComponent } from './absences.component';
import { AdminAbsencesComponent } from './admin-absences.component';

export const ABSENCES_ROUTES: Routes = [
  {
    path: '',
    component: AbsencesComponent
  }
];

export const ADMIN_ABSENCES_ROUTES: Routes = [
  {
    path: '',
    component: AdminAbsencesComponent
  }
];
