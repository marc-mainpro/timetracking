import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AppFooterComponent } from './app-footer.component';

describe('AppFooterComponent', () => {
  let fixture: ComponentFixture<AppFooterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AppFooterComponent] }).compileComponents();
    fixture = TestBed.createComponent(AppFooterComponent);
    fixture.detectChanges();
  });

  it('muestra la marca, el año y la zona horaria que interpreta las horas', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain(`${new Date().getFullYear()}`);
    expect(text).toContain(Intl.DateTimeFormat().resolvedOptions().timeZone);
  });
});
