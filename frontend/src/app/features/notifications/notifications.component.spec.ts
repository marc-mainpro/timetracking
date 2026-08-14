import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';

import { NotificationsComponent } from './notifications.component';
import { AppNotification, PagedNotifications } from './notifications.service';

describe('NotificationsComponent', () => {
  let fixture: ComponentFixture<NotificationsComponent>;
  let component: NotificationsComponent;
  let httpMock: HttpTestingController;

  const unread: AppNotification = {
    id: 'n1',
    type: 'ABSENCE_APPROVED',
    title: 'Ausencia aprobada',
    body: 'Tu solicitud ha sido aprobada.',
    actionPath: '/absences',
    createdAt: '2026-08-06T10:00:00Z',
    readAt: null,
    read: false
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    fixture = TestBed.createComponent(NotificationsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushInitial(content: AppNotification[]): void {
    const page: PagedNotifications = {
      content,
      page: 0,
      size: 20,
      totalElements: content.length,
      totalPages: 1
    };
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/notifications?page=0&size=20').flush(page);
  }

  it('loads notifications on init', () => {
    flushInitial([unread]);

    expect(component.result()?.content.length).toBe(1);
    expect(component.loading()).toBeFalse();
  });

  it('marks an unread notification as read and reloads', () => {
    flushInitial([unread]);

    component.markRead(unread);

    httpMock.expectOne('/api/v1/notifications/n1/read').flush(null);
    httpMock
      .expectOne('/api/v1/notifications?page=0&size=20')
      .flush({ content: [{ ...unread, read: true, readAt: '2026-08-06T11:00:00Z' }], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    expect(component.result()?.content[0].read).toBeTrue();
  });

  it('does not call the API for an already read notification', () => {
    const read: AppNotification = { ...unread, read: true, readAt: '2026-08-06T11:00:00Z' };
    flushInitial([read]);

    component.markRead(read);

    httpMock.expectNone('/api/v1/notifications/n1/read');
  });

  it('translates the type of a notification of each role', () => {
    flushInitial([unread]);

    // El enum del backend es estable a propósito: cada tipo nuevo tiene que
    // llegar aquí traducido, no como identificador crudo.
    expect(component.typeLabel('ABSENCE_APPROVED')).toBe('Ausencia resuelta');
    expect(component.typeLabel('ABSENCE_REQUESTED')).toBe('Pendiente de resolver');
    expect(component.typeLabel('REGISTRATION_PENDING_REVIEW')).toBe('Alta pendiente');
  });

  it('navigates to the screen of the notification and marks it as read', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    flushInitial([unread]);

    component.open(unread);

    httpMock.expectOne('/api/v1/notifications/n1/read').flush(null);
    httpMock.expectOne('/api/v1/notifications?page=0&size=20').flush({
      content: [{ ...unread, read: true, readAt: '2026-08-06T11:00:00Z' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1
    });
    expect(navigate).toHaveBeenCalledWith('/absences');
  });

  it('does not navigate when the notification has no action path', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    const informational: AppNotification = {
      ...unread,
      id: 'n2',
      type: 'ACCOUNT_DEACTIVATED',
      actionPath: null
    };
    flushInitial([informational]);

    component.open(informational);

    httpMock.expectOne('/api/v1/notifications/n2/read').flush(null);
    httpMock.expectOne('/api/v1/notifications?page=0&size=20').flush({
      content: [{ ...informational, read: true }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1
    });
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows a translated error when the request fails', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/v1/notifications?page=0&size=20')
      .flush({ errorCode: 'INTERNAL_SERVER_ERROR' }, { status: 500, statusText: 'Server Error' });

    expect(component.error()).toBeTruthy();
    expect(component.loading()).toBeFalse();
  });
});
