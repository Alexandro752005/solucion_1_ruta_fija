import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthSessionService } from './auth-session.service';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  const session = { hasAnyRole: vi.fn() };
  const forbiddenTree = { path: '/forbidden' };
  const router = { createUrlTree: vi.fn(() => forbiddenTree) };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthSessionService, useValue: session },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('permite un rol incluido en la ruta', () => {
    session.hasAnyRole.mockReturnValue(true);

    const result = runGuard(['ADMIN']);

    expect(result).toBe(true);
    expect(session.hasAnyRole).toHaveBeenCalledWith(['ADMIN']);
  });

  it('redirige cuando el rol no está permitido', () => {
    session.hasAnyRole.mockReturnValue(false);

    const result = runGuard(['ADMIN']);

    expect(result).toBe(forbiddenTree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/forbidden']);
  });

  function runGuard(roles: readonly string[]): unknown {
    const route = { data: { roles } } as unknown as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => roleGuard(route, state));
  }
});
