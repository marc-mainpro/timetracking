import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { TenantRegistrationComponent } from './tenant-registration.component';

describe('TenantRegistrationComponent', () => {
  let component: TenantRegistrationComponent;
  let fixture: ComponentFixture<TenantRegistrationComponent>;
  let httpMock: HttpTestingController;

  const validForm = {
    companyName: 'Acme',
    timezone: 'Europe/Madrid',
    firstName: 'Ana',
    lastName: 'Ruiz',
    email: 'ana@acme.test',
    password: 'supersecretpwd',
    acceptTerms: true
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TenantRegistrationComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TenantRegistrationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('does not call the API until the form is valid', () => {
    component.submit();

    httpMock.expectNone('/api/v1/public/tenant-registrations');
    expect(component.form.touched).toBeTrue();
  });

  it('requires the terms checkbox', () => {
    component.form.setValue({ ...validForm, acceptTerms: false });

    expect(component.form.invalid).toBeTrue();
    component.submit();
    httpMock.expectNone('/api/v1/public/tenant-registrations');
  });

  it('shows the neutral backend message instead of claiming the account exists', () => {
    component.form.setValue(validForm);
    component.submit();

    const request = httpMock.expectOne('/api/v1/public/tenant-registrations');
    expect(request.request.method).toBe('POST');
    request.flush({ message: 'Si los datos son correctos, recibirás un correo para confirmar tu dirección.' });

    expect(component.submitted()).toBeTrue();
    expect(component.message()).toContain('Si los datos son correctos');
  });

  it('translates a backend error and stays on the form', () => {
    component.form.setValue(validForm);
    component.submit();

    httpMock
      .expectOne('/api/v1/public/tenant-registrations')
      .flush({ errorCode: 'FORBIDDEN', detail: 'Acceso denegado' }, { status: 403, statusText: 'Forbidden' });

    expect(component.submitted()).toBeFalse();
    expect(component.error()).toBe('Acceso denegado');
  });

  it('resets back to an empty form when starting over', () => {
    component.form.setValue(validForm);
    component.submit();
    httpMock.expectOne('/api/v1/public/tenant-registrations').flush({ message: 'ok' });

    component.startOver();

    expect(component.submitted()).toBeFalse();
    expect(component.form.getRawValue().companyName).toBe('');
    expect(component.form.getRawValue().acceptTerms).toBeFalse();
    expect(component.form.getRawValue().timezone).toBe('Europe/Madrid');
  });
});
