import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FailedQueueComponent } from './failed-queue.component';
import { FailedQueueEntry } from './failed-queue.service';

describe('FailedQueueComponent', () => {
  let fixture: ComponentFixture<FailedQueueComponent>;
  let component: FailedQueueComponent;
  let httpMock: HttpTestingController;

  const entry: FailedQueueEntry = {
    id: 'entry-1',
    tenantId: 'tenant-1',
    type: 'time-tracking.workday-closed.v1',
    reference: 'Workday#abc',
    attempts: 8,
    lastError: 'Connection refused',
    occurredAt: '2026-08-19T08:00:00Z'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [FailedQueueComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    fixture = TestBed.createComponent(FailedQueueComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('queue', 'outbox');
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushList(content: FailedQueueEntry[]): void {
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/platform/queues/outbox/failed')
      .flush({ content, page: 0, size: 10, totalElements: content.length, totalPages: 1 });
    fixture.detectChanges();
  }

  it('muestra qué falló, por qué y cuántos intentos llevaba', () => {
    flushList([entry]);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('time-tracking.workday-closed.v1');
    expect(text).toContain('Connection refused');
    expect(text).toContain('8 intentos');
  });

  it('dice que no queda nada cuando la cola está limpia', () => {
    flushList([]);

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No queda nada fallido');
  });

  it('reintenta tras confirmar y recarga la lista y los contadores', () => {
    flushList([entry]);
    const changed = jasmine.createSpy('changed');
    component.changed.subscribe(changed);

    component.askRetry(entry);
    component.confirmAction();

    httpMock.expectOne('/api/v1/platform/queues/outbox/failed/entry-1/retry').flush(null);
    // La lista se vuelve a pedir: el elemento ya no debería estar.
    httpMock
      .expectOne((r) => r.url === '/api/v1/platform/queues/outbox/failed')
      .flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(component.message()).toContain('devuelto a la cola');
    // Sin esto, el veredicto del panel seguiría anunciando una incidencia
    // que acaba de resolverse.
    expect(changed).toHaveBeenCalled();
  });

  it('no descarta sin motivo', () => {
    flushList([entry]);

    component.askDiscard(entry);
    component.confirmAction();

    // Ninguna petición sale: el motivo es la única explicación que quedará.
    httpMock.verify();
    expect(component.reasonControl.touched).toBeTrue();
  });

  it('descarta con el motivo escrito', () => {
    flushList([entry]);

    component.askDiscard(entry);
    component.reasonControl.setValue('evento duplicado');
    component.confirmAction();

    const req = httpMock.expectOne('/api/v1/platform/queues/outbox/failed/entry-1/discard');
    expect(req.request.body).toEqual({ reason: 'evento duplicado' });
    req.flush(null);
    httpMock
      .expectOne((r) => r.url === '/api/v1/platform/queues/outbox/failed')
      .flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(component.message()).toContain('descartado');
  });

  it('explica el conflicto y recarga cuando otra persona actuó antes', () => {
    flushList([entry]);

    component.askRetry(entry);
    component.confirmAction();

    httpMock
      .expectOne('/api/v1/platform/queues/outbox/failed/entry-1/retry')
      .flush({ errorCode: 'OUTBOX_MESSAGE_NOT_FAILED' }, { status: 409, statusText: 'Conflict' });
    // Aunque falle, la lista que se está viendo ya es falsa: se recarga.
    httpMock
      .expectOne((r) => r.url === '/api/v1/platform/queues/outbox/failed')
      .flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(component.error()).toContain('ya no está fallido');
  });

  it('ignora un segundo clic mientras la acción está en vuelo', () => {
    flushList([entry]);

    component.askRetry(entry);
    component.confirmAction();
    component.confirmAction();

    // Una sola petición: reintentar dos veces publicaría dos veces.
    const requests = httpMock.match('/api/v1/platform/queues/outbox/failed/entry-1/retry');
    expect(requests.length).toBe(1);
    requests[0].flush(null);
    httpMock
      .expectOne((r) => r.url === '/api/v1/platform/queues/outbox/failed')
      .flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('muestra el error del listado sin dejar la pantalla en blanco', () => {
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/platform/queues/outbox/failed')
      .flush({ errorCode: 'RESOURCE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(component.error()).toContain('No se encontró');
    expect(component.loading()).toBeFalse();
  });
});
