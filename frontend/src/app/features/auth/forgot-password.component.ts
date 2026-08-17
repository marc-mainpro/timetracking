import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { AuthService } from '../../core/services/auth.service';
import { AuthShellComponent } from '../../shared/auth-shell/auth-shell.component';

/**
 * Pantalla de solicitud de recuperación de contraseña (RF-USR-006).
 *
 * <p>El backend responde 202 con el mismo mensaje exista o no la cuenta
 * (anti-enumeración, RS-007). Esta pantalla debe conservar esa propiedad: nunca
 * debe mostrar un texto distinto según lo que devuelva la API, ni deshabilitar
 * el envío para correos "desconocidos" —no hay forma de saberlo desde aquí—.
 */
@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, AuthShellComponent],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss'
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly errorMessagesService = inject(ErrorMessagesService);

  readonly loading = signal(false);
  readonly submitted = signal(false);
  readonly message = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  /** Método y no `computed`: `invalid`/`touched` de los controles no son señales. */
  showEmailError(): boolean {
    return this.form.controls.email.invalid && this.form.controls.email.touched;
  }

  submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.requestPasswordReset(this.form.getRawValue().email).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.submitted.set(true);
        this.message.set(response.message);
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }

  startOver(): void {
    this.submitted.set(false);
    this.message.set(null);
    this.errorMessage.set(null);
    this.form.reset({ email: '' });
  }
}
