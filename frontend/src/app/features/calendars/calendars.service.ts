import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type CalendarStatus = 'ACTIVE' | 'ARCHIVED';

export type AssignmentScope = 'TENANT' | 'TEAM' | 'EMPLOYEE';

/**
 * Regla que ha decidido si un día es laborable. Se muestra en la UI para que el
 * administrador pueda explicar el resultado sin abrir el calendario.
 */
export type DaySource = 'OUT_OF_VALIDITY' | 'SPECIAL_DAY' | 'HOLIDAY' | 'WEEKLY_RULE';

export type WeekDay =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface DayRule {
  dayOfWeek: WeekDay;
  working: boolean;
  expectedMinutes: number;
}

/** Las fechas son locales (`YYYY-MM-DD`), nunca instantes: un festivo es un día del calendario civil. */
export interface Holiday {
  date: string;
  name: string;
}

export interface SpecialDay {
  date: string;
  name: string;
  expectedMinutes: number;
}

export interface CalendarSummary {
  id: string;
  name: string;
  timezone: string;
  validFrom: string;
  validTo: string | null;
  status: CalendarStatus;
  holidayCount: number;
  specialDayCount: number;
}

export interface CalendarDetail {
  id: string;
  name: string;
  timezone: string;
  validFrom: string;
  validTo: string | null;
  status: CalendarStatus;
  dayRules: DayRule[];
  holidays: Holiday[];
  specialDays: SpecialDay[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface SaveCalendarPayload {
  name: string;
  timezone: string;
  validFrom: string;
  validTo: string | null;
  dayRules: DayRule[];
  holidays: Holiday[];
  specialDays: SpecialDay[];
}

export interface PagedCalendars {
  content: CalendarSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CalendarAssignment {
  id: string;
  calendarId: string;
  scope: AssignmentScope;
  targetId: string | null;
  createdAt: string;
}

export interface PagedAssignments {
  content: CalendarAssignment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AssignCalendarPayload {
  calendarId: string;
  scope: AssignmentScope;
  targetId: string | null;
}

export interface EffectiveCalendar {
  calendarId: string;
  calendarName: string;
  timezone: string;
  assignmentId: string;
  scope: AssignmentScope;
  targetId: string | null;
  date: string;
  working: boolean;
  expectedMinutes: number;
  source: DaySource;
}

/** Cliente HTTP de la API de administración de calendarios (T70-05). */
@Injectable({ providedIn: 'root' })
export class CalendarsService {
  private readonly http = inject(HttpClient);

  list(page: number, size: number, status?: string): Observable<PagedCalendars> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PagedCalendars>('/api/v1/admin/calendars', { params });
  }

  get(calendarId: string): Observable<CalendarDetail> {
    return this.http.get<CalendarDetail>(`/api/v1/admin/calendars/${calendarId}`);
  }

  create(payload: SaveCalendarPayload): Observable<CalendarDetail> {
    return this.http.post<CalendarDetail>('/api/v1/admin/calendars', payload);
  }

  update(calendarId: string, payload: SaveCalendarPayload): Observable<CalendarDetail> {
    return this.http.put<CalendarDetail>(`/api/v1/admin/calendars/${calendarId}`, payload);
  }

  /** Archiva el calendario: es un borrado lógico, el recurso sigue consultable. */
  archive(calendarId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/admin/calendars/${calendarId}`);
  }

  listAssignments(page: number, size: number, calendarId?: string): Observable<PagedAssignments> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (calendarId) {
      params = params.set('calendarId', calendarId);
    }
    return this.http.get<PagedAssignments>('/api/v1/admin/calendar-assignments', { params });
  }

  assign(payload: AssignCalendarPayload): Observable<CalendarAssignment> {
    return this.http.post<CalendarAssignment>('/api/v1/admin/calendar-assignments', payload);
  }

  removeAssignment(assignmentId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/admin/calendar-assignments/${assignmentId}`);
  }

  /**
   * Resuelve el calendario efectivo de un empleado en una fecha aplicando la
   * precedencia empleado > equipo > tenant. `teamId` lo aporta quien llama: el
   * sistema todavía no gestiona equipos.
   */
  resolveEffective(employeeId: string, date: string, teamId?: string): Observable<EffectiveCalendar> {
    let params = new HttpParams().set('employeeId', employeeId).set('date', date);
    if (teamId) {
      params = params.set('teamId', teamId);
    }
    return this.http.get<EffectiveCalendar>('/api/v1/admin/calendar-assignments/effective', { params });
  }
}
