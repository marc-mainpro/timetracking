import { Injectable } from '@angular/core';

type ProblemDetail = {
  detail?: string;
  errorCode?: string;
  errors?: Array<{ field: string; message: string }>;
};

@Injectable({ providedIn: 'root' })
export class ErrorMessagesService {
  private readonly messages = new Map<string, string>([
    ['INVALID_CREDENTIALS', 'Las credenciales no son válidas.'],
    ['USER_INACTIVE', 'Tu usuario está desactivado.'],
    ['TENANT_INACTIVE', 'Tu organización está inactiva.'],
    ['WORKDAY_ALREADY_OPEN', 'Ya tienes una jornada activa.'],
    ['WORKDAY_NOT_OPEN', 'No hay una jornada activa para realizar esta acción.'],
    ['BREAK_ALREADY_OPEN', 'Ya tienes una pausa iniciada.'],
    ['BREAK_NOT_OPEN', 'No hay una pausa activa que finalizar.'],
    ['WORKDAY_OPEN_BREAK', 'No puedes finalizar la jornada con una pausa abierta.'],
    ['RATE_LIMIT_EXCEEDED', 'Has realizado demasiados intentos. Espera un momento e inténtalo otra vez.'],
    ['LAST_ADMIN', 'La organización debe mantener al menos un administrador activo.'],
    ['EMAIL_ALREADY_IN_USE', 'Ese correo ya está en uso.'],
    ['CORRECTION_ALREADY_PENDING', 'Ya existe una corrección pendiente para esa jornada.'],
    ['CORRECTION_ALREADY_RESOLVED', 'La corrección ya fue resuelta por otro administrador.'],
    ['CONCURRENT_MODIFICATION', 'Otra persona modificó este recurso al mismo tiempo. Recargamos el estado más reciente.'],
    ['RESOURCE_NOT_FOUND', 'No se encontró el recurso solicitado.'],
    ['UNAUTHORIZED', 'Necesitas iniciar sesión para continuar.']
  ]);

  fromProblem(problem: ProblemDetail | null | undefined): string {
    if (!problem) {
      return 'Se produjo un error inesperado.';
    }

    if (problem.errorCode === 'VALIDATION_ERROR' && problem.errors?.length) {
      return problem.errors.map((error) => `${error.field}: ${error.message}`).join(' | ');
    }

    return this.messages.get(problem.errorCode ?? '') ?? problem.detail ?? 'Se produjo un error inesperado.';
  }
}
