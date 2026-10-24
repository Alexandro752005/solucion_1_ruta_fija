import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { MaintenancePage } from './maintenance.page';
import { MaintenanceView } from '../../core/maintenance/maintenance-api.service';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';

describe('L03 maintenance explicit work and return', () => {
  let http: HttpTestingController;
  const url = '/api/v1/vehicles/unit-a/maintenance';
  const view: MaintenanceView = { vehicleId: 'unit-a', version: 0, records: [], mechanicallyBlockedNow: false, history: [], observedAt: '2026-10-19T12:00:00Z' };
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MaintenancePage], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ vehicleId: 'unit-a' }) } } }] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function setup() { const f = TestBed.createComponent(MaintenancePage); http.expectOne(url).flush(view); f.detectChanges(); const c = f.componentInstance; c.reason = 'SYNTHETIC-CHECK'; c.confirmed = true; return c; }
  it('requires preview and resolves commitments before preventive confirmation', () => {
    const c = setup(); c.startDate = '2026-10-21'; c.endDate = '2026-10-23'; c.create(); http.expectNone((r) => r.method === 'POST');
    c.check(); http.expectOne((r) => r.url === url + '/preview').flush({ canConfirmPreventive: false, commitments: [{ assignmentId: 'service-a' }] });
    c.create(); http.expectNone((r) => r.method === 'POST'); expect(c.error()).not.toBeNull();
  });
  it('requires explicit confirmation and sends urgent exactly once without auth replay', () => {
    const c = setup(); c.kind = 'URGENT'; c.confirmed = false; c.create(); http.expectNone(url);
    c.confirmed = true; c.create(); const r = http.expectOne(url); expect(r.request.context.get(SKIP_AUTH_REFRESH)).toBe(true); expect(r.request.body.startDate).toBeNull();
    r.flush({ ...view, version: 1, mechanicallyBlockedNow: true }); expect(c.confirmed).toBe(false); expect(c.view()?.mechanicallyBlockedNow).toBe(true);
  });
  it('keeps work completion and return as distinct commands', () => {
    const c = setup(); c.responsibleReference = 'SYNTHETIC-TECH'; c.evidenceReference = 'SYNTHETIC-VISUAL'; c.work('job-a');
    const r = http.expectOne(url + '/job-a/work-completed'); expect(r.request.body.workCompleted).toBe(true); r.flush({ ...view, version: 1, mechanicallyBlockedNow: true });
    c.authorizeReturn(); http.expectNone(url + '/return'); c.confirmed = true; c.authorizeReturn(); http.expectOne(url + '/return').flush({ ...view, version: 2, mechanicallyBlockedNow: true });
    expect(c.view()?.mechanicallyBlockedNow).toBe(true);
  });
  it('blocks replay after an uncertain response until an explicit server read', () => {
    const c = setup(); c.kind = 'URGENT'; c.create(); http.expectOne(url).error(new ProgressEvent('lost-response'));
    expect(c.uncertain()).toBe(true); c.confirmed = true; c.create(); http.expectNone(url);
    c.refresh(); http.expectOne(url).flush({ ...view, version: 1, mechanicallyBlockedNow: true }); expect(c.uncertain()).toBe(false); expect(c.confirmed).toBe(false);
  });
  it('never uses an older preview for a changed start date', () => {
    const c = setup(); c.startDate = '2026-10-21'; c.endDate = '2026-10-25'; c.check();
    http.expectOne((r) => r.url.endsWith('/preview')).flush({ canConfirmPreventive: true, commitments: [] });
    c.startDate = '2026-10-22'; c.create(); http.expectNone((r) => r.method === 'POST');
  });
});
