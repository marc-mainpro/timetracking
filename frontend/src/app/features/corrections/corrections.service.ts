import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Workday } from '../workdays/workdays.service';

export type CorrectionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ProposedBreak {
  startedAt: string;
  endedAt: string;
}

export interface ProposedChanges {
  startedAt: string;
  endedAt: string;
  breaks: ProposedBreak[];
}

export interface Correction {
  id: string;
  workdayId: string;
  requestedBy: string;
  reason: string;
  proposedChanges: ProposedChanges;
  status: CorrectionStatus;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolutionComment: string | null;
  createdAt: string;
}

export interface PagedCorrections {
  content: Correction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RequestCorrectionPayload {
  reason: string;
  proposedChanges: ProposedChanges;
}

@Injectable({ providedIn: 'root' })
export class CorrectionsService {
  private readonly http = inject(HttpClient);

  list(page: number, size: number, status?: CorrectionStatus): Observable<PagedCorrections> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PagedCorrections>('/api/v1/corrections', { params });
  }

  get(correctionId: string): Observable<Correction> {
    return this.http.get<Correction>(`/api/v1/corrections/${correctionId}`);
  }

  request(workdayId: string, payload: RequestCorrectionPayload): Observable<Correction> {
    return this.http.post<Correction>(`/api/v1/workdays/${workdayId}/corrections`, payload);
  }

  approve(correctionId: string, resolutionComment?: string): Observable<Correction> {
    return this.http.post<Correction>(`/api/v1/corrections/${correctionId}/approve`, {
      resolutionComment: resolutionComment || null
    });
  }

  reject(correctionId: string, resolutionComment: string): Observable<Correction> {
    return this.http.post<Correction>(`/api/v1/corrections/${correctionId}/reject`, { resolutionComment });
  }

  getOwnWorkday(workdayId: string): Observable<Workday> {
    return this.http.get<Workday>(`/api/v1/workdays/${workdayId}`);
  }

  getAdminWorkday(workdayId: string): Observable<Workday> {
    return this.http.get<Workday>(`/api/v1/admin/workdays/${workdayId}`);
  }
}
