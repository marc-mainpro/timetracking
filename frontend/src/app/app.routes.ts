import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'auth/login'
  },
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.AUTH_ROUTES)
  },
  {
    path: 'employee-dashboard',
    canActivate: [authGuard, roleGuard(['EMPLOYEE'])],
    loadChildren: () =>
      import('./features/employee-dashboard/employee-dashboard.routes').then(
        (m) => m.EMPLOYEE_DASHBOARD_ROUTES
      )
  },
  {
    path: 'workdays',
    canActivate: [authGuard, roleGuard(['EMPLOYEE'])],
    loadChildren: () => import('./features/workdays/workdays.routes').then((m) => m.WORKDAYS_ROUTES)
  },
  {
    path: 'corrections',
    // TODO T204/T404: proteger con authGuard real (autenticación)
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/corrections/corrections.routes').then((m) => m.CORRECTIONS_ROUTES)
  },
  {
    path: 'admin/employees',
    canActivate: [authGuard, roleGuard(['TENANT_ADMIN'])],
    loadChildren: () =>
      import('./features/admin-employees/admin-employees.routes').then(
        (m) => m.ADMIN_EMPLOYEES_ROUTES
      )
  },
  {
    path: 'reports',
    canActivate: [authGuard, roleGuard(['TENANT_ADMIN'])],
    loadChildren: () => import('./features/reports/reports.routes').then((m) => m.REPORTS_ROUTES)
  },
  {
    path: '**',
    redirectTo: 'auth/login'
  }
];
