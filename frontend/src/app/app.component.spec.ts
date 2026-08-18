import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { AppComponent } from './app.component';
import { AuthService } from './core/services/auth.service';
import { routes } from './app.routes';

describe('AppComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the navigation links', () => {
    const fixture = renderAsRoles(['EMPLOYEE']);

    const links = (fixture.nativeElement as HTMLElement).querySelectorAll('.app-nav a');
    expect(links.length).toBeGreaterThan(0);
  });

  it('deja la barra en una sola línea para el administrador de plataforma', () => {
    const fixture = renderAsRoles(['PLATFORM_ADMIN']);

    const header = (fixture.nativeElement as HTMLElement).querySelector('.app-bar');
    expect(header?.classList).not.toContain('app-bar--stacked');
  });

  it('no ofrece cambiar de vista a quien solo tiene un rol', () => {
    const fixture = renderAsRoles(['EMPLOYEE']);

    expect((fixture.nativeElement as HTMLElement).querySelector('.view-switch')).toBeNull();
  });

  it('muestra un solo menú a quien acumula empleado y administración', () => {
    const fixture = renderAsRoles(['EMPLOYEE', 'TENANT_ADMIN']);

    const titles = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.app-nav__title')
    ).map((title) => title.textContent?.trim());

    // Administración por prioridad, más el grupo transversal de cuenta.
    expect(titles).toEqual(['Administración', 'Cuenta']);
    expect((fixture.nativeElement as HTMLElement).querySelector('.view-switch')).not.toBeNull();
    // Trece enlaces a la vez era justo lo que obligaba a la segunda banda.
    const header = (fixture.nativeElement as HTMLElement).querySelector('.app-bar');
    expect(header?.classList).not.toContain('app-bar--stacked');
  });

  it('cambia el menú al conmutar de vista', () => {
    const fixture = renderAsRoles(['EMPLOYEE', 'TENANT_ADMIN']);

    const options = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>(
      '.view-switch__option'
    );
    const employeeOption = Array.from(options).find(
      (option) => option.textContent?.trim() === 'Empleado'
    );
    employeeOption?.click();
    fixture.detectChanges();

    const titles = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.app-nav__title')
    ).map((title) => title.textContent?.trim());
    expect(titles).toEqual(['Mi tiempo', 'Cuenta']);
  });

  it('should redirect unauthenticated navigation to login', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/employee-dashboard');
    expect(router.url).toBe('/auth/login');
  });
});

function renderAsRoles(roles: string[]): ComponentFixture<AppComponent> {
  const authService = TestBed.inject(AuthService);
  spyOn(authService, 'isAuthenticated').and.returnValue(true);
  spyOn(authService, 'hasRole').and.callFake((role: string) => roles.includes(role));
  spyOn(authService, 'currentRoles').and.returnValue(roles);
  spyOn(authService, 'currentUserId').and.returnValue('user-1');

  const fixture = TestBed.createComponent(AppComponent);
  fixture.detectChanges();
  return fixture;
}
