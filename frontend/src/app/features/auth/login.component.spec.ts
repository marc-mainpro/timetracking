import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('enruta a un administrador (TENANT_ADMIN) a /admin/employees tras el login', () => {
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);

    submitWithToken(sampleToken(['TENANT_ADMIN']));

    expect(navigate).toHaveBeenCalledOnceWith(['/admin/employees']);
  });

  it('enruta a un empleado (EMPLOYEE) a /employee-dashboard tras el login', () => {
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);

    submitWithToken(sampleToken(['EMPLOYEE']));

    expect(navigate).toHaveBeenCalledOnceWith(['/employee-dashboard']);
  });

  function submitWithToken(accessToken: string): void {
    component.form.setValue({ email: 'admin@acme.test', password: 'supersecretpwd' });
    component.submit();

    const request = httpMock.expectOne('/api/v1/auth/login');
    request.flush({ accessToken, expiresAt: new Date(Date.now() + 3_600_000).toISOString() });
  }
});

function sampleToken(roles: string[]): string {
  const payload = btoa(
    JSON.stringify({ sub: 'user-id', tenantId: 'tenant-id', roles, exp: Math.floor(Date.now() / 1000) + 3600 })
  )
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${payload}.signature`;
}
