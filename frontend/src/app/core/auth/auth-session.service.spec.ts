import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthSessionService } from './auth-session.service';
import { CrossTabLockService } from './cross-tab-lock.service';

class ImmediateCrossTabLock {
  runExclusive<T>(_name: string, operation: () => Promise<T>): Promise<T> {
    return operation();
  }
}

describe('AuthSessionService', () => {
  let service: AuthSessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CrossTabLockService, useClass: ImmediateCrossTabLock },
      ],
    });
    service = TestBed.inject(AuthSessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    try {
      http?.verify();
    } finally {
      TestBed.resetTestingModule();
      vi.restoreAllMocks();
    }
  });

  it('inicia sesión, consulta /auth/me y no persiste el token en Storage', async () => {
    const storageSpy = vi.spyOn(Storage.prototype, 'setItem');
    const result = firstValueFrom(
      service.login({
        email: 'admin@ruta-fija.pe',
        password: 'no-se-registra',
      }),
    );

    const loginRequest = http.expectOne('/api/v1/auth/login');
    expect(loginRequest.request.withCredentials).toBe(true);
    loginRequest.flush({
      accessToken: 'access-1',
      expiresIn: 900,
      user: {
        id: 'user-1',
        fullName: 'Admin Ruta Fija',
        role: 'ADMIN',
      },
    });

    const meRequest = http.expectOne('/api/v1/auth/me');
    meRequest.flush({
      id: 'user-1',
      fullName: 'Admin Ruta Fija',
      email: 'admin@ruta-fija.pe',
      role: 'ADMIN',
      organizationId: 'organization-1',
    });

    await expect(result).resolves.toBe('authenticated');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('access-1');
    expect(service.user()?.organizationId).toBe('organization-1');
    expect(storageSpy).not.toHaveBeenCalled();
  });

  it('mantiene el desafío OTP solo en memoria hasta que se verifica', async () => {
    const storageSpy = vi.spyOn(Storage.prototype, 'setItem');
    const loginResult = firstValueFrom(
      service.login({
        email: 'admin@ruta-fija.pe',
        password: 'credencial-de-prueba',
      }),
    );

    const loginRequest = http.expectOne('/api/v1/auth/login');
    loginRequest.flush({
      emailVerificationRequired: true,
      verification: {
        verificationToken: 'verification-token-in-memory',
        otpExpiresAt: '2026-10-14T17:05:00Z',
        challengeExpiresAt: '2026-10-14T17:30:00Z',
        emailSent: true,
      },
    });

    await expect(loginResult).resolves.toBe('verification-required');
    expect(service.status()).toBe('anonymous');
    expect(service.isAuthenticated()).toBe(false);
    expect(service.accessToken()).toBeNull();
    expect(service.emailOtpChallenge()?.verificationToken).toBe(
      'verification-token-in-memory',
    );
    http.expectNone('/api/v1/auth/me');

    const verifyResult = firstValueFrom(service.verifyEmailOtp('123456'));
    const verifyRequest = http.expectOne('/api/v1/auth/otp/verify');
    expect(verifyRequest.request.body).toEqual({
      verificationToken: 'verification-token-in-memory',
      code: '123456',
    });
    expect(verifyRequest.request.withCredentials).toBe(true);
    verifyRequest.flush({
      accessToken: 'access-after-otp',
      expiresIn: 900,
      user: {
        id: 'user-otp',
        fullName: 'Admin verificado',
        role: 'ADMIN',
      },
    });

    http.expectOne('/api/v1/auth/me').flush({
      id: 'user-otp',
      fullName: 'Admin verificado',
      email: 'admin@ruta-fija.pe',
      role: 'ADMIN',
      organizationId: 'organization-1',
    });
    await expect(verifyResult).resolves.toBeUndefined();

    expect(service.emailOtpChallenge()).toBeNull();
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('access-after-otp');
    expect(storageSpy).not.toHaveBeenCalled();
  });

  it('comparte una única renovación entre consumidores concurrentes', async () => {
    const first = firstValueFrom(service.refreshAccessToken());
    const second = firstValueFrom(service.refreshAccessToken());

    const refreshRequest = http.expectOne('/api/v1/auth/refresh');
    expect(refreshRequest.request.withCredentials).toBe(true);
    refreshRequest.flush({
      accessToken: 'access-rotated',
      expiresIn: 900,
      user: {
        id: 'user-2',
        fullName: 'Admin Operativo',
        role: 'ADMIN',
      },
    });

    await expect(first).resolves.toBe('access-rotated');
    await expect(second).resolves.toBe('access-rotated');
    expect(service.accessToken()).toBe('access-rotated');
  });

  it('limpia la sesión cuando la renovación es rechazada', async () => {
    const result = firstValueFrom(service.refreshAccessToken());
    http
      .expectOne('/api/v1/auth/refresh')
      .flush(
        { status: 401, code: 'AUTH_TOKEN_EXPIRED', message: 'Sesión vencida' },
        { status: 401, statusText: 'Unauthorized' },
      );

    await expect(result).rejects.toBeTruthy();
    expect(service.status()).toBe('anonymous');
    expect(service.accessToken()).toBeNull();
  });

  it('limpia la sesión si /auth/me falla después de renovar', async () => {
    const result = firstValueFrom(service.ensureSession());
    http.expectOne('/api/v1/auth/refresh').flush({
      accessToken: 'access-temporary',
      expiresIn: 900,
      user: {
        id: 'user-3',
        fullName: 'Admin Temporal',
        role: 'ADMIN',
      },
    });
    await new Promise((resolve) => globalThis.setTimeout(resolve, 0));
    http
      .expectOne('/api/v1/auth/me')
      .flush(
        { status: 401, code: 'AUTH_TOKEN_INVALID', message: 'Token inválido' },
        { status: 401, statusText: 'Unauthorized' },
      );

    await expect(result).resolves.toBe(false);
    expect(service.status()).toBe('anonymous');
    expect(service.accessToken()).toBeNull();
    expect(service.user()).toBeNull();
  });
});
