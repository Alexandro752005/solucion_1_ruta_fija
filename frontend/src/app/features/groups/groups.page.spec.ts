import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { GroupResources, TransportGroup } from '../../core/management/management.models';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';
import { GroupsPage } from './groups.page';

describe('L03 group resources and retirement', () => {
  let http: HttpTestingController;
  const group: TransportGroup = { id: 'group-a', name: 'Synthetic group', active: true, version: 0, createdAt: '2026-10-19T00:00:00Z', updatedAt: '2026-10-19T00:00:00Z' };
  const resources: GroupResources = { groupId: group.id, version: 0, active: true, memberships: [], commitments: [], history: [], observedAt: '2026-10-19T00:00:00Z' };
  const page = (items: readonly unknown[]) => ({ items, page: 0, size: 100, totalPages: 1, totalItems: items.length });

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [GroupsPage], providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthSessionService, useValue: { hasAnyRole: () => true, user: () => ({ organizationName: 'SYNTHETIC ONLY' }) } },
    ] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function create() {
    const fixture = TestBed.createComponent(GroupsPage);
    http.expectOne((r) => r.url === '/api/v1/groups').flush(page([group]));
    fixture.detectChanges();
    return fixture;
  }
  function open(component: GroupsPage, value = resources) {
    component.openResources(group);
    http.expectOne('/api/v1/groups/group-a/resources').flush(value);
    http.expectOne((r) => r.url === '/api/v1/drivers').flush(page([{ id: 'driver-a', fullName: 'PRIVATE_SYNTHETIC_NAME', documentNumber: 'PRIVATE_DOCUMENT' }]));
    http.expectOne((r) => r.url === '/api/v1/vehicles').flush(page([{ id: 'vehicle-a', plate: 'PRIVATE_SYNTHETIC_PLATE' }]));
  }
  it('shows resource codes without keeping personal candidate fields', () => {
    const f = create();open(f.componentInstance);f.detectChanges();
    const text = (f.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('CONDUCTOR-driver-a');
    expect(text).not.toContain('PRIVATE_SYNTHETIC_NAME');
    expect(text).not.toContain('PRIVATE_DOCUMENT');
    expect(f.componentInstance.resourceCandidates()).toEqual([{ id: 'driver-a', kind: 'DRIVER' }, { id: 'vehicle-a', kind: 'VEHICLE' }]);
  });
  it('blocks retirement and shows exact commitments before asking confirmation', () => {
    const f = create();const c = f.componentInstance;c.startEdit(group);c.form.controls.active.setValue(false);c.submit();
    http.expectOne('/api/v1/groups/group-a/resources').flush({ ...resources, commitments: [{ assignmentId: 'service-a', status: 'CANCELLED', attentionKind: 'PENDING_REASSIGNMENT', scheduledAt: '2026-10-20T00:00:00Z', scheduledEndAt: '2026-10-20T01:00:00Z', groupReferenceSource: 'CREATION_SNAPSHOT' }] });
    f.detectChanges();expect(c.confirmingDeactivation()).toBe(false);expect(c.resources()?.commitments[0].assignmentId).toBe('service-a');
    http.expectNone((r) => r.method === 'PATCH');
    expect((f.nativeElement as HTMLElement).textContent).toContain('SERVICIO-service-a');
  });
  it('sends version and a single operation key without automatic refresh/retry', () => {
    const f = create();open(f.componentInstance);const c = f.componentInstance;
    c.changeMembership('DRIVER', 'driver-a', true, 'SYNTHETIC_REVIEW');
    const req = http.expectOne('/api/v1/groups/group-a/memberships');
    expect(req.request.body.expectedVersion).toBe(0);expect(req.request.body.operationKey).toMatch(/^[a-f0-9-]{36}$/);
    expect(req.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    req.flush({ ...resources, version: 1, memberships: [{ resourceId: 'driver-a', kind: 'DRIVER', active: true, source: 'ADMIN', recordedAt: resources.observedAt }] });
    expect(c.resources()?.version).toBe(1);expect(c.resourcesSaving()).toBe(false);
  });
  it('requires explicit reconciliation after a lost response', () => {
    const f = create();open(f.componentInstance);const c = f.componentInstance;
    c.changeMembership('DRIVER', 'driver-a', true, 'SYNTHETIC_REVIEW');
    http.expectOne('/api/v1/groups/group-a/memberships').error(new ProgressEvent('network-failure'));
    expect(c.resourcesUncertain()).toBe(true);
    c.changeMembership('DRIVER', 'driver-a', true, 'SYNTHETIC_REVIEW');http.expectNone('/api/v1/groups/group-a/memberships');
    c.reloadResources();http.expectOne('/api/v1/groups/group-a/resources').flush({ ...resources, version: 1 });
    expect(c.resourcesUncertain()).toBe(false);expect(c.resources()?.version).toBe(1);
  });
  it('checks server commitments before versioned administrative retirement', () => {
    const f = create();const c = f.componentInstance;c.startEdit(group);c.form.controls.active.setValue(false);c.submit();
    http.expectOne('/api/v1/groups/group-a/resources').flush(resources);expect(c.confirmingDeactivation()).toBe(true);
    c.confirmDeactivation();const req = http.expectOne('/api/v1/groups/group-a');expect(req.request.method).toBe('PATCH');expect(req.request.body.expectedVersion).toBe(0);
    req.flush({ ...group, active: false, version: 1 });http.expectOne((r) => r.url === '/api/v1/groups').flush(page([{ ...group, active: false, version: 1 }]));
    expect(c.formOpen()).toBe(false);
  });
  it('distinguishes pending safety attention from interrupted continuity using readable labels',()=>{
    const c=create().componentInstance;expect(c.commitmentStatusLabel('INTERRUPTED')).toBe('Interrumpida');expect(c.attentionLabel('SAFETY_ATTENTION')).toContain('conductor o unidad');expect(c.attentionLabel('INTERRUPTED_CONTINUITY')).toContain('decisión');
  });
});
