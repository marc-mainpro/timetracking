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
    expect(text).toContain('2 elementos agotaron');
    expect(text).toContain('requieren intervención');
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

  it('muestra el error traducido si la consulta falla', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/v1/platform/system-status')
      .flush({ errorCode: 'FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });

    expect(component.error()).toBeTruthy();
    expect(component.loading()).toBeFalse();
  });
});
