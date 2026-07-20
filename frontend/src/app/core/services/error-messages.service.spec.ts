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
