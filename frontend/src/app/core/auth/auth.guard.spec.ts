import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { firstValueFrom, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { authGuard, anonymousGuard } from './auth.guard';
import { AuthSessionService } from './auth-session.service';

describe('auth guards', () => {
  const session = { ensureSession: vi.fn() };
  const loginTree = { path: '/login' };
  const dashboardTree = { path: '/dashboard' };
  const router = {
    createUrlTree: vi.fn((commands: readonly string[]) =>
      commands[0] === '/login' ? loginTree : dashboardTree,
    ),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthSessionService, useValue: session },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('authGuard permite una sesión recuperada', async () => {
    session.ensureSession.mockReturnValue(of(true));

    await expect(runAuthGuard()).resolves.toBe(true);
  });

  it('authGuard redirige al login conservando returnUrl', async () => {
    session.ensureSession.mockReturnValue(of(false));

    await expect(runAuthGuard()).resolves.toBe(loginTree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/dashboard' },
    });
  });

  it('anonymousGuard evita mostrar login a una sesión vigente', async () => {
    session.ensureSession.mockReturnValue(of(true));
    const result = TestBed.runInInjectionContext(() =>
      anonymousGuard(
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
      ),
    );

    await expect(firstValueFrom(result as ReturnType<typeof of>)).resolves.toBe(
      dashboardTree,
    );
  });

  function runAuthGuard(): Promise<unknown> {
    const result = TestBed.runInInjectionContext(() =>
      authGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/dashboard' } as RouterStateSnapshot,
      ),
    );
    return firstValueFrom(result as ReturnType<typeof of>);
  }
});
