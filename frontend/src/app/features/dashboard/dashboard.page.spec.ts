import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EMPTY, of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DashboardPage } from './dashboard.page';
import { DashboardApiService } from '../../core/dashboard/dashboard-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import { AdminDashboardComponent } from './admin-dashboard/admin-dashboard.component';
import { AdminDashboardFacade } from './admin-dashboard/admin-dashboard.facade';
import { DashboardReportFacade } from './admin-dashboard/dashboard-report.facade';
import { DashboardLiveUpdates } from './admin-dashboard/dashboard-live-updates.service';
import { dashboardFixture } from '../../../preview/dashboard-admin.fixtures';

describe('Role-scoped dashboard host (API and facade doubles)', () => {
  afterEach(() => TestBed.resetTestingModule());
  async function setup(platform = false, failed = false) {
    const dash = {
      admin: vi.fn(),
      platform: vi.fn(() => failed ? throwError(() => new Error('SYNTHETIC')) : of({ generatedAt: '2026-10-26T12:00:00Z', organizations: 5, activeOrganizations: 4, activeAdmins: 3, pendingAdmins: 1 })),
    };
    const created = vi.fn();
    const facade = { view: () => dashboardFixture('critical'), selectDate: vi.fn(), refresh: vi.fn(), loadRoutes: vi.fn(), loadActivity: vi.fn() };
    TestBed.overrideComponent(AdminDashboardComponent, { set: { providers: [{ provide: AdminDashboardFacade, useFactory: () => { created(); return facade; } }, { provide: DashboardReportFacade, useValue: { open: vi.fn() } }, { provide: DashboardLiveUpdates, useValue: { start: vi.fn(), stop: vi.fn() } }] } });
    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [provideRouter([]), { provide: DashboardApiService, useValue: dash },
        { provide: AuthSessionService, useValue: { hasAnyRole: (roles: string[]) => roles.includes(platform ? 'SUPER_ADMIN' : 'ADMIN') } },
        { provide: OperationRealtimeService, useValue: { events: EMPTY, connectionState: () => 'disconnected', connect: vi.fn(), disconnect: vi.fn() } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(DashboardPage); fixture.detectChanges();
    return { fixture, dash, created };
  }

  it('instantiates the ADMIN data host and keeps safety before the operational metrics', async () => {
    const { fixture, dash, created } = await setup();
    const root: HTMLElement = fixture.nativeElement;
    expect(created).toHaveBeenCalledOnce();
    expect(root.querySelector('rf-admin-dashboard')).not.toBeNull();
    const priorities = root.querySelector('.priorities')!;
    const metrics = root.querySelector('.metric-grid')!;
    expect(priorities.compareDocumentPosition(metrics) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(root.textContent!.indexOf('Incidencias críticas abiertas')).toBeLessThan(root.textContent!.indexOf('Servicios próximos'));
    expect(dash.platform).not.toHaveBeenCalled(); fixture.destroy();
  });

  it('SUPER_ADMIN requests only platform aggregates and never instantiates the operational facade', async () => {
    const { fixture, dash, created } = await setup(true);
    expect(dash.platform).toHaveBeenCalledOnce(); expect(dash.admin).not.toHaveBeenCalled();
    expect(created).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('rf-admin-dashboard')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Activaciones ADMIN pendientes'); fixture.destroy();
  });

  it('does not substitute tenant endpoints or fabricate zero after a platform failure', async () => {
    const { fixture, dash, created } = await setup(true, true);
    expect(fixture.nativeElement.textContent).toContain('No disponible');
    expect(created).not.toHaveBeenCalled(); expect(dash.admin).not.toHaveBeenCalled(); fixture.destroy();
  });
});
