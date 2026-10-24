import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { firstValueFrom, isObservable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminTermsApiService } from '../terms/admin-terms-api.service';
import { AuthSessionService } from './auth-session.service';
import { adminTermsGuard } from './admin-terms.guard';

describe('adminTermsGuard', () => {
  let currentRole: string;
  let currentTerms: { accepted: boolean };
  let getCurrentTerms: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    currentRole = 'ADMIN';
    currentTerms = { accepted: false };
    getCurrentTerms = vi.fn(() => of(currentTerms));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthSessionService,
          useValue: { user: () => ({ role: currentRole }) },
        },
        {
          provide: AdminTermsApiService,
          useValue: { getCurrentTerms },
        },
      ],
    });
  });

  it('envía al ADMIN a términos si aún no acepta la versión vigente', async () => {
    const result = await evaluateGuard('dashboard', '/dashboard');
    const router = TestBed.inject(Router);

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toContain('/admin-terms');
    expect(router.serializeUrl(result as UrlTree)).toContain('returnUrl');
    expect(getCurrentTerms).toHaveBeenCalledOnce();
  });

  it('permite continuar si el backend confirma la aceptación de la versión vigente', async () => {
    currentTerms = { accepted: true };

    await expect(evaluateGuard('dashboard', '/dashboard')).resolves.toBe(true);
    expect(getCurrentTerms).toHaveBeenCalledOnce();
  });

  it('lleva a la pantalla de términos cuando aún no hay versión publicada', async () => {
    getCurrentTerms.mockReturnValue(
      throwError(() => ({ status: 404 })),
    );
    const result = await evaluateGuard('dashboard', '/dashboard');
    const router = TestBed.inject(Router);

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toContain('/admin-terms');
  });

  it('no consulta términos para SUPER_ADMIN ni bloquea la propia pantalla de términos', async () => {
    currentRole = 'SUPER_ADMIN';
    await expect(evaluateGuard('dashboard', '/dashboard')).resolves.toBe(true);
    expect(getCurrentTerms).not.toHaveBeenCalled();

    currentRole = 'ADMIN';
    await expect(evaluateGuard('admin-terms', '/admin-terms')).resolves.toBe(true);
    expect(getCurrentTerms).not.toHaveBeenCalled();
  });

  async function evaluateGuard(
    routePath: string,
    url: string,
  ): Promise<unknown> {
    const route = {
      routeConfig: { path: routePath },
    } as ActivatedRouteSnapshot;
    const state = { url } as RouterStateSnapshot;
    const result = TestBed.runInInjectionContext(() =>
      adminTermsGuard(route, state),
    );
    return isObservable(result) ? firstValueFrom(result) : await Promise.resolve(result);
  }
});
