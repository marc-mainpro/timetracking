import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { RegistrationService } from './registration.service';

type VerificationState = 'verifying' | 'verified' | 'failed' | 'missing-token';

/**
 * Pantalla de verificación de correo (RF-REG-004). Es la que abre el enlace del
 * correo, así que toma el token de la query string y verifica en cuanto carga.
 *
 * <p>Si el token falta, ha caducado o ya se usó, ofrece reenviar el correo. El
 * reenvío responde siempre lo mismo aunque el correo no exista (RF-REG-005),
 * por lo que el mensaje de confirmación es deliberadamente neutro.
 */
@Component({
  selector: 'app-verify-registration-email',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './verify-registration-email.component.html',
  styleUrl: './verify-registration-email.component.scss'
})
export class VerifyRegistrationEmailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly registrationService = inject(RegistrationService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  readonly state = signal<VerificationState>('verifying');
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly resending = signal(false);
  readonly resendMessage = signal<string | null>(null);

  readonly resendForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  constructor() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('missing-token');
      return;
    }
    this.registrationService.verifyEmail(token).subscribe({
      next: (response) => {
        this.state.set('verified');
        this.message.set(response.message);
      },
      error: (err) => {
        this.state.set('failed');
        this.error.set(this.errorMessagesService.fromProblem(err.error));
      }
    });
  }

  resend(): void {
    if (this.resendForm.invalid || this.resending()) {
      this.resendForm.markAllAsTouched();
      return;
    }
    this.resending.set(true);
    this.resendMessage.set(null);
    this.registrationService.resendVerification(this.resendForm.getRawValue().email).subscribe({
      next: (response) => {
        this.resending.set(false);
        this.resendMessage.set(response.message);
      },
      error: (err) => {
        this.resending.set(false);
        this.resendMessage.set(this.errorMessagesService.fromProblem(err.error));
      }
    });
  }
}
