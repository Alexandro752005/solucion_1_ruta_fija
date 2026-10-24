import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AssignmentRoutePanel } from './assignment-route-panel';
import { Assignment } from '../../core/operations/operations.models';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';

describe('L03 assigned version and explicit replacement', () => {
  let http: HttpTestingController;
  const assignment = { id: 'service-a', serviceId:'synthetic-service', version: 3, status: 'SCHEDULED', scheduledAt: '2030-01-01T12:00:00Z' } as Assignment;
  const route = { assignmentId: 'service-a', referenceStatus: 'CAPTURED', versionId: 'version-old', route: { name: 'Synthetic old', revision: 1, contentSha256: 'a'.repeat(64), stops: [{ key: 's', kind: 'START', name: 'Start' }, { key: 'm', kind: 'INTERMEDIATE', name: 'Middle' }, { key: 'e', kind: 'END', name: 'End' }] } };
  beforeEach(() => { TestBed.configureTestingModule({ imports: [AssignmentRoutePanel], providers: [provideHttpClient(),provideHttpClientTesting()] });http=TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  function setup() { const f=TestBed.createComponent(AssignmentRoutePanel);f.componentRef.setInput('assignment',assignment);f.detectChanges();http.expectOne('/api/v1/assignments/service-a/route').flush(route);return f; }
  it('shows the captured historical version without substituting the current catalog', () => { const f=setup();f.detectChanges();expect(f.nativeElement.textContent).toContain('Synthetic old');expect(f.nativeElement.textContent).toContain('Middle');http.expectNone('/api/v1/routes'); });
  it('requires explicit confirmation and sends a single replacement with no auth replay', () => {
    const c=setup().componentInstance;c.replacementVersion='version-new';c.reason='SYNTHETIC-CHANGE';c.replace();http.expectNone('/api/v1/assignments/service-a/route-replacement');
    c.confirmed=true;c.replace();const r=http.expectOne('/api/v1/assignments/service-a/route-replacement');expect(r.request.body.expectedVersion).toBe(3);expect(r.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    r.flush({...assignment,id:'service-new',status:'PENDING_RESPONSE'});http.expectOne('/api/v1/assignments/service-a/route').flush({...route,replacementAssignmentId:'service-new'});expect(c.canReplace()).toBe(false);
  });
  it('reconciles a lost response by reading the operation and never reposts', () => {
    const c=setup().componentInstance;c.replacementVersion='version-new';c.reason='SYNTHETIC-CHANGE';c.confirmed=true;c.replace();const r=http.expectOne('/api/v1/assignments/service-a/route-replacement');const key=r.request.body.operationKey;
    r.error(new ProgressEvent('lost'));http.expectOne('/api/v1/assignments/service-a/route').flush(route);expect(c.uncertain()).toBe(true);c.confirmed=true;c.replace();http.expectNone('/api/v1/assignments/service-a/route-replacement');
    c.reconcile();http.expectOne('/api/v1/assignment-route-operations/'+key).flush({...assignment,id:'service-new'});http.expectOne('/api/v1/assignments/service-a/route').flush({...route,replacementAssignmentId:'service-new'});expect(c.uncertain()).toBe(false);
  });
  it('keeps a historical unknown reference explicit', () => { const f=TestBed.createComponent(AssignmentRoutePanel);f.componentRef.setInput('assignment',assignment);f.detectChanges();http.expectOne('/api/v1/assignments/service-a/route').flush({assignmentId:'service-a',referenceStatus:'LEGACY_UNRECORDED'});f.detectChanges();expect(f.nativeElement.textContent).toContain('sin versión registrada'); });
});
