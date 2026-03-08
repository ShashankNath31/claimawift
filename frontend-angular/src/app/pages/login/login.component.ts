import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { extractValidationErrors, getFieldError, toUserErrorMessage } from '../../core/utils/error-message.util';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  readonly credentialsForm = this.fb.group({
    usernameOrEmail: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  readonly otpForm = this.fb.group({
    otp: ['', [Validators.required, Validators.pattern(/^[0-9]{6}$/)]]
  });

  isSubmitting = false;
  awaitingOtp = false;
  errorMessage = '';
  challengeId = '';
  maskedEmail = '';
  otpExpiresInSeconds = 0;
  serverValidationErrors: Record<string, string> = {};
  private credentialsSnapshot: { usernameOrEmail: string; password: string } | null = null;

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  submit(): void {
    if (this.awaitingOtp) {
      this.verifyOtp();
      return;
    }
    this.requestOtp();
  }

  resendOtp(): void {
    if (!this.credentialsSnapshot || this.isSubmitting) {
      return;
    }
    this.requestOtp(this.credentialsSnapshot);
  }

  backToCredentials(): void {
    this.awaitingOtp = false;
    this.challengeId = '';
    this.maskedEmail = '';
    this.otpExpiresInSeconds = 0;
    this.otpForm.reset({ otp: '' });
    this.errorMessage = '';
    this.serverValidationErrors = {};
  }

  credentialsFieldError(field: string, label: string): string {
    return getFieldError(this.credentialsForm, field, label, this.serverValidationErrors);
  }

  otpFieldError(field: string, label: string): string {
    return getFieldError(this.otpForm, field, label, this.serverValidationErrors);
  }

  private requestOtp(existingCredentials?: { usernameOrEmail: string; password: string }): void {
    if (this.isSubmitting) {
      return;
    }

    if (!existingCredentials) {
      if (this.credentialsForm.invalid) {
        this.credentialsForm.markAllAsTouched();
        return;
      }
      this.credentialsSnapshot = this.credentialsForm.getRawValue() as { usernameOrEmail: string; password: string };
    }

    const payload = existingCredentials ?? this.credentialsSnapshot;
    if (!payload) {
      return;
    }

    this.errorMessage = '';
    this.serverValidationErrors = {};
    this.isSubmitting = true;

    this.authService.login(payload).subscribe({
      next: (response) => {
        this.challengeId = response.data.challengeId;
        this.maskedEmail = response.data.maskedEmail;
        this.otpExpiresInSeconds = response.data.expiresInSeconds;
        this.awaitingOtp = true;
        this.otpForm.reset({ otp: '' });
        this.isSubmitting = false;
      },
      error: (error) => {
        this.isSubmitting = false;
        this.serverValidationErrors = extractValidationErrors(error);
        this.errorMessage = toUserErrorMessage(
          error,
          'Sign-in failed. Verify your credentials and try again.',
          'Enter a valid username or email and password.'
        );
      }
    });
  }

  private verifyOtp(): void {
    if (this.otpForm.invalid || this.isSubmitting || !this.challengeId) {
      this.otpForm.markAllAsTouched();
      return;
    }

    this.errorMessage = '';
    this.serverValidationErrors = {};
    this.isSubmitting = true;

    this.authService
      .verifyLoginOtp({
        challengeId: this.challengeId,
        otp: (this.otpForm.getRawValue().otp ?? '').trim()
      })
      .subscribe({
        next: () => {
          this.isSubmitting = false;
          this.router.navigateByUrl('/dashboard');
        },
        error: (error) => {
          this.isSubmitting = false;
          this.serverValidationErrors = extractValidationErrors(error);
          this.errorMessage = toUserErrorMessage(
            error,
            'OTP verification failed. Please try again.',
            'Enter the 6-digit OTP sent to your registered email.'
          );
        }
      });
  }
}
