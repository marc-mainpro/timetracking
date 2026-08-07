import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { RegistrationService } from './registration.service';

/**
 * Pantalla pública de solicitud de alta de organización (RF-REG-001/002).
 *
 * <p>Al enviar no se muestra «cuenta creada» sino el mensaje neutro que
 * devuelve el backend: el alta solo queda solicitada, y el mensaje es el mismo
 * aunque el correo ya tuviera cuenta (RF-REG-005). Comunicar aquí un éxito
 * rotundo sería mentir y, además, filtraría qué correos existen.
 */
@Component({
  selector: 'app-tenant-registration',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './tenant-registration.component.html',
  styleUrl: './tenant-registration.component.scss'
})
export class TenantRegistrationComponent {
  private readonly fb = inject(FormBuilder);
  private readonly registrationService = inject(RegistrationService);
  private readonly errorMessagesService = inject(ErrorMessagesService);

  readonly submitting = signal(false);
  readonly submitted = signal(false);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    companyName: ['', [Validators.required, Validators.maxLength(200)]],
    timezone: ['Europe/Madrid', [Validators.required]],
    firstName: ['', [Validators.required, Validators.maxLength(200)]],
    lastName: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(10)]],
    acceptTerms: [false, [Validators.requiredTrue]]
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.registrationService.request(this.form.getRawValue()).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.submitted.set(true);
        this.message.set(response.message);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(this.errorMessagesService.fromProblem(err.error));
      }
    });
  }

  startOver(): void {
    this.submitted.set(false);
    this.message.set(null);
    this.error.set(null);
    this.form.reset({
      companyName: '',
      timezone: 'Europe/Madrid',
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      acceptTerms: false
    });
  }
}
