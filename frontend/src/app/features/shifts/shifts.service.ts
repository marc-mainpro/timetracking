import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface ShiftTemplate {
  id: string;
  name: string;
  startTime: string;
  endTime: string;
  plannedBreakMinutes: number;
  status: 'ACTIVE' | 'ARCHIVED';
  crossesMidnight: boolean;
}

export interface ShiftAssignment {
  id: string;
  employeeId: string;
  shiftTemplateId: string;
  validFrom: string;
  validTo: string | null;
}

export interface AppShift {
  assignmentId: string;
  shiftTemplateId: string;
  name: string;
  startTime: string;
  endTime: string;
  crossesMidnight: boolean;
  plannedDuration: string;
  plannedBreakDuration: string;
  validFrom: string;
  validTo: string | null;
}

export interface ShiftTemplatePayload {
  name: string;
  startTime: string;
  endTime: string;
  plannedBreakMinutes: number;
}

export interface ShiftAssignmentPayload {
  employeeId: string;
  shiftTemplateId: string;
  validFrom: string;
  validTo?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ShiftsService {
  private readonly http = inject(HttpClient);

  listTemplates(): Observable<ShiftTemplate[]> {
    return this.http.get<ShiftTemplate[]>('/api/v1/admin/shifts/templates');
  }

  createTemplate(payload: ShiftTemplatePayload): Observable<ShiftTemplate> {
    return this.http.post<ShiftTemplate>('/api/v1/admin/shifts/templates', payload);
  }

  assign(payload: ShiftAssignmentPayload): Observable<ShiftAssignment> {
    return this.http.post<ShiftAssignment>('/api/v1/admin/shifts/assignments', payload);
  }

  listOwn(date: string): Observable<AppShift[]> {
    const params = new HttpParams().set('date', date);
    return this.http.get<AppShift[]>('/api/v1/app/shifts', { params });
  }
}
