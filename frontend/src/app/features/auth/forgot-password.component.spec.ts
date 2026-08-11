import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { ForgotPasswordComponent } from './forgot-password.component';

const NEUTRAL_MESSAGE =
  'Si la cuenta existe y esta operativa, recibiras instrucciones para restablecer la contrasena.';

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPasswordComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('no llama a la API con un correo inválido', () => {
    component.form.setValue({ email: 'no-arroba' });

    component.submit();

    httpMock.expectNone('/api/v1/auth/password/forgot');
    expect(component.form.touched).toBeTrue();
  });

  it('envía el correo y muestra el mensaje del backend', () => {
    submit('ana@empresa.test');

    const request = httpMock.expectOne('/api/v1/auth/password/forgot');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'ana@empresa.test' });
    request.flush({ message: NEUTRAL_MESSAGE }, { status: 202, statusText: 'Accepted' });

    expect(component.submitted()).toBeTrue();
    expect(component.message()).toBe(NEUTRAL_MESSAGE);
  });

  it('muestra la misma confirmación para una cuenta que no existe (RS-007)', () => {
    submit('nadie@empresa.test');

    httpMock
      .expectOne('/api/v1/auth/password/forgot')
      .flush({ message: NEUTRAL_MESSAGE }, { status: 202, statusText: 'Accepted' });

    expect(component.submitted()).toBeTrue();
    expect(component.message()).toBe(NEUTRAL_MESSAGE);
    expect(component.errorMessage()).toBeNull();
  });

  it('explica al usuario que ha superado el límite de intentos (RS-007)', () => {
    submit('ana@empresa.test');

    httpMock
      .expectOne('/api/v1/auth/password/forgot')
      .flush({ errorCode: 'RATE_LIMIT_EXCEEDED' }, { status: 429, statusText: 'Too Many Requests' });

    expect(component.errorMessage()).toContain('demasiados intentos');
    expect(component.submitted()).toBeFalse();
  });

  it('permite volver al formulario para usar otro correo', () => {
    submit('ana@empresa.test');
    httpMock
      .expectOne('/api/v1/auth/password/forgot')
      .flush({ message: NEUTRAL_MESSAGE }, { status: 202, statusText: 'Accepted' });

    component.startOver();

    expect(component.submitted()).toBeFalse();
    expect(component.form.getRawValue().email).toBe('');
  });

  function submit(email: string): void {
    component.form.setValue({ email });
    component.submit();
  }
});
