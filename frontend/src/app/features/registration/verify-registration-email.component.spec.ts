import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { VerifyRegistrationEmailComponent } from './verify-registration-email.component';

describe('VerifyRegistrationEmailComponent', () => {
  let httpMock: HttpTestingController;

  async function createComponent(
    queryParams: Record<string, string>
  ): Promise<ComponentFixture<VerifyRegistrationEmailComponent>> {
    await TestBed.configureTestingModule({
      imports: [VerifyRegistrationEmailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } }
        }
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(VerifyRegistrationEmailComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('verifies the token from the query string as soon as it loads', async () => {
    const fixture = await createComponent({ token: 'un-token' });

    const request = httpMock.expectOne('/api/v1/public/tenant-registrations/verify-email');
    expect(request.request.body).toEqual({ token: 'un-token' });
    request.flush({ message: 'Correo confirmado. Tu solicitud está pendiente de revisión.' });

    expect(fixture.componentInstance.state()).toBe('verified');
    expect(fixture.componentInstance.message()).toContain('pendiente de revisión');
  });

  it('reports an expired or already used link', async () => {
    const fixture = await createComponent({ token: 'gastado' });

    httpMock
      .expectOne('/api/v1/public/tenant-registrations/verify-email')
      .flush(
        { errorCode: 'INVALID_VERIFICATION_TOKEN', detail: 'El enlace de verificación no es válido o ha caducado' },
        { status: 409, statusText: 'Conflict' }
      );

    expect(fixture.componentInstance.state()).toBe('failed');
    expect(fixture.componentInstance.error()).toContain('no es válido');
  });

  it('does not call the API when the link carries no token', async () => {
    const fixture = await createComponent({});

    httpMock.expectNone('/api/v1/public/tenant-registrations/verify-email');
    expect(fixture.componentInstance.state()).toBe('missing-token');
  });

  it('resends a verification email and shows the neutral message', async () => {
    const fixture = await createComponent({});
    fixture.componentInstance.resendForm.setValue({ email: 'ana@acme.test' });

    fixture.componentInstance.resend();

    const request = httpMock.expectOne('/api/v1/public/tenant-registrations/resend-verification');
    expect(request.request.body).toEqual({ email: 'ana@acme.test' });
    request.flush({ message: 'Si los datos son correctos, recibirás un correo para confirmar tu dirección.' });

    expect(fixture.componentInstance.resendMessage()).toContain('Si los datos son correctos');
  });

  it('does not resend with an invalid email', async () => {
    const fixture = await createComponent({});
    fixture.componentInstance.resendForm.setValue({ email: 'no-arroba' });

    fixture.componentInstance.resend();

    httpMock.expectNone('/api/v1/public/tenant-registrations/resend-verification');
    expect(fixture.componentInstance.resendForm.touched).toBeTrue();
  });

  it('shows a translated error if the resend fails', async () => {
    const fixture = await createComponent({});
    fixture.componentInstance.resendForm.setValue({ email: 'ana@acme.test' });
    fixture.componentInstance.resend();

    httpMock
      .expectOne('/api/v1/public/tenant-registrations/resend-verification')
      .flush({ errorCode: 'RATE_LIMIT_EXCEEDED' }, { status: 429, statusText: 'Too Many Requests' });

    expect(fixture.componentInstance.resendMessage()).toContain('demasiados intentos');
  });
});
