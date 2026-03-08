import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { extractValidationErrors, getFieldError, toUserErrorMessage } from '../../core/utils/error-message.util';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {
  readonly form = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    phoneNumber: ['', [Validators.required, Validators.maxLength(20), Validators.pattern(/^[0-9+\-\s()]{7,20}$/)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]]
  });

  isSubmitting = false;
  errorMessage = '';
  serverValidationErrors: Record<string, string> = {};

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  submit(): void {
    if (this.form.invalid || this.isSubmitting) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage = '';
    this.serverValidationErrors = {};
    this.isSubmitting = true;
    const values = this.form.getRawValue();
    const payload = {
      username: values.username ?? '',
      email: values.email ?? '',
      phoneNumber: (values.phoneNumber ?? '').trim(),
      password: values.password ?? '',
      firstName: values.firstName ?? '',
      lastName: values.lastName ?? ''
    };

    this.authService.register(payload).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.router.navigateByUrl('/dashboard');
      },
      error: (error) => {
        this.isSubmitting = false;
        this.serverValidationErrors = extractValidationErrors(error);
        this.errorMessage = toUserErrorMessage(
          error,
          'Account registration could not be completed.',
          'Please review the registration details and enter valid values.'
        );
      }
    });
  }

  fieldError(field: string, label: string): string {
    return getFieldError(this.form, field, label, this.serverValidationErrors);
  }
}
