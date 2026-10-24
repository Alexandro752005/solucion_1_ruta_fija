import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';
import { RouteView } from '../../core/routes/routes-api.service';
import { RoutesPage } from './routes.page';

describe('L03 versioned route catalog', () => {
  let http: HttpTestingController;
  const view: RouteView = { id: 'route-a', name: 'Synthetic route', active: true, version: 1, currentVersionId: 'version-a', observedAt: '2026-10-19T00:00:00Z', versions: [{ id: 'version-a', revision: 1, name: 'Synthetic route', contentSha256: 'a'.repeat(64), createdAt: '2026-10-19T00:00:00Z', stops: [{ key: 's', name: 'Start', kind: 'START' }, { key: 'm', name: 'Middle', kind: 'INTERMEDIATE' }, { key: 'e', name: 'End', kind: 'END' }] }] };
  beforeEach(() => { TestBed.configureTestingModule({ imports: [RoutesPage], providers: [provideHttpClient(), provideHttpClientTesting()] }); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  function create() { const f = TestBed.createComponent(RoutesPage); http.expectOne((r) => r.url === '/api/v1/routes').flush([view]); f.detectChanges(); return f; }
  function fill(c: RoutesPage) { c.newRoute(); c.name = 'Synthetic route'; c.reason = 'SYNTHETIC_REVIEW'; c.stops.forEach((s, i) => s.name = ['Start','Middle','End'][i]); }
  it('creates explicit ordered stops and sends one guarded write', () => {
    const f = create();fill(f.componentInstance);const c = f.componentInstance;c.save();
    const req = http.expectOne('/api/v1/routes');expect(req.request.method).toBe('POST');expect(req.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    expect(req.request.body.stops.map((s: { kind: string }) => s.kind)).toEqual(['START','INTERMEDIATE','END']);
    req.flush(view);http.expectOne((r) => r.url === '/api/v1/routes').flush([view]);expect(c.selected()?.currentVersionId).toBe('version-a');
  });
  it('appends a version while keeping the previous definition', () => {
    const f = create();const c = f.componentInstance;c.choose(view.id);http.expectOne('/api/v1/routes/route-a').flush(view);
    c.stops[1].name = 'Other middle';c.reason = 'SYNTHETIC_REVISION';c.save();
    const req = http.expectOne('/api/v1/routes/route-a/versions');expect(req.request.body.expectedVersion).toBe(1);
    const next = { ...view, version: 2, currentVersionId: 'version-b', versions: [...view.versions, { ...view.versions[0], id: 'version-b', revision: 2 }] };
    req.flush(next);http.expectOne((r) => r.url === '/api/v1/routes').flush([next]);expect(c.selected()?.versions[0]).toEqual(view.versions[0]);
  });
  it('resolves a lost response by operation lookup without repeating creation', () => {
    const f = create();const c = f.componentInstance;fill(c);c.save();const req = http.expectOne('/api/v1/routes');const key = req.request.body.operationKey;
    req.error(new ProgressEvent('network-failure'));expect(c.uncertain()).toBe(true);c.save();http.expectNone('/api/v1/routes');
    c.reconcile();http.expectOne('/api/v1/routes/operations/' + key).flush(view);http.expectOne((r) => r.url === '/api/v1/routes').flush([view]);
    expect(c.uncertain()).toBe(false);expect(c.selected()?.id).toBe('route-a');
  });
  it('rejects partial coordinates locally', () => {
    const f = create();const c = f.componentInstance;fill(c);c.stops[0].latitude = '-6.7';c.save();
    http.expectNone((r) => r.method === 'POST');expect(c.error()).not.toBeNull();
  });
  it('allows manual correction after a definite validation rejection', () => {
    const f = create();const c = f.componentInstance;fill(c);c.save();http.expectOne('/api/v1/routes').flush({ message: 'Synthetic validation rejection' }, { status: 400, statusText: 'Bad Request' });
    expect(c.uncertain()).toBe(false);expect(c.notice()).toBeNull();expect(c.name).toBe('Synthetic route');
  });
  it('does not let an older detail response overwrite a new route draft', () => {
    const f = create();const c = f.componentInstance;c.choose(view.id);const req = http.expectOne('/api/v1/routes/route-a');
    c.newRoute();c.name = 'New draft';req.flush(view);expect(c.selected()).toBeNull();expect(c.name).toBe('New draft');
  });
});
