import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { AuthService } from '../../core/services/auth.service';
import { AuthShellComponent } from '../../shared/auth-shell/auth-shell.component';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, AuthShellComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly passwordVisible = signal(false);
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  // Métodos y no `computed`: `invalid`/`touched` de los controles no son
  // señales, así que un computed se quedaría cacheado en su primer valor.
  // Centralizan la condición para que el mensaje y los `aria-*` del campo
  // aparezcan siempre a la par.
  showEmailError(): boolean {
    return this.form.controls.email.invalid && this.form.controls.email.touched;
  }

  showPasswordError(): boolean {
    return this.form.controls.password.invalid && this.form.controls.password.touched;
  }

  togglePassword(): void {
    this.passwordVisible.update((visible) => !visible);
  }

  submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.loading.set(false);
        const landingRoute = this.authService.hasRole('TENANT_ADMIN')
          ? '/admin/employees'
          : '/employee-dashboard';
        void this.router.navigate([landingRoute]);
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(this.errorMessagesService.fromProblem(error.error));
      }
    });
  }
}
