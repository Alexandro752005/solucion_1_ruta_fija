import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { authInterceptor } from './auth.interceptor';
import { AuthSessionService } from './auth-session.service';
import { CrossTabLockService } from './cross-tab-lock.service';

class ImmediateCrossTabLock {
  runExclusive<T>(_name: string, operation: () => Promise<T>): Promise<T> {
    return operation();
  }
}

describe('authInterceptor', () => {
  let client: HttpClient;
  let session: AuthSessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CrossTabLockService, useClass: ImmediateCrossTabLock },
      ],
    });
    client = TestBed.inject(HttpClient);
    session = TestBed.inject(AuthSessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    try {
      http?.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  it('no envía Bearer al login y sí lo usa para /auth/me', async () => {
    const authenticated = authenticate('access-initial');

    const login = http.expectOne('/api/v1/auth/login');
    expect(login.request.headers.has('Authorization')).toBe(false);
    login.flush(loginResponse('access-initial'));

    const me = http.expectOne('/api/v1/auth/me');
    expect(me.request.headers.get('Authorization')).toBe(
      'Bearer access-initial',
    );
    me.flush(currentUser());

    await expect(authenticated).resolves.toBeUndefined();
  });

  it('renueva sin Bearer y reintenta una sola vez con el token nuevo', async () => {
    await completeAuthentication('access-expired');
    const result = firstValueFrom(
      client.get<{ readonly ok: boolean }>('/api/v1/protected-resource'),
    );

    const original = http.expectOne('/api/v1/protected-resource');
    expect(original.request.headers.get('Authorization')).toBe(
      'Bearer access-expired',
    );
    original.flush(
      { status: 401, code: 'AUTH_TOKEN_EXPIRED', message: 'Token vencido' },
      { status: 401, statusText: 'Unauthorized' },
    );

    const refresh = http.expectOne('/api/v1/auth/refresh');
    expect(refresh.request.withCredentials).toBe(true);
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    refresh.flush(loginResponse('access-rotated'));
    await new Promise((resolve) => globalThis.setTimeout(resolve, 0));

    const retry = http.expectOne('/api/v1/protected-resource');
    expect(retry.request.headers.get('Authorization')).toBe(
      'Bearer access-rotated',
    );
    retry.flush({ ok: true });

    await expect(result).resolves.toEqual({ ok: true });
    http.expectNone('/api/v1/auth/refresh');
  });

  it('envía logout solo con cookie y no intenta auto-refresh ante 401', async () => {
    await completeAuthentication('access-current');
    const result = firstValueFrom(session.logout());

    const logout = http.expectOne('/api/v1/auth/logout');
    expect(logout.request.withCredentials).toBe(true);
    expect(logout.request.headers.has('Authorization')).toBe(false);
    logout.flush(
      { status: 401, code: 'AUTH_TOKEN_INVALID', message: 'Sesión inválida' },
      { status: 401, statusText: 'Unauthorized' },
    );

    await expect(result).rejects.toBeTruthy();
    http.expectNone('/api/v1/auth/refresh');
    expect(session.status()).toBe('anonymous');
  });

  async function completeAuthentication(accessToken: string): Promise<void> {
    const authenticated = authenticate(accessToken);
    http.expectOne('/api/v1/auth/login').flush(loginResponse(accessToken));
    http.expectOne('/api/v1/auth/me').flush(currentUser());
    await authenticated;
  }

  function authenticate(accessToken: string): Promise<void> {
    return firstValueFrom(
      session.login({
        email: 'admin@ruta-fija.pe',
        password: 'credencial-de-prueba',
      }),
    );
  }

  function loginResponse(accessToken: string) {
    return {
      accessToken,
      expiresIn: 900,
      user: currentUser(),
    } as const;
  }

  function currentUser() {
    return {
      id: 'user-1',
      fullName: 'Administradora Ruta Fija',
      email: 'admin@ruta-fija.pe',
      role: 'ADMINISTRADOR',
      organizationId: 'organization-1',
      organizationName: 'Transportes Demo',
    } as const;
  }
});
