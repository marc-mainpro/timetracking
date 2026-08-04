import { TestBed } from '@angular/core/testing';

import { ErrorMessagesService } from './error-messages.service';

describe('ErrorMessagesService', () => {
  let service: ErrorMessagesService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ErrorMessagesService);
  });

  it('maps known error codes to friendly messages', () => {
    expect(service.fromProblem({ errorCode: 'INVALID_CREDENTIALS' })).toBe('Las credenciales no son válidas.');
  });

  it('explains an account locked by failed attempts (RS-008)', () => {
    expect(service.fromProblem({ errorCode: 'ACCOUNT_LOCKED' })).toBe(
      'Tu cuenta está bloqueada temporalmente por varios intentos fallidos. Vuelve a intentarlo dentro de unos minutos.'
    );
  });

  it('explains that the rate limit was exceeded (RS-007)', () => {
    expect(service.fromProblem({ errorCode: 'RATE_LIMIT_EXCEEDED' })).toBe(
      'Has realizado demasiados intentos. Espera un momento e inténtalo otra vez.'
    );
  });

  it('does not leak the backend detail when the code is known', () => {
    expect(service.fromProblem({ errorCode: 'ACCOUNT_LOCKED', detail: 'La cuenta esta bloqueada temporalmente' })).toBe(
      'Tu cuenta está bloqueada temporalmente por varios intentos fallidos. Vuelve a intentarlo dentro de unos minutos.'
    );
  });

  it('formats validation errors field by field', () => {
    expect(
      service.fromProblem({
        errorCode: 'VALIDATION_ERROR',
        errors: [
          { field: 'reason', message: 'must not be blank' },
          { field: 'resolutionComment', message: 'size must be between 0 and 500' }
        ]
      })
    ).toBe('reason: must not be blank | resolutionComment: size must be between 0 and 500');
  });

  it('falls back to backend detail and then generic text', () => {
    expect(service.fromProblem({ detail: 'Detalle del backend' })).toBe('Detalle del backend');
    expect(service.fromProblem(null)).toBe('Se produjo un error inesperado.');
  });
});
