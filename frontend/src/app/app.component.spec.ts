import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { AppComponent } from './app.component';
import { AuthService } from './core/services/auth.service';
import { routes } from './app.routes';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()]
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

  it('deja la barra en una sola línea para el administrador de plataforma', () => {
    const fixture = renderAsRoles(['PLATFORM_ADMIN']);

    const header = (fixture.nativeElement as HTMLElement).querySelector('.app-bar');
    expect(header?.classList).not.toContain('app-bar--stacked');
  });

  it('baja la navegación a una segunda banda cuando hay demasiados enlaces', () => {
    // Empleado y administración a la vez son trece enlaces: en línea se parten
    // en filas sueltas y los grupos dejan de distinguirse.
    const fixture = renderAsRoles(['EMPLOYEE', 'TENANT_ADMIN']);

    const header = (fixture.nativeElement as HTMLElement).querySelector('.app-bar');
    expect(header?.classList).toContain('app-bar--stacked');
  });

  it('should redirect unauthenticated navigation to login', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/employee-dashboard');
    expect(router.url).toBe('/auth/login');
  });
});

/** Monta el shell como si hubiera sesión con los roles indicados. */
function renderAsRoles(roles: string[]): ComponentFixture<AppComponent> {
  const authService = TestBed.inject(AuthService);
  spyOn(authService, 'isAuthenticated').and.returnValue(true);
  spyOn(authService, 'hasRole').and.callFake((role: string) => roles.includes(role));

  const fixture = TestBed.createComponent(AppComponent);
  fixture.detectChanges();
  return fixture;
}
