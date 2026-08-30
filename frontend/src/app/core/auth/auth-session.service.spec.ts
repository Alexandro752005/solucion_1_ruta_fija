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
        fullName: 'Administradora Ruta Fija',
        role: 'ADMINISTRADOR',
      },
    });

    const meRequest = http.expectOne('/api/v1/auth/me');
    meRequest.flush({
      id: 'user-1',
      fullName: 'Administradora Ruta Fija',
      email: 'admin@ruta-fija.pe',
      role: 'ADMINISTRADOR',
      organizationId: 'organization-1',
    });

    await expect(result).resolves.toBeUndefined();
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('access-1');
    expect(service.user()?.organizationId).toBe('organization-1');
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
        fullName: 'Coordinador Operativo',
        role: 'COORDINADOR',
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
        fullName: 'Administradora Temporal',
        role: 'ADMINISTRADOR',
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
