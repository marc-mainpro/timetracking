import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Tipos del catálogo del backend (`NotificationType`). El enum es estable a
 * propósito: cada valor nuevo obliga a traducirlo aquí y en el mapa de
 * etiquetas, en lugar de mostrar el identificador crudo al usuario.
 */
export type NotificationType =
  // Empleado.
  | 'WORKDAY_ANOMALY_DETECTED'
  | 'CORRECTION_APPROVED'
  | 'CORRECTION_REJECTED'
  | 'ABSENCE_APPROVED'
  | 'ABSENCE_REJECTED'
  | 'ACCOUNT_CREATED'
  | 'ACCOUNT_DEACTIVATED'
  | 'SHIFT_ASSIGNED'
  // Administrador de tenant.
  | 'CORRECTION_REQUESTED'
  | 'ABSENCE_REQUESTED'
  | 'TEAM_WORKDAY_ANOMALY'
  | 'TENANT_SUSPENDED'
  | 'TENANT_REACTIVATED'
  | 'TENANT_ARCHIVED'
  // Administrador de plataforma.
  | 'REGISTRATION_PENDING_REVIEW'
  | 'SYSTEM_QUEUE_STUCK';

export interface AppNotification {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  /** Ruta de la aplicación a la que lleva el aviso, o `null` si es informativo. */
  actionPath: string | null;
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
