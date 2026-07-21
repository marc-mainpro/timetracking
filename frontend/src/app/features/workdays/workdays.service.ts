import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface BreakEntry {
  id: string;
  startedAt: string;
  endedAt: string | null;
}

export interface Workday {
  id: string;
  status: 'OPEN' | 'ON_BREAK' | 'CLOSED' | 'ADJUSTED';
  startedAt: string;
  endedAt: string | null;
  breaks: BreakEntry[];
  workedDuration: string;
}

export interface PagedWorkdays {
  content: Workday[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class WorkdaysService {
  private readonly http = inject(HttpClient);

  getCurrent(): Observable<Workday> {
    return this.http.get<Workday>('/api/v1/workdays/current');
  }

  start(): Observable<Workday> {
    return this.http.post<Workday>('/api/v1/workdays/start', {});
  }

  startBreak(): Observable<Workday> {
    return this.http.post<Workday>('/api/v1/workdays/current/breaks/start', {});
  }

  endBreak(): Observable<Workday> {
    return this.http.post<Workday>('/api/v1/workdays/current/breaks/end', {});
  }

  endWorkday(): Observable<Workday> {
    return this.http.post<Workday>('/api/v1/workdays/current/end', {});
  }

  list(page: number, size: number, from?: string, to?: string): Observable<PagedWorkdays> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<PagedWorkdays>('/api/v1/workdays', { params });
  }
}
