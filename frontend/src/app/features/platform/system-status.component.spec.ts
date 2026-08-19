import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { SystemStatusComponent } from './system-status.component';
import { SystemStatus } from './system-status.service';

describe('SystemStatusComponent', () => {
  let fixture: ComponentFixture<SystemStatusComponent>;
  let component: SystemStatusComponent;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SystemStatusComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    fixture = TestBed.createComponent(SystemStatusComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flush(status: SystemStatus): void {
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/platform/system-status').flush(status);
    fixture.detectChanges();
  }

  it('avisa cuando algo agotó sus reintentos', () => {
    flush({
      queues: [
        { name: 'outbox', pending: 0, failed: 2 },
        { name: 'notifications', pending: 3, failed: 0 }
      ],
      totalFailed: 2,
      needsAttention: true
    });

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Elementos que agotaron sus reintentos');
    expect(text).toContain('hay que intervenir');
    // La cifra de fallos es el dato más grande de la pantalla.
    expect(text).toContain('2');
  });

  it('usa el singular cuando solo ha fallado un elemento', () => {
    flush({
      queues: [{ name: 'outbox', pending: 0, failed: 1 }],
      totalFailed: 1,
      needsAttention: true
    });

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Un elemento agotó');
  });

  it('deja constancia de cuándo se consultó el estado', () => {
    flush({ queues: [], totalFailed: 0, needsAttention: false });

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Consultado');
  });

  it('no alarma cuando solo hay trabajo pendiente', () => {
    // Lo pendiente se procesa solo: marcarlo como incidencia haría que el panel
    // avisara siempre y dejara de mirarse.
    flush({
      queues: [{ name: 'outbox', pending: 500, failed: 0 }],
      totalFailed: 0,
      needsAttention: false
    });

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Sin incidencias');
  });

  it('traduce el nombre técnico de cada cola', () => {
    flush({ queues: [{ name: 'notifications', pending: 0, failed: 0 }], totalFailed: 0, needsAttention: false });

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Envío de notificaciones');
  });

  it('solo deja abrir el detalle de una cola con fallos', () => {
    flush({
      queues: [
        { name: 'outbox', pending: 0, failed: 2 },
        { name: 'notifications', pending: 3, failed: 0 }
      ],
      totalFailed: 2,
      needsAttention: true
    });

    // En una cola sana no hay nada que resolver: ofrecer un desplegable vacío
    // sería una promesa que la pantalla no puede cumplir.
    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll('button.queue--actionable');
    expect(buttons.length).toBe(1);
  });

  it('despliega el detalle de la cola al pulsarla y lo pliega al repetir', () => {
    flush({ queues: [{ name: 'outbox', pending: 0, failed: 2 }], totalFailed: 2, needsAttention: true });

    component.toggleQueue('outbox');
    fixture.detectChanges();
    // El hijo pide sus elementos en cuanto aparece.
    httpMock.expectOne((r) => r.url === '/api/v1/platform/queues/outbox/failed').flush({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0
    });
    expect(component.expandedQueue()).toBe('outbox');

    component.toggleQueue('outbox');
    fixture.detectChanges();
    expect(component.expandedQueue()).toBeNull();
  });

  it('vuelve a consultar el estado cuando el detalle avisa de un cambio', () => {
    flush({ queues: [{ name: 'outbox', pending: 0, failed: 1 }], totalFailed: 1, needsAttention: true });

    component.onQueueChanged();

    // Sin esto el veredicto seguiría anunciando una incidencia ya resuelta.
    httpMock.expectOne('/api/v1/platform/system-status').flush({
      queues: [{ name: 'outbox', pending: 0, failed: 0 }],
      totalFailed: 0,
      needsAttention: false
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Sin incidencias');
  });

  it('muestra el error traducido si la consulta falla', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/v1/platform/system-status')
      .flush({ errorCode: 'FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });

    expect(component.error()).toBeTruthy();
    expect(component.loading()).toBeFalse();
  });
});
