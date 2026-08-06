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
    canActivate: [authGuard, roleGuard(['EMPLOYEE'])],
    loadChildren: () =>
      import('./features/corrections/corrections.routes').then((m) => m.CORRECTIONS_ROUTES)
  },
  {
    path: 'absences',
    canActivate: [authGuard, roleGuard(['EMPLOYEE'])],
    loadChildren: () => import('./features/absences/absences.routes').then((m) => m.ABSENCES_ROUTES)
  },
  {
    path: 'shifts',
    canActivate: [authGuard, roleGuard(['EMPLOYEE'])],
    loadChildren: () => import('./features/shifts/shifts.routes').then((m) => m.SHIFTS_ROUTES)
  },
  {
    path: 'registro',
    loadChildren: () =>
      import('./features/registration/registration.routes').then((m) => m.REGISTRATION_ROUTES)
  },
  {
    path: 'reports',
    canActivate: [authGuard, roleGuard(['EMPLOYEE'])],
    loadChildren: () =>
      import('./features/reports/employee-report.routes').then((m) => m.EMPLOYEE_REPORT_ROUTES)
  },
  {
    path: 'admin/calendars',
    canActivate: [authGuard, roleGuard(['TENANT_ADMIN'])],
    loadChildren: () =>
      import('./features/calendars/calendars.routes').then((m) => m.CALENDARS_ROUTES)
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
    path: 'admin/corrections',
    canActivate: [authGuard, roleGuard(['TENANT_ADMIN'])],
    loadChildren: () =>
      import('./features/corrections/corrections.routes').then(
        (m) => m.ADMIN_CORRECTIONS_ROUTES
      )
  },
  {
    path: 'admin/absences',
    canActivate: [authGuard, roleGuard(['TENANT_ADMIN'])],
    loadChildren: () =>
      import('./features/absences/absences.routes').then((m) => m.ADMIN_ABSENCES_ROUTES)
  },
  {
    path: 'admin/shifts',
    canActivate: [authGuard, roleGuard(['TENANT_ADMIN'])],
    loadChildren: () => import('./features/shifts/shifts.routes').then((m) => m.ADMIN_SHIFTS_ROUTES)
  },
  {
    path: 'admin/reports',
    canActivate: [authGuard, roleGuard(['TENANT_ADMIN'])],
    loadChildren: () => import('./features/reports/reports.routes').then((m) => m.REPORTS_ROUTES)
  },
  {
    path: 'platform',
    canActivate: [authGuard, roleGuard(['PLATFORM_ADMIN'])],
    loadChildren: () => import('./features/platform/platform.routes').then((m) => m.PLATFORM_ROUTES)
  },
  {
    path: '**',
    redirectTo: 'auth/login'
  }
];
