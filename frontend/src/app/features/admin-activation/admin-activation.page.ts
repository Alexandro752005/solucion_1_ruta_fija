import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnDestroy, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, Observable, Subscription } from 'rxjs';

import { AdminActivationApiService } from '../../core/admin-activation/admin-activation-api.service';
import { AdminActivationOtp, AdminActivationTerms } from '../../core/admin-activation/admin-activation.models';

@Component({
  selector: 'rf-admin-activation-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './admin-activation.page.html',
  styleUrl: './admin-activation.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminActivationPage implements OnDestroy {
  private readonly api = inject(AdminActivationApiService);
  private readonly destroyRef = inject(DestroyRef);
  private token: string | null = null;
  private expiresAt = 0;
  private expiryTimer?: ReturnType<typeof setTimeout>;
  private verificationToken: string | null = null;
  private operation?: Subscription;

  readonly claimed = signal(false);
  readonly verified = signal(false);
  readonly accepted = signal(false);
  readonly completed = signal(false);
  readonly expired = signal(false);
  readonly replacesAdmin = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly otp = signal<AdminActivationOtp | null>(null);
  readonly terms = signal<AdminActivationTerms | null>(null);

  readonly claimForm = new FormGroup({
    credential: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(300)] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(12), Validators.maxLength(128)] }),
    confirmation: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  readonly otpForm = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{6}$/)] }),
  });
  readonly termsForm = new FormGroup({
    accepted: new FormControl(false, { nonNullable: true, validators: [Validators.requiredTrue] }),
  });

  claim(): void {
    if (this.busy() || this.claimed()) return;
    if (this.claimForm.invalid) {
      this.claimForm.markAllAsTouched();
      this.error.set('Ingresa la credencial y una contraseña de 12 a 128 caracteres.'); return;
    }
    const { credential, password, confirmation } = this.claimForm.getRawValue();
    if (password !== confirmation) {
      this.error.set('Las contraseñas no coinciden.'); return;
    }
    // Secrets are cleared before any response, including connection/provider failures.
    this.claimForm.reset();
    this.run(this.api.claim(credential.trim(), password), (result) => {
      this.token = result.activationToken;
      this.expiresAt = Date.parse(result.expiresAt);
      if (!Number.isFinite(this.expiresAt) || this.expiresAt <= Date.now()) {
        this.expire(); return;
      }
      this.claimed.set(true);
      this.expired.set(false);
      this.expiryTimer = setTimeout(() => this.expire(), Math.min(this.expiresAt - Date.now(), 2_147_483_647));
      this.notice.set('Contraseña registrada. Verifica el correo antes de continuar. Esta activación no inicia sesión.');
    });
  }

  beginOtp(): void {
    const token = this.activeToken();
    if (!token || this.verified()) return;
    this.run(this.api.beginOtp(token), (ticket) => this.useOtp(ticket));
  }

  resendOtp(): void {
    const token = this.activeToken();
    if (!token || !this.verificationToken || this.verified()) return;
    this.run(this.api.resendOtp(token, this.verificationToken), (ticket) => this.useOtp(ticket));
  }

  verifyOtp(): void {
    const token = this.activeToken();
    if (!token || !this.verificationToken || this.verified()) return;
    if (this.otpForm.invalid) {
      this.otpForm.markAllAsTouched(); this.error.set('Ingresa exactamente seis dígitos.'); return;
    }
    const code = this.otpForm.controls.code.value;
    this.otpForm.reset();
    this.run(this.api.verifyOtp(token, this.verificationToken, code), (status) => {
      this.verified.set(status.emailVerified);
      this.replacesAdmin.set(status.replacesAdmin ?? false);
      if (status.emailVerified) {
        this.verificationToken = null;
        this.otp.set(null);
        this.notice.set('Correo verificado. Consulta y acepta el documento vigente para continuar.');
      }
    });
  }

  loadTerms(): void {
    const token = this.activeToken();
    if (!token || !this.verified()) return;
    this.termsForm.reset();
    this.terms.set(null);
    this.accepted.set(false);
    this.run(this.api.terms(token), (document) => {
      this.terms.set(document);
      this.accepted.set(document.accepted);
    }, 'terms');
  }

  acceptTerms(): void {
    const token = this.activeToken();
    const document = this.terms();
    if (!token || !document || !this.verified() || this.accepted()) return;
    if (this.termsForm.invalid) {
      this.termsForm.markAllAsTouched(); this.error.set('Marca la aceptación expresa después de leer el documento.'); return;
    }
    this.run(this.api.acceptTerms(token, document), () => {
      this.accepted.set(true);
      this.termsForm.reset();
      this.notice.set('Aceptación registrada por el servidor. Puedes completar la activación.');
    }, 'terms');
  }

  complete(): void {
    const token = this.activeToken();
    if (!token || !this.verified() || !this.accepted()) return;
    this.run(this.api.complete(token), (status) => {
      if (status.status === 'COMPLETED') {
        this.clearSecrets();
        this.terms.set(null);
        this.completed.set(true);
        this.notice.set('Activación completada. Vuelve al inicio de sesión para ingresar con tu contraseña.');
      } else {
        this.notice.set('Validaciones registradas. La cuenta sigue pendiente de habilitación por SUPER_ADMIN.');
      }
    }, 'terms');
  }

  ngOnDestroy(): void {
    this.clearSecrets();
    this.terms.set(null);
  }

  private useOtp(ticket: AdminActivationOtp): void {
    this.verificationToken = ticket.verificationToken;
    // Do not retain the verification credential in renderable state.
    this.otp.set({ ...ticket, verificationToken: '' });
    this.otpForm.reset();
    this.notice.set(ticket.emailSent
      ? 'Código enviado. Tiene una vigencia de cinco minutos. Revisa tu correo.'
      : 'El código vigente se conserva. Revisa el correo recibido.');
  }

  private activeToken(): string | null {
    if (this.busy() || this.completed()) return null;
    if (!this.token) return null;
    if (Date.now() >= this.expiresAt) { this.expire(); return null; }
    return this.token;
  }

  private run<T>(request: Observable<T>, next: (value: T) => void, context?: 'terms'): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.operation = request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false))).subscribe({
      next,
      error: (failure: unknown) => {
        const status = failure instanceof HttpErrorResponse ? failure.status : 0;
        if (status === 401 || status === 410) {
          // A 410 OTP challenge may expire independently of the activation token.
          if (status === 410 && context !== 'terms' && this.verificationToken) {
            this.otpForm.reset();
            this.error.set('El código o la credencial venció. Si la activación sigue vigente, solicita otro código.');
          } else this.expire();
          return;
        }
        if (context === 'terms' && (status === 409 || status === 404)) {
          this.terms.set(null); this.accepted.set(false); this.termsForm.reset();
          this.error.set(status === 404
            ? 'No hay términos aprobados disponibles. La cuenta permanece inactiva.'
            : 'La versión del documento cambió. Consulta el documento vigente y vuelve a revisarlo.');
          return;
        }
        this.error.set(({
          400: 'Revisa los datos. El código debe contener seis dígitos y la contraseña entre 12 y 128 caracteres.',
          403: 'La operación no está autorizada para esta activación.',
          404: 'La credencial no está disponible. Consulta con SUPER_ADMIN.',
          409: 'La operación no está permitida en el estado actual. Consulta con SUPER_ADMIN.',
          429: 'Se alcanzó un límite temporal. Espera antes de solicitar o verificar otro código.',
          502: 'El correo no pudo enviarse. No se reintentará automáticamente.',
          503: 'El proveedor de correo no está disponible. No se reintentará automáticamente.',
        } as Record<number, string>)[status] ?? 'No se confirmó la operación. No se reintentará automáticamente.');
      },
    });
  }

  private expire(): void {
    this.clearSecrets();
    this.expired.set(true);
    this.terms.set(null);
    this.termsForm.reset();
    this.error.set('La activación venció o dejó de ser válida. Solicita una nueva credencial a SUPER_ADMIN.');
  }

  private clearSecrets(): void {
    this.operation?.unsubscribe();
    this.operation = undefined;
    if (this.expiryTimer) clearTimeout(this.expiryTimer);
    this.expiryTimer = undefined;
    this.token = this.verificationToken = null;
    this.expiresAt = 0;
    this.claimForm.reset();
    this.otpForm.reset();
    this.otp.set(null);
  }
}
