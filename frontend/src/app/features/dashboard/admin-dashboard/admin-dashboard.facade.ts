import { DestroyRef, Injectable, WritableSignal, computed, effect, inject, signal, untracked } from '@angular/core';
import { Observable, Subscription, finalize, map } from 'rxjs';
import { AuthSessionService } from '../../../core/auth/auth-session.service';
import { DashboardApiService, AdminDashboard } from '../../../core/dashboard/dashboard-api.service';
import { OperationsApiService } from '../../../core/operations/operations-api.service';
import { AvailabilityReport, IncidentReport } from '../../../core/operations/operations.models';
import { ServiceReportsApiService } from '../../../core/reports/service-reports-api.service';
import { ServiceReportView } from '../../../core/reports/service-reports.models';
import { ManagementApiService } from '../../../core/management/management-api.service';
import { Driver, PageResult } from '../../../core/management/management.models';
import { CommunicationApiService, OperationNotice } from '../../../core/operations/communication-api.service';
import { RoutesApiService, RouteSummary, RouteView } from '../../../core/routes/routes-api.service';
import { AuditApiService } from '../../../core/audit/audit-api.service';
import { AuditEvent } from '../../../core/audit/audit.models';
import { DashboardPanelState } from './admin-dashboard.models';
import { DashboardSource, InvalidDashboardResponse, dashboardPresentation, dateContext, isDay, isInstant, isUuid, routePresentation, validateAvailability, validateDrivers, validateIncidents, validateNotices, validateRoutes, validateServices, validateSummary } from './admin-dashboard.adapter';

const initial = <T>(): DashboardSource<T> => ({ state: 'loading', value: null, consultedAt: null });

/** Page-scoped data access. Never creates reports, acknowledges notices or changes resources. */
@Injectable()
export class AdminDashboardFacade {
  private readonly session = inject(AuthSessionService);
  private readonly dashboard = inject(DashboardApiService);
  private readonly operations = inject(OperationsApiService);
  private readonly reports = inject(ServiceReportsApiService);
  private readonly management = inject(ManagementApiService);
  private readonly communications = inject(CommunicationApiService);
  private readonly routes = inject(RoutesApiService);
  private readonly audit = inject(AuditApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly subscriptions = new Map<string, Subscription>();
  private readonly sequences = new Map<string, number>();
  private readonly queued = new Map<string, () => void>();
  private actorKey = '';
  private periodKey = '';
  private epoch = 0;
  private readonly organization = signal('');
  private userId = '';

  readonly summary = signal(initial<AdminDashboard>());
  readonly availability = signal(initial<AvailabilityReport>());
  readonly services = signal(initial<ServiceReportView>());
  readonly incidents = signal(initial<IncidentReport>());
  readonly drivers = signal(initial<PageResult<Driver>>());
  readonly notices = signal(initial<PageResult<OperationNotice>>());
  readonly route = signal(initial<RouteView>());
  readonly activity = signal(initial<readonly AuditEvent[]>());
  readonly selectedDay = signal('');
  private readonly routeNotice = signal('');
  readonly routeCatalog = signal(initial<readonly RouteSummary[]>());
  readonly routeOffset = signal(0);
  readonly selectedRouteId = signal('');
  readonly view = computed(() => ({ ...dashboardPresentation({
    organization: this.organization(), userId: this.userId, selectedDay: this.selectedDay(),
    summary: this.summary(), availability: this.availability(), services: this.services(), incidents: this.incidents(),
    drivers: this.drivers(), notices: this.notices(), route: this.route(), routeNotice: this.routeNotice(), activity: this.activity(),
  }), routeCatalog: {
    state: this.routeCatalog().state,
    options: (this.routeCatalog().value ?? []).filter(row => row.active && isUuid(row.currentVersionId)).map(row => ({ id: row.id, name: row.name })),
    selectedId: this.selectedRouteId(), page: this.routeOffset() / 100 + 1, hasNext: this.routeCatalog().value?.length === 100,
  } }));

  constructor() {
    effect(() => {
      const actor = this.session.user();
      const key = actor?.role === 'ADMIN' && actor.id && actor.organizationId ? `${actor.id}|${actor.organizationId}` : '';
      untracked(() => {
        if (key === this.actorKey) { if (key) this.organization.set(actor?.organizationName?.trim() || 'Organización asignada'); return; }
        this.reset(); this.actorKey = key;
        if (!key || !actor) return;
        this.organization.set(actor.organizationName?.trim() || 'Organización asignada'); this.userId = actor.id;
        this.refresh(); this.loadRoutes();
      });
    });
    this.destroyRef.onDestroy(() => this.cancel());
  }

  private cancel(): void {
    this.epoch++; this.queued.clear(); this.subscriptions.forEach(subscription => subscription.unsubscribe()); this.subscriptions.clear(); this.sequences.clear();
  }
  private cancelSource(key: string): void {
    this.queued.delete(key); this.sequences.set(key, (this.sequences.get(key) ?? 0) + 1);
    this.subscriptions.get(key)?.unsubscribe(); this.subscriptions.delete(key);
  }
  private reset(): void {
    this.cancel(); this.organization.set(''); this.userId = ''; this.lastOperationalDay = ''; this.periodKey = ''; this.selectedDay.set(''); this.routeNotice.set('');
    this.summary.set(initial()); this.availability.set(initial()); this.services.set(initial()); this.incidents.set(initial());
    this.drivers.set(initial()); this.notices.set(initial()); this.route.set(initial()); this.activity.set(initial());
    this.routeCatalog.set(initial()); this.routeOffset.set(0); this.selectedRouteId.set('');
  }

  private fail<T>(target: WritableSignal<DashboardSource<T>>, error: unknown): void {
    const status = typeof error === 'object' && error !== null && 'status' in error ? error.status : null;
    const previous = target();
    const state: DashboardPanelState = status === 401 || status === 403 ? 'forbidden' : error instanceof InvalidDashboardResponse ? 'invalid' : previous.value !== null ? 'stale' : 'unavailable';
    target.set({ state, value: state === 'stale' ? previous.value : null, consultedAt: previous.consultedAt });
  }

  private request<T>(key: string, source: Observable<T>, target: WritableSignal<DashboardSource<T>>, validate: (value: T) => T, next?: (value: T) => void, onError?: (error: unknown) => void): void {
    if (!this.actorKey) return;
    if (this.subscriptions.get(key) && !this.subscriptions.get(key)!.closed) {
      this.queued.set(key, () => this.request(key, source, target, validate, next, onError)); return;
    }
    const sequence = (this.sequences.get(key) ?? 0) + 1; this.sequences.set(key, sequence);
    const epoch = this.epoch;
    target.update(previous => ({ ...previous, state: previous.value === null ? 'loading' : 'refreshing' }));
    const job = new Subscription(); this.subscriptions.set(key, job);
    job.add(source.pipe(finalize(() => {
      if (epoch !== this.epoch || this.sequences.get(key) !== sequence) return;
      this.subscriptions.delete(key); job.unsubscribe();
      const again = this.queued.get(key); this.queued.delete(key); again?.();
    })).subscribe({
      next: value => {
        if (epoch !== this.epoch || this.sequences.get(key) !== sequence) return;
        try {
          const valid = validate(value);
          target.set({ state: 'ready', value: valid, consultedAt: new Date().toISOString() });
          next?.(valid);
        } catch (error) { const invalid = error instanceof InvalidDashboardResponse ? error : new InvalidDashboardResponse(); this.fail(target, invalid); onError?.(invalid); }
      },
      error: error => { if (epoch === this.epoch && this.sequences.get(key) === sequence) { this.fail(target, error); onError?.(error); } },
    }));
  }

  refresh(): void {
    if (!this.actorKey) return;
    this.request('summary', this.dashboard.admin(), this.summary, validateSummary, value => {
      const context = dateContext(value);
      if (!context) {
        this.cancelSource('services'); this.cancelSource('incidents');
        this.periodKey = '';
        this.selectedDay.set(''); this.services.set({ state: 'unavailable', value: null, consultedAt: null }); this.incidents.set({ state: 'unavailable', value: null, consultedAt: null }); return;
      }
      const selected = this.selectedDay();
      const priorToday = this.lastOperationalDay;
      if (!selected || selected === priorToday || selected > context.operationalDate) this.selectedDay.set(context.operationalDate);
      this.lastOperationalDay = context.operationalDate;
      this.loadPeriod();
    }, () => {
      // A failed summary cannot leave date-dependent panels waiting for requests that will never start.
      if (!dateContext(this.summary().value)) {
        this.cancelSource('services'); this.cancelSource('incidents'); this.periodKey = ''; this.selectedDay.set('');
        this.services.set({ state: 'unavailable', value: null, consultedAt: null });
        this.incidents.set({ state: 'unavailable', value: null, consultedAt: null });
      }
    });
    this.request('availability', this.operations.availabilityReport(), this.availability, validateAvailability);
    this.request('drivers', this.management.listDrivers({ page: 0, size: 2, sort: 'createdAt,desc' }), this.drivers, validateDrivers);
    this.request('notices', this.communications.inbox(false, 0), this.notices, value => validateNotices(value, this.userId));
  }
  private lastOperationalDay = '';

  selectDate(day: string): void {
    const context = dateContext(this.summary().value);
    if (!context || !isDay(day) || day > context.operationalDate || day === this.selectedDay()) return;
    this.cancelSource('services'); this.cancelSource('incidents');
    this.selectedDay.set(day); this.services.set(initial()); this.incidents.set(initial()); this.loadPeriod();
  }
  private loadPeriod(): void {
    const context = dateContext(this.summary().value), day = this.selectedDay();
    if (!context || !isDay(day)) return;
    const key = `${day}|${context.zoneId}`;
    if (key !== this.periodKey) {
      this.cancelSource('services'); this.cancelSource('incidents');
      this.services.set(initial()); this.incidents.set(initial()); this.periodKey = key;
    }
    if (this.services().value?.from !== day || this.services().value?.zoneId !== context.zoneId) this.services.set(initial());
    if (this.incidents().value?.from !== day) this.incidents.set(initial());
    this.request('services', this.reports.current(day, day), this.services, value => validateServices(value, day, context.zoneId));
    this.request('incidents', this.operations.incidentReport(day, day), this.incidents, value => validateIncidents(value, day));
  }

  loadRoutes(): void {
    if (!this.actorKey) return;
    // A newer catalog invalidates an outstanding detail, never an explicit choice still present in that page.
    this.cancelSource('route');
    this.route.update(previous => ({ ...previous, state: previous.value === null ? 'loading' : 'refreshing' }));
    const offset = this.routeOffset();
    this.request('route-catalog', this.routes.list(offset), this.routeCatalog, validateRoutes, rows => {
          const selected = rows.find(row => row.id === this.selectedRouteId() && row.active && isUuid(row.currentVersionId))
            ?? rows.find(row => row.active && isUuid(row.currentVersionId));
          if (!selected) {
            this.selectedRouteId.set('');
            this.routeNotice.set(rows.length === 0 && offset === 0 ? 'No hay rutas registradas.' : 'No hay rutas activas con versión vigente en esta página.');
            this.route.set({ state: 'empty', value: null, consultedAt: new Date().toISOString() }); return;
          }
          this.cancelSource('route');
          this.selectedRouteId.set(selected.id); this.loadRoute(selected);
    }, error => this.fail(this.route, error));
  }

  selectRoute(id: string): void {
    if (id === this.selectedRouteId() || this.routeCatalog().state !== 'ready') return;
    const selected = this.routeCatalog().value?.find(row => row.id === id && row.active && isUuid(row.currentVersionId));
    if (!selected) return;
    this.cancelSource('route'); this.selectedRouteId.set(id); this.route.set(initial()); this.loadRoute(selected);
  }
  routePage(direction: -1 | 1): void {
    if (this.routeCatalog().state !== 'ready' || direction !== -1 && direction !== 1) return;
    const offset = this.routeOffset() + direction * 100;
    if (offset < 0 || direction === 1 && this.routeCatalog().value?.length !== 100) return;
    this.cancelSource('route-catalog'); this.cancelSource('route');
    this.routeOffset.set(offset); this.selectedRouteId.set(''); this.routeCatalog.set(initial()); this.route.set(initial()); this.loadRoutes();
  }
  private loadRoute(selected: RouteSummary): void {
          this.routeNotice.set('');
          this.request('route', this.routes.get(selected.id), this.route, detail => {
            if (detail.id !== selected.id || detail.currentVersionId !== selected.currentVersionId) throw new InvalidDashboardResponse();
            routePresentation(detail); return detail;
          });
  }

  loadActivity(): void {
    if (this.activity().value !== null || this.subscriptions.get('activity') && !this.subscriptions.get('activity')!.closed) return;
    this.request('activity', this.audit.list({ page: 0, size: 6, sort: 'occurredAt,desc' }).pipe(map(page => page.items)), this.activity, rows => {
      if (!Array.isArray(rows) || rows.length > 6 || !rows.every(row => isUuid(row.id) && isInstant(row.occurredAt))) throw new InvalidDashboardResponse(); return rows;
    });
  }
}
