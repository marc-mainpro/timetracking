import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface EmployeeDaySummary {
  day: string;
  worked: string;
  paused: string;
  workdayCount: number;
  adjustedWorkdayCount: number;
  openWorkdays: number;
}

export interface TenantEmployeeSummary {
  employeeId: string;
  worked: string;
  paused: string;
  workdayCount: number;
  adjustedWorkdayCount: number;
  openWorkdays: number;
}

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);

  employeeSummary(employeeId: string, from: string, to: string): Observable<EmployeeDaySummary[]> {
    return this.http.get<EmployeeDaySummary[]>(`/api/v1/reports/employees/${employeeId}/summary`, {
      params: this.rangeParams(from, to)
    });
  }

  tenantSummary(from: string, to: string): Observable<TenantEmployeeSummary[]> {
    return this.http.get<TenantEmployeeSummary[]>('/api/v1/reports/tenant/summary', {
      params: this.rangeParams(from, to)
    });
  }

  exportTenantCsv(from: string, to: string): Observable<Blob> {
    return this.http.get('/api/v1/reports/tenant/export.csv', {
      params: this.rangeParams(from, to),
      responseType: 'blob'
    });
  }

  private rangeParams(from: string, to: string): HttpParams {
    return new HttpParams().set('from', from).set('to', to);
  }
}
