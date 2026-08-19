import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FailedQueueService } from './failed-queue.service';

describe('FailedQueueService', () => {
  let service: FailedQueueService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(FailedQueueService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('pide los fallidos de la cola indicada, paginados', () => {
    service.list('outbox', 1, 10).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/platform/queues/outbox/failed' && r.method === 'GET'
    );
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('reintenta con POST y sin cuerpo significativo', () => {
    service.retry('notifications', 'entry-1').subscribe();

    const req = httpMock.expectOne('/api/v1/platform/queues/notifications/failed/entry-1/retry');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('envía el motivo al descartar', () => {
    service.discard('outbox', 'entry-2', 'duplicado').subscribe();

    const req = httpMock.expectOne('/api/v1/platform/queues/outbox/failed/entry-2/discard');
    expect(req.request.method).toBe('POST');
    // Sin motivo la acción no tendría explicación en la auditoría.
    expect(req.request.body).toEqual({ reason: 'duplicado' });
    req.flush(null);
  });
});
