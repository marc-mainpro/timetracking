import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Role } from '../../core/models/role';

export interface Employee {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: 'ACTIVE' | 'INACTIVE';
  roles: Role[];
  createdAt: string;
  updatedAt: string;
}

export interface PagedEmployees {
  content: Employee[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateEmployeePayload {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  roles: Role[];
}

export interface UpdateEmployeePayload {
  firstName: string;
  lastName: string;
}

@Injectable({ providedIn: 'root' })
export class AdminEmployeesService {
  private readonly http = inject(HttpClient);

  list(page: number, size: number, status?: string, role?: Role): Observable<PagedEmployees> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    if (role) {
      params = params.set('role', role);
    }
    return this.http.get<PagedEmployees>('/api/v1/employees', { params });
  }

  /**
   * Quien puede recibir un turno o un calendario propio: solo empleados en
   * activo. Es un método aparte y no un parámetro suelto para que la definición
   * de «asignable» viva en un único sitio; el backend rechaza con 409 cualquier
   * asignación que se salte esto.
   */
  listAssignable(page = 0, size = 100): Observable<PagedEmployees> {
    return this.list(page, size, 'ACTIVE', 'EMPLOYEE');
  }

  create(payload: CreateEmployeePayload): Observable<Employee> {
    return this.http.post<Employee>('/api/v1/employees', payload);
  }

  update(employeeId: string, payload: UpdateEmployeePayload): Observable<Employee> {
    return this.http.put<Employee>(`/api/v1/employees/${employeeId}`, payload);
  }

  activate(employeeId: string): Observable<Employee> {
    return this.http.patch<Employee>(`/api/v1/employees/${employeeId}/activate`, {});
  }

  deactivate(employeeId: string): Observable<Employee> {
    return this.http.patch<Employee>(`/api/v1/employees/${employeeId}/deactivate`, {});
  }

  assignRoles(employeeId: string, roles: Role[]): Observable<Employee> {
    return this.http.put<Employee>(`/api/v1/employees/${employeeId}/roles`, { roles });
  }
}
