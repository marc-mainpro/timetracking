import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { AppComponent } from './app.component';
import { routes } from './app.routes';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter(routes)]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the navigation links', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const links = compiled.querySelectorAll('nav a');
    expect(links.length).toBeGreaterThan(0);
  });

  it('should navigate between placeholder routes', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/employee-dashboard');
    expect(router.url).toBe('/employee-dashboard');

    await router.navigateByUrl('/workdays');
    expect(router.url).toBe('/workdays');
  });
});
