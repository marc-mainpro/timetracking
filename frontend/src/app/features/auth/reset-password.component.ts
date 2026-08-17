import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { AuthService } from '../../core/services/auth.service';
import { AuthShellComponent } from '../../shared/auth-shell/auth-shell.component';

type ResetState = 'form' | 'missing-token' | 'done';

/** Mínimo que exige el backend en `PasswordResetRequest` (`@Size(min = 10)`). */
const MIN_PASSWORD_LENGTH = 10;

/** Marca el grupo cuando la confirmación no coincide con la contraseña nueva. */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const newPassword = group.get('newPassword')?.value;
  const confirmation = group.get('confirmation')?.value;
  return newPassword && confirmation && newPassword !== confirmation ? { mismatch: true } : null;
}

/**
 * Pantalla que abre el enlace del correo de recuperación (RF-USR-006). La ruta
 * la fija el backend en `auth.password-reset.reset-url-template`.
 *
 * <p>A diferencia de la verificación de alta, aquí el token no se consume al
 * cargar: se guarda y se envía junto a la contraseña nueva, porque el token es
 * de un solo uso y gastarlo antes de que el usuario escriba nada dejaría el
 * enlace inservible.
 */
@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, AuthShellComponent],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss'
})
export class ResetPasswordComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly errorMessagesService = inject(ErrorMessagesService);

  private readonly token = this.route.snapshot.queryParamMap.get('token');

  readonly minLength = MIN_PASSWORD_LENGTH;
  readonly state = signal<ResetState>(this.token ? 'form' : 'missing-token');
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH)]],
      confirmation: ['', [Validators.required]]
    },
    { validators: passwordsMatch }
  );

  readonly passwordVisible = signal(false);

  togglePassword(): void {
    this.passwordVisible.update((visible) => !visible);
  }

  // Métodos y no `computed`: `invalid`/`touched` de los controles no son
  // señales, así que un computed se quedaría cacheado en su primer valor.
  showPasswordError(): boolean {
    return this.form.controls.newPassword.invalid && this.form.controls.newPassword.touched;
  }

  showConfirmationError(): boolean {
    return this.form.controls.confirmation.invalid && this.form.controls.confirmation.touched;
  }

  showMismatch(): boolean {
    return !!this.form.errors?.['mismatch'] && this.form.controls.confirmation.touched;
  }

  submit(): void {
    if (!this.token || this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.resetPassword(this.token, this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.loading.set(false);
        this.state.set('done');
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }
}
