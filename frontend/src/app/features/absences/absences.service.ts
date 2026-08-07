import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type AbsenceStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface AbsenceType {
  id: string;
  code: string;
  name: string;
  requiresApproval: boolean;
  allowsAttachment: boolean;
  active: boolean;
}

export interface Absence {
  id: string;
  employeeId: string;
  absenceTypeId: string;
  startDate: string;
  endDate: string;
  reason: string | null;
  status: AbsenceStatus;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolutionComment: string | null;
  createdAt: string;
}

export interface RequestAbsencePayload {
  absenceTypeId: string;
  startDate: string;
  endDate: string;
  reason?: string | null;
}

@Injectable({ providedIn: 'root' })
export class AbsencesService {
  private readonly http = inject(HttpClient);

  listTypes(): Observable<AbsenceType[]> {
    return this.http.get<AbsenceType[]>('/api/v1/app/absence-types');
  }

  request(payload: RequestAbsencePayload): Observable<Absence> {
    return this.http.post<Absence>('/api/v1/app/absences', payload);
  }

  listOwn(from?: string, to?: string): Observable<Absence[]> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<Absence[]>('/api/v1/app/absences', { params });
  }

  cancel(absenceId: string): Observable<Absence> {
    return this.http.post<Absence>(`/api/v1/app/absences/${absenceId}/cancel`, {});
  }

  listAdmin(from?: string, to?: string): Observable<Absence[]> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<Absence[]>('/api/v1/admin/absences', { params });
  }

  approve(absenceId: string, resolutionComment?: string | null): Observable<Absence> {
    return this.http.post<Absence>(`/api/v1/admin/absences/${absenceId}/approve`, {
      resolutionComment: resolutionComment || null
    });
  }

  reject(absenceId: string, resolutionComment?: string | null): Observable<Absence> {
    return this.http.post<Absence>(`/api/v1/admin/absences/${absenceId}/reject`, {
      resolutionComment: resolutionComment || null
    });
  }
}
