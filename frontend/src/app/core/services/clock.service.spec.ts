import { TestBed } from '@angular/core/testing';

import { ClockService } from './clock.service';

describe('ClockService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('expone la hora actual', () => {
    const before = Date.now();
    const service = TestBed.inject(ClockService);

    expect(service.now()).toBeGreaterThanOrEqual(before);
    expect(service.now()).toBeLessThanOrEqual(Date.now());
  });

  it('mide el avance del día entre 0 y 1', () => {
    const service = TestBed.inject(ClockService);

    expect(service.dayProgress()).toBeGreaterThanOrEqual(0);
    expect(service.dayProgress()).toBeLessThan(1);
  });

  it('resuelve la zona horaria del navegador', () => {
    const service = TestBed.inject(ClockService);

    expect(service.timeZone).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
  });
});
