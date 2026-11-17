import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthSessionService } from '../../../core/auth/auth-session.service';
import { SessionUser } from '../../../core/auth/auth.models';
import { AdminDashboardFacade } from './admin-dashboard.facade';
import { InvalidDashboardResponse, PRIORITIES, dateContext, distribution, routePresentation, validateServices } from './admin-dashboard.adapter';
import { RouteView } from '../../../core/routes/routes-api.service';
import { ServiceReportView } from '../../../core/reports/service-reports.models';

const actorId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const orgId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const routeId = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const versionId = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
const day = '2026-10-26';
const instant = '2026-10-26T12:00:00Z';
const summary = () => ({ generatedAt: instant, upcomingUntil: '2026-10-27T12:00:00Z', zoneId: 'America/Lima', operationalDate: day,
  priorities: PRIORITIES.map(([code, , route], index) => ({ code, order: index + 1, count: 0, route })), servicesInProgress: 1, activeDrivers: 3, activeVehicles: 9 });
const availability = () => ({ generatedAt: instant, drivers: [{ status: 'DISPONIBLE', total: 2 }, { status: 'NO_DISPONIBLE', total: 2 }], vehicles: [{ status: 'DISPONIBLE', total: 4 }, { status: 'EN_SERVICIO', total: 2 }, { status: 'ATENCION', total: 1 }, { status: 'MANTENIMIENTO', total: 1 }, { status: 'INACTIVO', total: 2 }], futureScheduledAssignments: 3, assignmentsInService: 3, openIncidents: 8 });
const services = (selected = day): ServiceReportView => ({ ruleVersion: 'SYNTHETIC_ONLY', scope: 'ORGANIZATION', from: selected, to: selected, zoneId: 'America/Lima', cutoffAt: instant, totals: { scheduled: 10, completed: 4, pending: 2, cancelled: 2, interrupted: 1, transferred: 1, completionPercent: 40 }, coverage: { excludedLegacyOffers: 0, canonicalServicesWithoutFirstOffer: 0, notice: '' }, items: [] });
const incidents = (selected = day) => ({ from: selected, to: selected, totalsByStatus: [{ status: 'OPEN', total: 1 }, { status: 'FOLLOW_UP', total: 1 }, { status: 'RESOLVED', total: 3 }, { status: 'CLOSED_BY_DRIVER', total: 1 }], totalsByCategory: [], items: [] });
const registeredRoute = (): RouteView => ({ id: routeId, name: 'Registered synthetic route', active: true, version: 1, currentVersionId: versionId, observedAt: instant,
  versions: [{ id: versionId, name: 'Registered synthetic route', revision: 1, contentSha256: '0'.repeat(64), createdAt: instant, stops: [{ key: 'start', name: 'Start', kind: 'START', latitude: -12.1, longitude: -77.1 }, { key: 'end', name: 'End', kind: 'END', latitude: -12.2, longitude: -77.2 }] }] });

describe('Dashboard facade HTTP boundaries (synthetic transport responses)', () => {
  let http: HttpTestingController;
  let data: AdminDashboardFacade;
  const actor = signal<SessionUser | null>(null);

  beforeEach(() => {
    actor.set({ id: actorId, organizationId: orgId, role: 'ADMIN', fullName: 'DO_NOT_RENDER_PRIVATE_NAME', organizationName: 'Synthetic tenant' });
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), AdminDashboardFacade, { provide: AuthSessionService, useValue: { user: actor } }] });
    http = TestBed.inject(HttpTestingController); data = TestBed.inject(AdminDashboardFacade); TestBed.tick();
  });
  afterEach(() => { http.verify({ ignoreCancelled: true }); TestBed.resetTestingModule(); });

  function complete(options: { noContext?: boolean; availabilityFailure?: boolean; invalidAvailability?: boolean; forbiddenSummary?: boolean; emptyRoute?: boolean; badServices?: boolean; badNoticeOwner?: boolean; refresh?: boolean; unknownFleetState?: boolean } = {}): void {
    const base = http.match(() => true);
    expect(base).toHaveLength(options.refresh ? 4 : 5);
    for (const request of base) {
      expect(request.request.method).toBe('GET'); expect(request.request.params.has('organizationId')).toBe(false);
      switch (request.request.url) {
        case '/api/v1/dashboard/admin': {
          if (options.forbiddenSummary) request.flush({}, { status: 403, statusText: 'Forbidden' });
          else { const value = summary(); request.flush(options.noContext ? { ...value, zoneId: undefined, operationalDate: undefined } : value); }
          break;
        }
        case '/api/v1/reports/availability':
          if (options.availabilityFailure) request.flush({}, { status: 503, statusText: 'Unavailable' });
          else request.flush(options.invalidAvailability ? { ...availability(), drivers: [{ status: 'DISPONIBLE', total: -1 }] } : options.unknownFleetState ? { ...availability(), vehicles: [...availability().vehicles, { status: 'FUTURE_STATE', total: 2 }] } : availability());
          break;
        case '/api/v1/drivers':
          expect(request.request.params.get('size')).toBe('2'); expect(request.request.params.get('sort')).toBe('createdAt,desc');
          request.flush({ page: 0, size: 2, totalItems: 4, totalPages: 2, items: [{ id: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', active: false, availabilityStatus: 'NO_DISPONIBLE', fullName: 'DO_NOT_RENDER_PRIVATE_NAME', phone: 'DO_NOT_RENDER_PRIVATE_PHONE', documentNumber: 'DO_NOT_RENDER_DOCUMENT' }] });
          break;
        case '/api/v1/notices':
          expect(request.request.params.get('failures')).toBe('false');
          request.flush(options.badNoticeOwner ? { page: 0, size: 20, totalItems: 1, totalPages: 1, items: [{ id: routeId, userId: orgId, eventType: 'service.changed', createdAt: instant, references: {} }] } : { page: 0, size: 20, totalItems: 0, totalPages: 0, items: [] });
          break;
        case '/api/v1/routes': request.flush(options.emptyRoute ? [] : [{ id: routeId, name: 'Registered synthetic route', active: true, version: 1, currentVersionId: versionId }]); break;
        default: throw new Error('Unexpected initial endpoint: ' + request.request.url);
      }
    }
    if (!options.forbiddenSummary && !options.noContext) {
      const service = http.expectOne(request => request.url === '/api/v1/reports/services');
      expect(service.request.params.get('from')).toBe(day); expect(service.request.params.get('to')).toBe(day);
      service.flush(options.badServices ? { ...services(), totals: { ...services().totals, completed: 11 } } : services());
      http.expectOne(request => request.url === '/api/v1/reports/incidents').flush(incidents());
    }
    if (!options.emptyRoute && !options.refresh) http.expectOne('/api/v1/routes/' + routeId).flush(registeredRoute());
  }

  it('loads eight bounded sources with the server day and canonical service count', () => {
    complete(); const view = data.view();
    expect(view.operationalDate).toBe(day); expect(view.selectedDate).toBe(day); expect(view.zoneId).toBe('America/Lima');
    expect(view.metrics.find(row => row.id === 'DA-K01')?.supporting).toBe('de 4 registrados');
    expect(view.metrics.find(row => row.id === 'DA-K04')?.value).toBe(1);
    expect(view.metrics.find(row => row.id === 'DA-K05')?.value).toBe(8);
    expect(view.incidents.total).toBe(6); expect(view.fleet.total).toBe(10);
    expect(view.operation.center).toBe('40.0 %'); expect(view.route.revision).toBe(1);
  });
  it('does not substitute active resource totals for registered availability totals', () => {
    complete(); expect(data.view().metrics[0].supporting).toBe('de 4 registrados'); expect(data.view().metrics[1].supporting).toBe('de 10 registrados');
  });
  it('keeps successful panels while an independent availability source fails', () => {
    complete({ availabilityFailure: true }); expect(data.view().metrics[0].value).toBeNull(); expect(data.view().fleet.state).toBe('unavailable'); expect(data.view().operation.center).toBe('40.0 %'); expect(data.view().metrics[3].value).toBe(1);
  });
  it('rejects negative distributions instead of manufacturing a successful zero', () => {
    complete({ invalidAvailability: true }); expect(data.view().fleet.state).toBe('invalid'); expect(data.view().metrics[0].value).toBeNull();
  });
  it('keeps a last valid response explicitly stale after a refresh failure', () => {
    complete(); data.refresh(); complete({ refresh: true, availabilityFailure: true });
    expect(data.view().fleet.state).toBe('stale'); expect(data.view().metrics[0].value).toBe(2); expect(data.view().metrics[0].state).toBe('stale');
  });
  it('retains an unknown fleet category in the denominator and labels it separately', () => {
    complete({ unknownFleetState: true }); expect(data.view().fleet.total).toBe(12); expect(data.view().fleet.legend.find(row => row.label === 'Otros estados')?.value).toBe(2);
  });
  it('rejects inconsistent canonical report counts without collapsing other sources', () => {
    complete({ badServices: true }); expect(data.view().operation.state).toBe('invalid'); expect(data.view().fleet.total).toBe(10);
  });
  it('disables daily queries when the old backend lacks the two additive fields', () => {
    complete({ noContext: true }); expect(data.view().dateContextAvailable).toBe(false); expect(data.view().selectedDate).toBe(''); expect(data.view().metrics[3].value).toBe(1); http.expectNone(request => request.url.includes('/reports/services') || request.url.includes('/reports/incidents'));
    expect(data.view().timestamps?.availability).toContain(' UTC');
    expect(data.view().updatedLabel).toContain(' UTC');
  });
  it('does not fall back to platform endpoints after a forbidden operational summary', () => {
    complete({ forbiddenSummary: true }); expect(data.view().prioritiesState).toBe('forbidden'); expect(data.view().metrics[3].value).toBeNull(); http.expectNone(request => request.url.includes('/platform/'));
    expect(data.view().operation.state).toBe('unavailable'); expect(data.view().incidents.state).toBe('unavailable');
  });
  it('drops old-period requests and preserves current-only metrics on a day change', () => {
    complete(); data.selectDate('2026-10-25');
    const oldServices = http.expectOne(request => request.url === '/api/v1/reports/services'); const oldIncidents = http.expectOne(request => request.url === '/api/v1/reports/incidents');
    data.selectDate('2026-10-24'); expect(oldServices.cancelled).toBe(true); expect(oldIncidents.cancelled).toBe(true);
    http.expectOne(request => request.url === '/api/v1/reports/services').flush(services('2026-10-24'));
    http.expectOne(request => request.url === '/api/v1/reports/incidents').flush(incidents('2026-10-24'));
    expect(data.view().selectedDate).toBe('2026-10-24'); expect(data.view().metrics[4].value).toBe(8);
  });
  it('refuses a report for a date different from the requested cohort', () => {
    complete(); data.selectDate('2026-10-25');
    http.expectOne(request => request.url === '/api/v1/reports/services').flush(services(day));
    http.expectOne(request => request.url === '/api/v1/reports/incidents').flush(incidents('2026-10-25'));
    expect(data.view().operation.state).toBe('invalid');
  });
  it('ignores future dates rather than requesting a fake historical availability state', () => {
    complete(); data.selectDate('2026-10-27'); expect(data.view().selectedDate).toBe(day); http.expectNone(() => true);
  });
  it.each(['SUPER_ADMIN', 'CONDUCTOR'] as const)('clears actor data and pending requests when the session changes to %s', role => {
    complete(); actor.set({ id: orgId, role, fullName: 'Synthetic other role' }); TestBed.tick();
    expect(data.view().metrics.every(metric => metric.value === null)).toBe(true); expect(data.view().drivers).toEqual([]); expect(data.view().notices).toEqual([]); http.expectNone(() => true);
  });
  it('never renders raw personal driver fields and retains the actual administrative state', () => {
    complete(); const view = data.view(); expect(JSON.stringify(view)).not.toContain('DO_NOT_RENDER_'); expect(view.drivers[0].code).toBe('CONDUCTOR-eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'); expect(view.drivers[0].active).toBe(false); expect(view.drivers[0].tone).toBe('gray');
  });
  it('rejects a notice belonging to another account', () => { complete({ badNoticeOwner: true }); expect(data.view().noticesState).toBe('invalid'); expect(data.view().noticeTotal).toBeNull(); });
  it('does not request a fabricated route when the route catalog is empty', () => { complete({ emptyRoute: true }); expect(data.view().route.state).toBe('empty'); expect(data.view().route.notice).toBe('No hay rutas registradas.'); });
  it('cancels an obsolete route detail before accepting a newer empty catalog', () => {
    complete(); data.loadRoutes();
    http.expectOne(request => request.url === '/api/v1/routes').flush([{ id: routeId, name: 'Registered synthetic route', version: 1, active: true, currentVersionId: versionId }]);
    const oldDetail = http.expectOne('/api/v1/routes/' + routeId);
    data.loadRoutes(); expect(oldDetail.cancelled).toBe(true);
    http.expectOne(request => request.url === '/api/v1/routes').flush([]);
    expect(data.view().route.state).toBe('empty'); expect(data.view().route.points).toEqual([]);
  });
  it('defers activity and performs only GET requests without acknowledging notices or creating reports', () => {
    complete(); http.expectNone('/api/v1/audit-events'); data.loadActivity();
    const request = http.expectOne(value => value.url === '/api/v1/audit-events'); expect(request.request.method).toBe('GET'); request.flush({ items: [] });
    expect(data.view().activity?.items).toEqual([]); http.expectNone(request => request.method !== 'GET');
  });
  it('coalesces repeated refreshes into one in-flight query and one pending query per source', () => {
    complete(); for(let i=0;i<30;i++) data.refresh();
    complete({refresh:true}); complete({refresh:true}); http.expectNone(()=>true);
    expect(data.view().fleet.state).toBe('ready');
  });
  it('keeps an explicit route selection during a later catalog refresh', () => {
    complete(); const secondId='ffffffff-ffff-4fff-8fff-ffffffffffff';
    const rows=[{id:routeId,name:'First',active:true,currentVersionId:versionId},{id:secondId,name:'Second',active:true,currentVersionId:versionId}];
    data.loadRoutes();http.expectOne(request=>request.url==='/api/v1/routes').flush(rows);
    const prior=http.expectOne('/api/v1/routes/'+routeId);data.selectRoute(secondId);expect(prior.cancelled).toBe(true);
    http.expectOne('/api/v1/routes/'+secondId).flush({...registeredRoute(),id:secondId});
    data.loadRoutes();http.expectOne(request=>request.url==='/api/v1/routes').flush(rows);
    http.expectOne('/api/v1/routes/'+secondId).flush({...registeredRoute(),id:secondId});expect(data.view().routeCatalog.selectedId).toBe(secondId);
  });
  it('pages the bounded route catalog and scopes absence to that page', () => {
    complete();data.loadRoutes();
    http.expectOne(request=>request.url==='/api/v1/routes').flush(Array.from({length:100},(_,i)=>({id:i.toString(16).padStart(8,'0')+'-aaaa-4aaa-8aaa-aaaaaaaaaaaa',name:'Synthetic '+i,active:false,currentVersionId:versionId})));
    data.routePage(1);const next=http.expectOne(request=>request.url==='/api/v1/routes');expect(next.request.params.get('offset')).toBe('100');next.flush([]);
    expect(data.view().route.notice).toContain('en esta página');expect(data.view().routeCatalog.page).toBe(2);
    data.routePage(-1);http.expectOne(request=>request.url==='/api/v1/routes').flush([]);expect(data.view().routeCatalog.page).toBe(1);
  });
  function refreshCore(context = summary()): void {
    const calls = http.match(request => ['/api/v1/dashboard/admin','/api/v1/reports/availability','/api/v1/drivers','/api/v1/notices'].includes(request.url));
    expect(calls).toHaveLength(4);
    for (const call of calls) {
      if (call.request.url === '/api/v1/dashboard/admin') call.flush(context);
      else if (call.request.url === '/api/v1/reports/availability') call.flush(availability());
      else call.flush({page:0,size:20,totalItems:0,totalPages:0,items:[]});
    }
  }
  it('cancels the prior cohort when the server advances its operational day during refresh', () => {
    complete(); data.refresh(); refreshCore();
    const oldService=http.expectOne(request=>request.url==='/api/v1/reports/services'),oldIncident=http.expectOne(request=>request.url==='/api/v1/reports/incidents');
    data.refresh();refreshCore({...summary(),generatedAt:'2026-10-27T12:00:00Z',operationalDate:'2026-10-27'});
    expect(oldService.cancelled).toBe(true);expect(oldIncident.cancelled).toBe(true);
    http.expectOne(request=>request.url==='/api/v1/reports/services').flush({...services('2026-10-27'),cutoffAt:'2026-10-27T12:00:00Z'});
    http.expectOne(request=>request.url==='/api/v1/reports/incidents').flush(incidents('2026-10-27'));
    expect(data.view().selectedDate).toBe('2026-10-27');
  });
  it('cancels both old queries when the same organization changes its zone', () => {
    complete();data.refresh();refreshCore();
    const oldService=http.expectOne(request=>request.url==='/api/v1/reports/services'),oldIncident=http.expectOne(request=>request.url==='/api/v1/reports/incidents');
    data.refresh();refreshCore({...summary(),zoneId:'Europe/Madrid'});
    expect(oldService.cancelled).toBe(true);expect(oldIncident.cancelled).toBe(true);
    http.expectOne(request=>request.url==='/api/v1/reports/services').flush({...services(),zoneId:'Europe/Madrid'});
    http.expectOne(request=>request.url==='/api/v1/reports/incidents').flush(incidents());
  });
  it('cancels a detail superseded by a queued catalog with a newer current version', () => {
    complete();data.loadRoutes();const first=http.expectOne(request=>request.url==='/api/v1/routes');data.loadRoutes();
    first.flush([{id:routeId,name:'Route',active:true,currentVersionId:versionId}]);
    const oldDetail=http.expectOne('/api/v1/routes/'+routeId),nextVersion='ffffffff-ffff-4fff-8fff-ffffffffffff';
    http.expectOne(request=>request.url==='/api/v1/routes').flush([{id:routeId,name:'Route',active:true,currentVersionId:nextVersion}]);
    expect(oldDetail.cancelled).toBe(true);
    const updated=registeredRoute();http.expectOne('/api/v1/routes/'+routeId).flush({...updated,currentVersionId:nextVersion,versions:[{...updated.versions[0],id:nextVersion,revision:2}]});
    expect(data.view().route.revision).toBe(2);
  });
});

describe('Dashboard response and route presentation invariants', () => {
  it('uses the actor zone across UTC midnight', () => { expect(dateContext({ ...summary(), generatedAt: '2026-10-26T04:30:00Z', operationalDate: '2026-10-25' })?.operationalDate).toBe('2026-10-25'); });
  it('rejects an operational date inconsistent with its declared zone', () => { expect(dateContext({ ...summary(), generatedAt: '2026-10-26T04:30:00Z' })).toBeNull(); });
  it('rejects duplicate distribution categories', () => { expect(() => distribution([{ status: 'DISPONIBLE', total: 1 }, { status: 'DISPONIBLE', total: 1 }])).toThrow(InvalidDashboardResponse); });
  it('does not join across an intermediate stop without coordinates', () => {
    const route = registeredRoute(); const version = route.versions[0];
    const view = routePresentation({ ...route, versions: [{ ...version, stops: [version.stops[0], { key: 'gap', name: 'No coordinates', kind: 'INTERMEDIATE' }, version.stops[1]] }] });
    expect(view.points).toHaveLength(2); expect(view.segments).toHaveLength(0);
  });
  it('preserves legitimate zero coordinates and does not replace missing coordinates by 0,0', () => {
    const route = registeredRoute(); const version = route.versions[0];
    const view = routePresentation({ ...route, versions: [{ ...version, stops: [{ key: 'zero', name: 'Zero latitude', kind: 'START', latitude: 0, longitude: 0 }, { key: 'one', name: 'One', kind: 'END', latitude: 1, longitude: 1 }] }] });
    expect(view.points).toHaveLength(2); expect(view.segments).toHaveLength(1);
  });
  it('keeps a null service percentage for a valid zero denominator', () => { const value = { ...services(), totals: { scheduled: 0, completed: 0, pending: 0, cancelled: 0, interrupted: 0, transferred: 0, completionPercent: null } }; expect(validateServices(value, day, 'America/Lima').totals.completionPercent).toBeNull(); });
});
