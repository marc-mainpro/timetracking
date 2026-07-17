import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'auth/login'
  },
  {
    path: 'auth/login',
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.AUTH_ROUTES)
  },
  {
    path: 'employee-dashboard',
    // TODO T204/T404: proteger con authGuard real (autenticación + rol EMPLOYEE)
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/employee-dashboard/employee-dashboard.routes').then(
        (m) => m.EMPLOYEE_DASHBOARD_ROUTES
      )
  },
  {
    path: 'workdays',
    // TODO T204/T404: proteger con authGuard real (autenticación + rol EMPLOYEE)
    canActivate: [authGuard],
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
    // TODO T204/T404: proteger con authGuard real (autenticación + rol TENANT_ADMIN)
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/admin-employees/admin-employees.routes').then(
        (m) => m.ADMIN_EMPLOYEES_ROUTES
      )
  },
  {
    path: 'reports',
    // TODO T204/T404: proteger con authGuard real (autenticación + rol según ruta)
    canActivate: [authGuard],
    loadChildren: () => import('./features/reports/reports.routes').then((m) => m.REPORTS_ROUTES)
  },
  {
    path: '**',
    redirectTo: 'auth/login'
  }
];
