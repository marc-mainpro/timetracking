import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface QueueStatus {
  name: string;
  /** Trabajo por procesar: se resuelve solo en la siguiente pasada del job. */
  pending: number;
  /** Trabajo que agotó sus reintentos: no se recupera solo. */
  failed: number;
}

export interface SystemStatus {
  queues: QueueStatus[];
  totalFailed: number;
  needsAttention: boolean;
}

@Injectable({ providedIn: 'root' })
export class SystemStatusService {
  private readonly http = inject(HttpClient);

  get(): Observable<SystemStatus> {
    return this.http.get<SystemStatus>('/api/v1/platform/system-status');
  }
}
