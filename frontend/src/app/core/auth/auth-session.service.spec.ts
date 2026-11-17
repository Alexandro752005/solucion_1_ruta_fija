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

  it('no restaura una sesión limpiada cuando llega una renovación anterior', async () => {
    const renewal = outcome(firstValueFrom(service.refreshAccessToken()));
    const request = http.expectOne('/api/v1/auth/refresh');
    service.clearSession();
    request.flush(sessionResponse('previous-user', 'previous-access'));
    const result = await renewal;

    expect(service.status()).toBe('anonymous');
    expect(service.accessToken()).toBeNull();
    expect(service.user()).toBeNull();
    expect(result.ok).toBe(false);
  });

  it('mantiene el cierre local mientras una renovación anterior termina antes del logout remoto', async () => {
    await signIn('previous-user', 'initial-access');
    const renewal = outcome(firstValueFrom(service.refreshAccessToken()));
    const refresh = http.expectOne('/api/v1/auth/refresh');
    const closing = firstValueFrom(service.logout());
    const logout = http.expectOne('/api/v1/auth/logout');
    refresh.flush(sessionResponse('previous-user', 'late-access'));
    await renewal;
    const authenticatedWhileClosing = service.isAuthenticated();
    logout.flush(null);
    await closing;

    expect(authenticatedWhileClosing).toBe(false);
    expect(service.status()).toBe('anonymous');
    expect(service.accessToken()).toBeNull();
  });

  it('un error de renovación anterior no elimina un nuevo inicio de sesión', async () => {
    const previous = outcome(firstValueFrom(service.refreshAccessToken()));
    const refresh = http.expectOne('/api/v1/auth/refresh');
    await signIn('new-user', 'new-access');
    refresh.flush({}, { status: 401, statusText: 'Unauthorized' });
    await previous;

    expect(service.isAuthenticated()).toBe(true);
    expect(service.user()?.id).toBe('new-user');
    expect(service.accessToken()).toBe('new-access');
  });

  it('una renovación anterior exitosa no sustituye la identidad del nuevo inicio de sesión', async () => {
    const previous = outcome(firstValueFrom(service.refreshAccessToken()));
    const refresh = http.expectOne('/api/v1/auth/refresh');
    await signIn('new-user', 'new-access');
    refresh.flush(sessionResponse('previous-user', 'previous-access'));
    const result = await previous;

    expect(service.user()?.id).toBe('new-user');
    expect(service.accessToken()).toBe('new-access');
    expect(result.ok).toBe(false);
  });

  it('ensureSession anterior no limpia la sesión nueva al recibir su error tardío', async () => {
    const previous = firstValueFrom(service.ensureSession());
    const refresh = http.expectOne('/api/v1/auth/refresh');
    await signIn('new-user', 'new-access');
    refresh.flush({}, { status: 401, statusText: 'Unauthorized' });

    await expect(previous).resolves.toBe(false);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.user()?.id).toBe('new-user');
    expect(service.accessToken()).toBe('new-access');
  });

  it('descarta una identidad /auth/me solicitada antes del nuevo inicio de sesión', async () => {
    await signIn('previous-user', 'previous-access');
    const previous = outcome(firstValueFrom(service.loadCurrentUser()));
    const me = http.expectOne('/api/v1/auth/me');
    await signIn('new-user', 'new-access');
    me.flush(sessionResponse('previous-user', 'previous-access').user);
    const result = await previous;

    expect(service.user()?.id).toBe('new-user');
    expect(service.accessToken()).toBe('new-access');
    expect(result.ok).toBe(false);
  });

  it('no inicia una renovación obtenida antes de limpiar la sesión pero suscrita después', async () => {
    const previous = service.refreshAccessToken();
    service.clearSession();
    const result = outcome(firstValueFrom(previous));
    const unexpected = http.match('/api/v1/auth/refresh');
    unexpected.forEach(request => request.flush(sessionResponse('previous-user', 'previous-access')));
    const settled = await result;

    expect(unexpected).toHaveLength(0);
    expect(settled.ok).toBe(false);
    expect(service.status()).toBe('anonymous');
  });

  it('comparte la renovación nueva sin que el fin de una anterior permita duplicarla', async () => {
    const previous = outcome(firstValueFrom(service.refreshAccessToken()));
    const oldRequest = http.expectOne('/api/v1/auth/refresh');
    service.clearSession();
    const current = outcome(firstValueFrom(service.refreshAccessToken()));
    const newRequests = http.match('/api/v1/auth/refresh');
    oldRequest.flush(sessionResponse('previous-user', 'previous-access'));
    await previous;
    const concurrent = outcome(firstValueFrom(service.refreshAccessToken()));
    const unexpectedDuplicates = http.match('/api/v1/auth/refresh');
    newRequests.forEach(request => request.flush(sessionResponse('new-user', 'new-access')));
    unexpectedDuplicates.forEach(request => request.flush(sessionResponse('new-user', 'new-access')));
    await current;
    await concurrent;

    expect(newRequests).toHaveLength(1);
    expect(unexpectedDuplicates).toHaveLength(0);
    expect(service.user()?.id).toBe('new-user');
    expect(service.accessToken()).toBe('new-access');
  });

  async function signIn(id: string, accessToken: string): Promise<void> {
    const result = firstValueFrom(service.login({ email: 'synthetic@example.test', password: 'synthetic-test-only' }));
    const response = sessionResponse(id, accessToken);
    http.expectOne('/api/v1/auth/login').flush(response);
    http.expectOne('/api/v1/auth/me').flush(response.user);
    await result;
  }

  function sessionResponse(id: string, accessToken: string) {
    return { accessToken, expiresIn: 900, user: { id, fullName: 'Synthetic test user', role: 'ADMIN', organizationId: 'synthetic-organization' } } as const;
  }

  function outcome<T>(promise: Promise<T>): Promise<{ ok: boolean }> {
    return promise.then(() => ({ ok: true }), () => ({ ok: false }));
  }
});
