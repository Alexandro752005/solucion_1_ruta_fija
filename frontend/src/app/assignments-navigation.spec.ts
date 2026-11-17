import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from './app.routes';
import { AuthSessionService } from './core/auth/auth-session.service';
import { UserRole } from './core/auth/auth.models';
import { AdminTermsApiService } from './core/terms/admin-terms-api.service';

@Component({ selector: 'rf-navigation-test-shell', template: '<router-outlet />', imports: [RouterOutlet] })
class TestShell {}
@Component({ selector: 'rf-navigation-assignment-probe', template: 'Asignaciones' })
class AssignmentProbe {}
@Component({ selector: 'rf-navigation-access-probe', template: 'Acceso' })
class AccessProbe {}

describe('revalidación real del router ante el atajo de Asignación', () => {
  let role: ReturnType<typeof signal<UserRole>>;
  let authenticated: boolean;
  let accepted: boolean;
  let ensureSession: ReturnType<typeof vi.fn>;
  let getCurrentTerms: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    role = signal<UserRole>('ADMIN'); authenticated = true; accepted = true;
    ensureSession = vi.fn(() => of(authenticated));
    getCurrentTerms = vi.fn(() => of({ accepted }));
    const shell = routes.find(r => r.path === '' && r.children)!;
    const original = shell.children!.find(r => r.path === 'assignments')!;
    const { loadComponent: _load, ...assignment } = original;
    const { loadComponent: _shellLoad, children: _children, ...parent } = shell;
    TestBed.configureTestingModule({ providers: [
      provideRouter([
        { path: 'login', component: AccessProbe }, { path: 'forbidden', component: AccessProbe },
        { ...parent, component: TestShell, children: [{ ...assignment, component: AssignmentProbe }, { path: 'admin-terms', component: AccessProbe }] },
      ]),
      { provide: AuthSessionService, useValue: { ensureSession, user: () => ({ role: role() }), hasAnyRole: (roles: readonly UserRole[]) => roles.includes(role()) } },
      { provide: AdminTermsApiService, useValue: { getCurrentTerms } },
    ] });
  });

  it('reutiliza la instancia, revalida también al consumir action y no necesita remontar la página', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/assignments');
    const original = harness.routeDebugElement!.children[0].componentInstance;
    ensureSession.mockClear(); getCurrentTerms.mockClear();
    await harness.navigateByUrl('/assignments?action=new');
    expect(ensureSession).toHaveBeenCalledOnce(); expect(getCurrentTerms).toHaveBeenCalledOnce();
    expect(harness.routeDebugElement!.children[0].componentInstance).toBe(original);
    await harness.navigateByUrl('/assignments');
    expect(ensureSession).toHaveBeenCalledTimes(2); expect(getCurrentTerms).toHaveBeenCalledTimes(2);
  });

  it.each(['session', 'role', 'terms'] as const)('impide abrir al cambiar %s desde la misma ruta', async kind => {
    const harness = await RouterTestingHarness.create('/assignments');
    if (kind === 'session') authenticated = false;
    if (kind === 'role') role.set('SUPER_ADMIN');
    if (kind === 'terms') accepted = false;
    await harness.navigateByUrl('/assignments?action=new');
    const url = TestBed.inject(Router).url;
    expect(url.startsWith(kind === 'session' ? '/login' : kind === 'role' ? '/forbidden' : '/admin-terms')).toBe(true);
    expect(url.split('?')[0]).not.toBe('/assignments');
  });
});
