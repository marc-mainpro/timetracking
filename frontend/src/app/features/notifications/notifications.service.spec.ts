import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { NotificationsService, PagedNotifications } from './notifications.service';

describe('NotificationsService', () => {
  let service: NotificationsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(NotificationsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists notifications with pagination', () => {
    const page: PagedNotifications = {
      content: [],
      page: 1,
      size: 10,
      totalElements: 0,
      totalPages: 0
    };
    let received: PagedNotifications | undefined;

    service.list(1, 10).subscribe((result) => (received = result));

    httpMock.expectOne('/api/v1/notifications?page=1&size=10').flush(page);
    expect(received).toEqual(page);
  });

  it('reads the unread count', () => {
    let unread: number | undefined;

    service.unreadCount().subscribe((result) => (unread = result.unread));

    httpMock.expectOne('/api/v1/notifications/unread-count').flush({ unread: 3 });
    expect(unread).toBe(3);
  });

  it('marks a notification as read', () => {
    service.markRead('abc').subscribe();

    const request = httpMock.expectOne('/api/v1/notifications/abc/read');
    expect(request.request.method).toBe('POST');
    request.flush(null);
  });
});
