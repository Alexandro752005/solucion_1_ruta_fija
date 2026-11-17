import { Component, InjectionToken, computed, inject, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ReactiveFormsModule, FormControl } from '@angular/forms';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthSessionService } from '../core/auth/auth-session.service';
import { SessionStatus, SessionUser, UserRole } from '../core/auth/auth.models';
import { RuntimeConfigService } from '../core/config/runtime-config.service';
import { AppShellComponent } from './app-shell.component';
import { SHELL_CONTEXT_RELOAD, crmSessionContextKey } from './shell-session-context';
import { TOPBAR_ACTIVATION } from './topbar-activation';

const FEED = new InjectionToken<Subject<string>>('Synthetic previous-context response');
@Component({ selector: 'rf-context-data-probe', imports: [ReactiveFormsModule], template: '<h1>Dashboard de prueba</h1><p data-context-value>{{ value() }}</p><input aria-label="Borrador de prueba" id="context-draft" [formControl]="draft" />' })
class ContextDataProbe {
  readonly draft = new FormControl('', { nonNullable: true });
  readonly value = signal('OLD_CONTEXT_VALUE');
  // Deliberately allow a late callback to update this discarded instance.
  constructor() { inject(FEED).subscribe(value => this.value.set(value)); }
}
@Component({ selector: 'rf-context-access-probe', template: '<h1>Acceso de prueba</h1>' })
class ContextAccessProbe {}

describe('AppShell session context boundary', () => {
  const original: SessionUser = { id: 'admin-1', fullName: 'Private fixture', email: 'private@example.test', role: 'ADMIN', organizationId: 'organization-1', organizationName: 'Transportes de prueba' };
  let user: ReturnType<typeof signal<SessionUser | null>>;
  let status: ReturnType<typeof signal<SessionStatus>>;
  let token: ReturnType<typeof signal<string>>;
  let feed: Subject<string>;
  let reload: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    user = signal<SessionUser | null>({ ...original }); status = signal<SessionStatus>('authenticated'); token = signal('synthetic-1');
    feed = new Subject<string>(); reload = vi.fn();
    TestBed.configureTestingModule({ providers: [
      provideRouter([
        { path: 'login', component: ContextAccessProbe }, { path: 'forbidden', component: ContextAccessProbe },
        { path: '', component: AppShellComponent, children: [{ path: 'dashboard', component: ContextDataProbe }] },
      ]),
      { provide: AuthSessionService, useValue: { user: user.asReadonly(), status: status.asReadonly(), accessToken: token.asReadonly(),
        isAuthenticated: computed(() => status() === 'authenticated' && user() !== null),
        logout: () => of(undefined), hasAnyRole: (roles: readonly UserRole[]) => user() !== null && roles.includes(user()!.role) } },
      { provide: RuntimeConfigService, useValue: {} }, { provide: TOPBAR_ACTIVATION, useValue: ['ADMIN', 'SUPER_ADMIN'] },
      { provide: SHELL_CONTEXT_RELOAD, useValue: reload }, { provide: FEED, useValue: feed },
    ] });
  });
  afterEach(() => feed.complete());

  async function setup() {
    const harness = await RouterTestingHarness.create('/dashboard');
    harness.detectChanges(); await harness.fixture.whenStable();
    return harness;
  }
  const draft = () => document.querySelector<HTMLInputElement>('#context-draft');

  it('keeps the mounted page and draft during renewal, a fresh user object and cosmetic updates', async () => {
    const harness = await setup(); const before = draft()!;
    before.value = 'Draft belonging to the same actor'; before.dispatchEvent(new Event('input', { bubbles: true }));
    token.set('synthetic-2'); user.set({ ...original, fullName: 'Changed private name', organizationName: 'Nuevo nombre visible' });
    harness.detectChanges(); await harness.fixture.whenStable();
    expect(draft()).toBe(before); expect(draft()?.value).toBe('Draft belonging to the same actor'); expect(reload).not.toHaveBeenCalled();
  });

  it('does not confuse a transient unknown status with a confirmed different actor', async () => {
    const harness = await setup(); const before = draft();
    status.set('unknown'); harness.detectChanges(); await harness.fixture.whenStable();
    expect(draft()).toBe(before); expect(reload).not.toHaveBeenCalled();
    status.set('authenticated'); harness.detectChanges(); await harness.fixture.whenStable();
    expect(draft()).toBe(before); expect(reload).not.toHaveBeenCalled();
  });

  it.each([
    { name: 'account', change: { id: 'admin-2' } },
    { name: 'role', change: { role: 'SUPER_ADMIN' as const } },
    { name: 'organization', change: { organizationId: 'organization-2' } },
  ])('discards the old view and late callbacks after a confirmed $name change', async ({ change }) => {
    const harness = await setup(); expect(draft()).not.toBeNull();
    user.set({ ...original, ...change }); harness.detectChanges(); await harness.fixture.whenStable();
    expect(draft()).toBeNull();
    expect(document.querySelector('[data-context-value]')).toBeNull();
    expect(reload).toHaveBeenCalledExactlyOnceWith('/dashboard');
    feed.next('LATE_FROM_PREVIOUS_CONTEXT'); harness.detectChanges(); await harness.fixture.whenStable();
    expect(document.body.textContent).not.toContain('LATE_FROM_PREVIOUS_CONTEXT');
    expect(document.body.textContent).not.toContain('OLD_CONTEXT_VALUE');
    user.set({ ...original, ...change, organizationName: 'Later cosmetic update' });
    harness.detectChanges(); await harness.fixture.whenStable();
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('clears the old view and returns to login on confirmed session loss', async () => {
    const harness = await setup(); user.set(null); status.set('anonymous');
    harness.detectChanges(); await harness.fixture.whenStable();
    expect(draft()).toBeNull(); expect(document.body.textContent).not.toContain('OLD_CONTEXT_VALUE');
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toContain('/login'));
    expect(reload).not.toHaveBeenCalled();
  });

  it.each(['CONDUCTOR', 'UNRECOGNIZED'])('does not mount CRM data or use an ADMIN fallback for %s', async role => {
    user.set({ ...original, role: role as UserRole });
    await setup();
    expect(draft()).toBeNull(); expect(document.querySelector('rf-crm-topbar')).toBeNull();
    expect(document.querySelector('.primary-navigation')).toBeNull(); expect(reload).not.toHaveBeenCalled();
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/forbidden'));
  });

  it('does not mount a view before the initial session is confirmed', async () => {
    status.set('unknown'); await setup();
    expect(draft()).toBeNull(); expect(document.querySelector('rf-crm-topbar')).toBeNull();
  });

  it('normalizes only stable actor/role/organization values, excluding token and displayed identity', () => {
    expect(crmSessionContextKey({ ...original, id: ' admin-1 ', organizationId: ' organization-1 ', fullName: 'Other' })).toBe(crmSessionContextKey(original));
    expect(crmSessionContextKey({ ...original, organizationId: undefined })).toBe(crmSessionContextKey({ ...original, organizationId: null }));
    expect(crmSessionContextKey(null)).toBeNull();
    expect(crmSessionContextKey({ ...original, role: 'CONDUCTOR' })).toBeNull();
  });
});
