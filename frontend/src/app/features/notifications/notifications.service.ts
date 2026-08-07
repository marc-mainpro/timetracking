import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type NotificationType =
  | 'WORKDAY_ANOMALY_DETECTED'
  | 'CORRECTION_APPROVED'
  | 'CORRECTION_REJECTED'
  | 'ABSENCE_APPROVED'
  | 'ABSENCE_REJECTED';

export interface AppNotification {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  createdAt: string;
  readAt: string | null;
  read: boolean;
}

export interface PagedNotifications {
  content: AppNotification[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UnreadCount {
  unread: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);

  list(page: number, size: number): Observable<PagedNotifications> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedNotifications>('/api/v1/notifications', { params });
  }

  unreadCount(): Observable<UnreadCount> {
    return this.http.get<UnreadCount>('/api/v1/notifications/unread-count');
  }

  markRead(notificationId: string): Observable<void> {
    return this.http.post<void>(`/api/v1/notifications/${notificationId}/read`, {});
  }
}
