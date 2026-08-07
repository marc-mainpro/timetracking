import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type TenantRegistrationStatus =
  | 'PENDING_EMAIL_VERIFICATION'
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CONSUMED';

export interface TenantRegistration {
  id: string;
  companyName: string;
  ownerFirstName: string;
  ownerLastName: string;
  email: string;
  timezone: string;
  status: TenantRegistrationStatus;
  source: string;
  decisionReason: string | null;
  createdTenantId: string | null;
  createdAt: string;
  verifiedAt: string | null;
  decidedAt: string | null;
}

export interface PagedTenantRegistrations {
  content: TenantRegistration[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class PlatformRegistrationsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/platform/registrations';

  list(page: number, size: number, status?: string): Observable<PagedTenantRegistrations> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PagedTenantRegistrations>(this.baseUrl, { params });
  }

  approve(registrationId: string): Observable<TenantRegistration> {
    return this.http.post<TenantRegistration>(`${this.baseUrl}/${registrationId}/approve`, {});
  }

  reject(registrationId: string, reason: string): Observable<TenantRegistration> {
    return this.http.post<TenantRegistration>(`${this.baseUrl}/${registrationId}/reject`, { reason });
  }
}
