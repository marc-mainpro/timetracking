import { Routes } from '@angular/router';

import { ShiftsComponent } from './shifts.component';
import { AdminShiftsComponent } from './admin-shifts.component';

export const SHIFTS_ROUTES: Routes = [
  {
    path: '',
    component: ShiftsComponent
  }
];

export const ADMIN_SHIFTS_ROUTES: Routes = [
  {
    path: '',
    component: AdminShiftsComponent
  }
];
