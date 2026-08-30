import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { RuntimeConfigService } from '../../core/config/runtime-config.service';

@Component({
  selector: 'rf-login-page',
  imports: [ReactiveFormsModule],
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
  readonly submitError = signal<UiError | null>(null);
  readonly sessionMessage = this.readSessionMessage();

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

  submit(): void {
    this.submitError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      globalThis.queueMicrotask(() =>
        this.host.nativeElement
          .querySelector<HTMLElement>('[aria-invalid="true"]')
          ?.focus(),
      );
      return;
    }

    this.submitting.set(true);
    const { email, password } = this.form.getRawValue();

    this.session
      .login({ email: email.trim().toLowerCase(), password })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl(this.safeReturnUrl()),
        error: (error: unknown) =>
          this.submitError.set(
            this.apiErrors.toUiError(
              error,
              'No se pudo iniciar sesión. Revisa tus credenciales.',
            ),
          ),
      });
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
