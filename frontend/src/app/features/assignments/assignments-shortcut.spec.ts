import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CanActivateFn, Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { EMPTY, Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthSessionService } from '../../core/auth/auth-session.service';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import { AssignmentsPage } from './assignments.page';

@Component({ template: '<p>Otra página</p>' })
class OtherPage {}

describe('ADMIN assignment shortcut', () => {
  let http: HttpTestingController;
  let router: Router;
  let permitted: boolean | Subject<boolean>;
  let guard: ReturnType<typeof vi.fn<CanActivateFn>>;

  beforeEach(() => {
    permitted = true;
    guard = vi.fn<CanActivateFn>(() => permitted);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'other', component: OtherPage },
          { path: 'assignments', component: AssignmentsPage, canActivate: [guard], runGuardsAndResolvers: 'paramsOrQueryParamsChange' },
        ]),
        provideHttpClient(), provideHttpClientTesting(),
        { provide: AuthSessionService, useValue: { user: () => null } },
        { provide: OperationRealtimeService, useValue: { events: EMPTY, connect: () => undefined, disconnect: () => undefined } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  function flushReads(): void {
    for (const request of http.match(r => r.method === 'GET')) {
      request.flush(['/api/v1/routes', '/api/v1/service-types'].includes(request.request.url)
        ? [] : { items: [], page: 0, totalPages: 0, totalItems: 0 });
    }
  }

  async function settled(harness: RouterTestingHarness): Promise<void> {
    await harness.fixture.whenStable();
    flushReads();
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  async function setup(url = '/assignments'): Promise<{ harness: RouterTestingHarness; page: AssignmentsPage }> {
    const harness = await RouterTestingHarness.create('/other');
    const page = await harness.navigateByUrl(url, AssignmentsPage);
    await settled(harness);
    return { harness, page };
  }

  it('opens from another page, removes only action with replacement, and never posts', async () => {
    const replace = vi.spyOn(TestBed.inject(Router), 'navigate');
    const { page } = await setup('/assignments?action=new&source=dashboard&tag=one&tag=two#offer');
    expect(page.formOpen()).toBe(true);
    expect(router.parseUrl(router.url).queryParams).toEqual({ source: 'dashboard', tag: ['one', 'two'] });
    expect(router.parseUrl(router.url).fragment).toBe('offer');
    expect(replace).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: { action: null }, queryParamsHandling: 'merge', replaceUrl: true,
    }));
    http.expectNone(r => r.method !== 'GET');
  });

  it.each(['/assignments', '/assignments?action=delete', '/assignments?action=NEW'])(
    'does not open for an ordinary or unknown intent: %s', async url => {
      const { page } = await setup(url);
      expect(page.formOpen()).toBe(false);
      http.expectNone(r => r.method !== 'GET');
    },
  );

  it('preserves a dirty existing form and focuses it without resetting or reloading resources', async () => {
    const { harness, page } = await setup();
    page.startCreate(); flushReads(); harness.detectChanges();
    page.form.controls.notes.setValue('Synthetic draft to preserve');
    page.form.markAsDirty();
    const start = vi.spyOn(page, 'startCreate');
    const before = page.form.getRawValue();
    await harness.navigateByUrl('/assignments?action=new', AssignmentsPage);
    await settled(harness);
    expect(harness.routeDebugElement?.componentInstance).toBe(page);
    expect(start).not.toHaveBeenCalled();
    expect(page.form.getRawValue()).toEqual(before);
    expect(page.form.dirty).toBe(true);
    const form = harness.routeNativeElement?.querySelector('[data-assignment-form]');
    expect(form?.contains(document.activeElement)).toBe(true);
    http.expectNone(() => true);
  });

  it.each(['saving', 'creationUncertain', 'reofferUncertain', 'cancellationUncertain', 'serviceBlocked'] as const)(
    'consumes a blocked intent once without resetting data or retrying when %s clears', async blocker => {
      const { harness, page } = await setup();
      page.form.controls.notes.setValue('Synthetic retained state');
      page[blocker].set(true);
      const before = page.form.getRawValue();
      await harness.navigateByUrl('/assignments?action=new&source=shortcut', AssignmentsPage);
      await settled(harness);
      expect(page.formOpen()).toBe(false);
      expect(page.form.getRawValue()).toEqual(before);
      expect(router.url).toBe('/assignments?source=shortcut');
      page[blocker].set(false); harness.detectChanges();
      await harness.navigateByUrl('/assignments?source=shortcut&later=1', AssignmentsPage);
      await settled(harness);
      expect(page.formOpen()).toBe(false);
      http.expectNone(r => r.method !== 'GET');
    },
  );

  it('waits for guards before opening and preserves the instance after permitted same-page navigation', async () => {
    const { harness, page } = await setup();
    const pendingGuard = new Subject<boolean>();
    permitted = pendingGuard;
    const navigation = harness.navigateByUrl('/assignments?action=new', AssignmentsPage);
    await Promise.resolve(); await Promise.resolve();
    expect(page.formOpen()).toBe(false);
    permitted = true; pendingGuard.next(true); pendingGuard.complete();
    await navigation; await settled(harness);
    expect(page.formOpen()).toBe(true);
    expect(harness.routeDebugElement?.componentInstance).toBe(page);
    expect(guard.mock.calls.length).toBeGreaterThanOrEqual(3);
    http.expectNone(r => r.method !== 'GET');
  });

  it('does not open or send commands when same-page navigation is denied', async () => {
    const { harness, page } = await setup();
    permitted = false;
    await router.navigateByUrl('/assignments?action=new');
    await settled(harness);
    expect(page.formOpen()).toBe(false);
    expect(router.url).toBe('/assignments');
    http.expectNone(r => r.method !== 'GET');
  });

  it('does not reopen after URL cleanup, a repeated click, a return to the cleaned URL or a recreated page', async () => {
    const { harness, page } = await setup('/assignments?action=new');
    const start = vi.spyOn(page, 'startCreate');
    await harness.navigateByUrl('/assignments?action=new', AssignmentsPage);
    await settled(harness);
    expect(start).not.toHaveBeenCalled();
    page.closeForm(); harness.detectChanges();
    await harness.navigateByUrl('/assignments', AssignmentsPage);
    await settled(harness);
    expect(page.formOpen()).toBe(false);
    await harness.navigateByUrl('/other', OtherPage);
    const fresh = await harness.navigateByUrl('/assignments', AssignmentsPage);
    await settled(harness);
    expect(fresh).not.toBe(page);
    expect(fresh.formOpen()).toBe(false);
    http.expectNone(r => r.method !== 'GET');
  });
});
