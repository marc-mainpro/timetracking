import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface FailedQueueEntry {
  id: string;
  /** Tenant afectado; null en los eventos que no pertenecen a ninguno. */
  tenantId: string | null;
  /** Tipo de evento o de notificación. */
  type: string;
  /** A qué se refiere, para poder rastrearlo. */
  reference: string;
  attempts: number;
  /** Último error; el backend lo trunca. */
  lastError: string | null;
  occurredAt: string;
}

export interface PagedFailedQueueEntries {
  content: FailedQueueEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class FailedQueueService {
  private readonly http = inject(HttpClient);

  private readonly baseUrl = '/api/v1/platform/queues';

  list(queue: string, page: number, size: number): Observable<PagedFailedQueueEntries> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedFailedQueueEntries>(`${this.baseUrl}/${queue}/failed`, { params });
  }

  retry(queue: string, entryId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${queue}/failed/${entryId}/retry`, {});
  }

  discard(queue: string, entryId: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${queue}/failed/${entryId}/discard`, { reason });
  }
}
