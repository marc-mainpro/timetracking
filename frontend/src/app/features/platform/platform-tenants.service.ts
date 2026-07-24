import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type TenantStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';

export interface PlatformTenantSummary {
  id: string;
  name: string;
  status: TenantStatus;
  timezone: string;
  createdAt: string;
  activatedAt: string | null;
  suspendedAt: string | null;
}

export interface PlatformTenantDetail extends PlatformTenantSummary {
  updatedAt: string;
  archivedAt: string | null;
  suspensionReason: string | null;
}

export interface PagedTenants {
  content: PlatformTenantSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateTenantPayload {
  tenantName: string;
  timezone: string;
  adminEmail: string;
  adminPassword: string;
  firstName: string;
  lastName: string;
}

export interface CreatedTenant {
  tenantId: string;
  adminUserId: string;
}

export interface AuditEvent {
  id: string;
  actorUserId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  metadata: Record<string, unknown>;
  occurredAt: string;
}

export interface PagedAuditEvents {
  content: AuditEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class PlatformTenantsService {
  private readonly http = inject(HttpClient);

  list(page: number, size: number, status?: string): Observable<PagedTenants> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PagedTenants>('/api/v1/platform/tenants', { params });
  }

  create(payload: CreateTenantPayload): Observable<CreatedTenant> {
    return this.http.post<CreatedTenant>('/api/v1/platform/tenants', payload);
  }

  get(tenantId: string): Observable<PlatformTenantDetail> {
    return this.http.get<PlatformTenantDetail>(`/api/v1/platform/tenants/${tenantId}`);
  }

  activate(tenantId: string): Observable<PlatformTenantDetail> {
    return this.http.post<PlatformTenantDetail>(`/api/v1/platform/tenants/${tenantId}/activate`, {});
  }

  suspend(tenantId: string, reason: string): Observable<PlatformTenantDetail> {
    return this.http.post<PlatformTenantDetail>(`/api/v1/platform/tenants/${tenantId}/suspend`, { reason });
  }

  reactivate(tenantId: string): Observable<PlatformTenantDetail> {
    return this.http.post<PlatformTenantDetail>(`/api/v1/platform/tenants/${tenantId}/reactivate`, {});
  }

  archive(tenantId: string, reason?: string): Observable<PlatformTenantDetail> {
    return this.http.post<PlatformTenantDetail>(`/api/v1/platform/tenants/${tenantId}/archive`, { reason });
  }

  audit(page: number, size: number): Observable<PagedAuditEvents> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedAuditEvents>('/api/v1/platform/audit', { params });
  }
}
