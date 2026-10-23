import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { RuntimeConfigService } from '../../core/config/runtime-config.service';

@Component({
  selector: 'rf-login-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly session = inject(AuthSessionService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  readonly runtimeConfig = inject(RuntimeConfigService);
  readonly submitting = signal(false);
  readonly passwordVisible = signal(false);
  readonly awaitingEmailVerification = signal(false);
  readonly verificationMessage = signal<string | null>(null);
  readonly submitError = signal<UiError | null>(null);
  readonly sessionMessage = this.readSessionMessage();
  readonly otpExpiryLabel = computed(() => {
    const expiresAt = this.session.emailOtpChallenge()?.otpExpiresAt;
    if (!expiresAt) return null;
    const date = new Date(expiresAt);
    if (Number.isNaN(date.getTime())) return null;
    return new Intl.DateTimeFormat('es-PE', {
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  });

  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(180)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
  });

  readonly otpForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });

  submit(): void {
    if (this.awaitingEmailVerification()) {
      this.verifyEmailOtp();
      return;
    }
    this.submitError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // These inputs already exist. Their ARIA attributes update on the next
      // render, so select the first invalid control from the current model.
      const firstInvalid = this.form.controls.email.invalid ? 'email' : 'password';
      this.host.nativeElement.querySelector<HTMLInputElement>(`#${firstInvalid}`)?.focus();
      return;
    }

    this.submitting.set(true);
    const { email, password } = this.form.getRawValue();

    this.session
      .login({ email: email.trim().toLowerCase(), password })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (result) => {
          if (result === 'verification-required') {
            this.awaitingEmailVerification.set(true);
            this.form.controls.password.setValue('');
            this.verificationMessage.set(
              'Tu correo todavía no está verificado. Ingresa el código de seis dígitos recibido por correo.',
            );
            return;
          }
          void this.router.navigateByUrl(this.safeReturnUrl());
        },
        error: (error: unknown) =>
          this.submitError.set(
            this.apiErrors.toUiError(
              error,
              'No se pudo iniciar sesión. Revisa tus credenciales.',
            ),
          ),
      });
  }

  verifyEmailOtp(): void {
    this.submitError.set(null);
    if (this.otpForm.invalid) {
      this.otpForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.session
      .verifyEmailOtp(this.otpForm.controls.code.value)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl(this.safeReturnUrl()),
        error: (error: unknown) =>
          this.submitError.set(
            this.apiErrors.toUiError(error, 'No se pudo verificar el correo.'),
          ),
      });
  }

  resendEmailOtp(): void {
    this.submitError.set(null);
    this.submitting.set(true);
    this.session
      .resendEmailOtp()
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (challenge) => {
          this.verificationMessage.set(
            challenge.emailSent
              ? 'Enviamos un nuevo código. El anterior dejó de ser válido.'
              : 'El código actual sigue vigente. Revisa tu correo.',
          );
          this.otpForm.controls.code.reset('');
        },
        error: (error: unknown) =>
          this.submitError.set(
            this.apiErrors.toUiError(error, 'No se pudo reenviar el código.'),
          ),
      });
  }

  cancelEmailOtp(): void {
    this.session.cancelEmailOtp();
    this.awaitingEmailVerification.set(false);
    this.verificationMessage.set(null);
    this.otpForm.reset({ code: '' });
  }

  togglePasswordVisibility(): void {
    this.passwordVisible.update((visible) => !visible);
  }

  private safeReturnUrl(): string {
    const candidate = this.route.snapshot.queryParamMap.get('returnUrl');
    return candidate !== null &&
      candidate.startsWith('/') &&
      !candidate.startsWith('//')
      ? candidate
      : '/dashboard';
  }

  private readSessionMessage(): string | null {
    switch (this.route.snapshot.queryParamMap.get('reason')) {
      case 'session-expired':
        return 'Tu sesión venció. Ingresa nuevamente para continuar.';
      case 'logout-unconfirmed':
        return 'La sesión se cerró en este navegador, pero el servidor no pudo confirmar la revocación. Inténtalo de nuevo cuando recupere conexión.';
      default:
        return null;
    }
  }
}
