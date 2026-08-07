import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { RegistrationService } from './registration.service';

describe('RegistrationService', () => {
  let service: RegistrationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(RegistrationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('posts the registration request to the public endpoint', () => {
    service
      .request({
        companyName: 'Acme',
        timezone: 'Europe/Madrid',
        firstName: 'Ana',
        lastName: 'Ruiz',
        email: 'ana@acme.test',
        password: 'supersecretpwd',
        acceptTerms: true
      })
      .subscribe();

    const req = httpMock.expectOne('/api/v1/public/tenant-registrations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.companyName).toBe('Acme');
    expect(req.request.body.acceptTerms).toBeTrue();
    req.flush({ message: 'ok' });
  });

  it('posts the token to the verification endpoint', () => {
    service.verifyEmail('un-token').subscribe();

    const req = httpMock.expectOne('/api/v1/public/tenant-registrations/verify-email');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'un-token' });
    req.flush({ message: 'ok' });
  });

  it('posts the email to the resend endpoint', () => {
    service.resendVerification('ana@acme.test').subscribe();

    const req = httpMock.expectOne('/api/v1/public/tenant-registrations/resend-verification');
    expect(req.request.body).toEqual({ email: 'ana@acme.test' });
    req.flush({ message: 'ok' });
  });
});
