import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Employee {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: 'ACTIVE' | 'INACTIVE';
  roles: string[];
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
  roles: string[];
}

export interface UpdateEmployeePayload {
  firstName: string;
  lastName: string;
}

@Injectable({ providedIn: 'root' })
export class AdminEmployeesService {
  private readonly http = inject(HttpClient);

  list(page: number, size: number, status?: string): Observable<PagedEmployees> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PagedEmployees>('/api/v1/employees', { params });
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

  assignRoles(employeeId: string, roles: string[]): Observable<Employee> {
    return this.http.put<Employee>(`/api/v1/employees/${employeeId}/roles`, { roles });
  }
}
