import { HttpClient, HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import {
  catchError,
  defer,
  finalize,
  firstValueFrom,
  from,
  map,
  Observable,
  of,
  shareReplay,
  switchMap,
  tap,
  timeout,
  throwError,
} from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from './auth-http-context';
import { CrossTabLockService } from './cross-tab-lock.service';
import {
  LoginRequest,
  LoginResponse,
  EmailOtpChallenge,
  SessionStatus,
  SessionUser,
  UserRole,
} from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfig = inject(RuntimeConfigService);
  private readonly crossTabLock = inject(CrossTabLockService);

  private readonly accessTokenState = signal<string | null>(null);
  private readonly userState = signal<SessionUser | null>(null);
  private readonly statusState = signal<SessionStatus>('unknown');
  private readonly emailOtpChallengeState = signal<EmailOtpChallenge | null>(null);
  private refreshRequest$: Observable<string> | undefined;

  readonly accessToken = this.accessTokenState.asReadonly();
  readonly user = this.userState.asReadonly();
  readonly status = this.statusState.asReadonly();
  readonly emailOtpChallenge = this.emailOtpChallengeState.asReadonly();
  readonly isAuthenticated = computed(
    () =>
      this.statusState() === 'authenticated' &&
      this.accessTokenState() !== null &&
      this.userState() !== null,
  );

  login(credentials: LoginRequest): Observable<'authenticated' | 'verification-required'> {
    return this.http
      .post<LoginResponse>(
        this.runtimeConfig.apiUrl('/auth/login'),
        credentials,
        { withCredentials: true },
      )
      .pipe(
        switchMap((response) => {
          if (response.emailVerificationRequired === true && response.verification) {
            this.accessTokenState.set(null);
            this.userState.set(null);
            this.statusState.set('anonymous');
            this.emailOtpChallengeState.set(response.verification);
            return of<'verification-required'>('verification-required');
          }
          this.emailOtpChallengeState.set(null);
          this.applySession(response);
          return this.loadCurrentUser().pipe(map(() => 'authenticated' as const));
        }),
        catchError((error: unknown) => {
          this.clearSession();
          return throwError(() => error);
        }),
      );
  }

  verifyEmailOtp(code: string): Observable<void> {
    const challenge = this.emailOtpChallengeState();
    if (!challenge) {
      return throwError(() => new Error('No hay una verificación de correo pendiente.'));
    }
    return this.http
      .post<LoginResponse>(
        this.runtimeConfig.apiUrl('/auth/otp/verify'),
        { verificationToken: challenge.verificationToken, code },
        { withCredentials: true, context: new HttpContext().set(SKIP_AUTH_REFRESH, true) },
      )
      .pipe(
        tap((response) => {
          this.emailOtpChallengeState.set(null);
          this.applySession(response);
        }),
        switchMap(() => this.loadCurrentUser()),
        map(() => undefined),
      );
  }

  resendEmailOtp(): Observable<EmailOtpChallenge> {
    const challenge = this.emailOtpChallengeState();
    if (!challenge) {
      return throwError(() => new Error('No hay una verificación de correo pendiente.'));
    }
    return this.http
      .post<EmailOtpChallenge>(
        this.runtimeConfig.apiUrl('/auth/otp/resend'),
        { verificationToken: challenge.verificationToken },
        { withCredentials: true, context: new HttpContext().set(SKIP_AUTH_REFRESH, true) },
      )
      .pipe(tap((updated) => this.emailOtpChallengeState.set(updated)));
  }

  cancelEmailOtp(): void {
    this.emailOtpChallengeState.set(null);
    this.clearSession();
  }

  ensureSession(): Observable<boolean> {
    if (this.isAuthenticated()) {
      return of(true);
    }

    if (this.statusState() === 'anonymous') {
      return of(false);
    }

    return this.refreshAccessToken().pipe(
      switchMap(() => this.loadCurrentUser()),
      map(() => true),
      catchError(() => {
        this.clearSession();
        return of(false);
      }),
    );
  }

  loadCurrentUser(): Observable<SessionUser> {
    return this.http
      .get<SessionUser>(this.runtimeConfig.apiUrl('/auth/me'), {
        withCredentials: true,
        context: new HttpContext().set(SKIP_AUTH_REFRESH, true),
      })
      .pipe(tap((user) => this.userState.set(user)));
  }

  refreshAccessToken(): Observable<string> {
    if (this.refreshRequest$ !== undefined) {
      return this.refreshRequest$;
    }

    const request$ = defer(() =>
      from(
        this.crossTabLock.runExclusive('ruta-fija-auth-refresh', () =>
          firstValueFrom(
            this.http
              .post<LoginResponse>(
                this.runtimeConfig.apiUrl('/auth/refresh'),
                {},
                {
                  withCredentials: true,
                  context: new HttpContext().set(SKIP_AUTH_REFRESH, true),
                },
              )
              .pipe(timeout({ first: 15_000 })),
          ),
        ),
      ),
    )
      .pipe(
        tap((response) => this.applySession(response)),
        map((response) => {
          if (!response.accessToken) {
            throw new Error('La respuesta de renovación no contiene un token de acceso.');
          }
          return response.accessToken;
        }),
        catchError((error: unknown) => {
          this.clearSession();
          return throwError(() => error);
        }),
        finalize(() => {
          this.refreshRequest$ = undefined;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );

    this.refreshRequest$ = request$;
    return request$;
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(
        this.runtimeConfig.apiUrl('/auth/logout'),
        {},
        {
          withCredentials: true,
          context: new HttpContext().set(SKIP_AUTH_REFRESH, true),
        },
      )
      .pipe(finalize(() => this.clearSession()));
  }

  hasAnyRole(allowedRoles: readonly UserRole[]): boolean {
    const currentRole = this.userState()?.role;
    return currentRole !== undefined && allowedRoles.includes(currentRole);
  }

  clearSession(): void {
    this.emailOtpChallengeState.set(null);
    this.accessTokenState.set(null);
    this.userState.set(null);
    this.statusState.set('anonymous');
  }

  private applySession(response: LoginResponse): void {
    if (!response.accessToken || !response.user) {
      throw new Error('La respuesta del servidor no contiene una sesión autenticada.');
    }
    this.accessTokenState.set(response.accessToken);
    this.userState.set(response.user);
    this.statusState.set('authenticated');
  }
}
