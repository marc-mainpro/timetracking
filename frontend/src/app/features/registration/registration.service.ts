import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface TenantRegistrationPayload {
  companyName: string;
  timezone: string;
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  acceptTerms: boolean;
}

/**
 * Respuesta genérica del alta pública. Deliberadamente no trae identificadores
 * ni estado: el backend responde lo mismo tanto si crea la solicitud como si la
 * descarta, para no revelar qué correos existen (RF-REG-005).
 */
export interface RegistrationAccepted {
  message: string;
}

@Injectable({ providedIn: 'root' })
export class RegistrationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/public/tenant-registrations';

  request(payload: TenantRegistrationPayload): Observable<RegistrationAccepted> {
    return this.http.post<RegistrationAccepted>(this.baseUrl, payload);
  }

  verifyEmail(token: string): Observable<RegistrationAccepted> {
    return this.http.post<RegistrationAccepted>(`${this.baseUrl}/verify-email`, { token });
  }

  resendVerification(email: string): Observable<RegistrationAccepted> {
    return this.http.post<RegistrationAccepted>(`${this.baseUrl}/resend-verification`, { email });
  }
}
