import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, InjectionToken, computed, effect, inject, signal, untracked } from '@angular/core';
import { Subscription, auditTime, tap } from 'rxjs';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { crmSessionContextKey } from '../../core/auth/auth-session-context';
import { LiveMapApiService } from '../../core/live-map/live-map-api.service';
import { LIVE_MAP_LIMITS, LiveMapGroup, LiveMapUnit, visibleLiveMapUnits } from '../../core/live-map/live-map.models';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';

export const LIVE_MAP_CLOCK = new InjectionToken<() => number>('Monotonic live map clock', { providedIn: 'root', factory: () => () => performance.now() });
export type LiveMapState = 'loading' | 'ready' | 'unavailable' | 'forbidden' | 'paused';

/** One page owns the existing stream and REST reconciliations. Coordinates never leave memory. */
@Injectable()
export class LiveMapFacade {
  private readonly session = inject(AuthSessionService);
  private readonly api = inject(LiveMapApiService);
  private readonly realtime = inject(OperationRealtimeService);
  private readonly document = inject(DOCUMENT);
  private readonly clock = inject(LIVE_MAP_CLOCK);
  private started = false;
  private active = false;
  private blocked = false;
  private actorKey = '';
  private epoch = 0;
  private sequence = 0;
  private query?: Subscription;
  private groupQuery?: Subscription;
  private events?: Subscription;
  private tick?: ReturnType<typeof setInterval>;
  private debounce?: ReturnType<typeof setTimeout>;
  private queued = false;
  private anchor: { server: number; monotonic: number } | null = null;
  private lastPoll = 0;
  private resumeSelectionId = '';
  readonly units = signal<readonly LiveMapUnit[]>([]);
  readonly groups = signal<readonly LiveMapGroup[]>([]);
  readonly state = signal<LiveMapState>('loading');
  readonly busy = signal(false);
  readonly suspended = signal(false);
  readonly notice = signal('');
  readonly limit = signal(5);
  readonly groupId = signal('');
  readonly driverName = signal('');
  readonly selectedId = signal('');
  readonly selected = computed(() => this.units().find(row => row.driverId === this.selectedId()) ?? null);
  readonly organization = computed(() => this.session.user()?.role === 'ADMIN' ? this.session.user()?.organizationName?.trim() || 'Organización asignada' : '');
  readonly connection = this.realtime.connectionState;
  readonly limits = LIVE_MAP_LIMITS;
  readonly statusText = computed(() => this.state() === 'paused' ? 'Vista en pausa' : this.state() === 'forbidden' ? 'Acceso restringido'
    : this.connection() === 'connected' ? 'En línea' : this.connection() === 'connecting' || this.connection() === 'reconnecting' ? 'Reconectando' : 'Sin canal en vivo');
  private readonly visibility = () => {
    if (this.document.visibilityState === 'hidden') { this.halt(); this.state.set('paused'); return; }
    this.synchronize(); if (!this.active && this.actorKey && !this.blocked) this.activate();
  };

  constructor() {
    effect(() => { this.session.user(); this.session.status(); untracked(() => this.synchronize()); });
    inject(DestroyRef).onDestroy(() => this.stop());
  }
  start(): void {
    if (this.started) return;
    this.started = true; this.document.addEventListener('visibilitychange', this.visibility); this.synchronize();
  }
  stop(): void {
    if (!this.started) return;
    this.started = false; this.document.removeEventListener('visibilitychange', this.visibility); this.halt(); this.actorKey = '';
  }
  private currentKey(): string {
    const user = this.session.user();
    return this.session.status() === 'authenticated' && user?.role === 'ADMIN' && user.organizationId ? crmSessionContextKey(user) ?? '' : '';
  }
  private synchronize(): void {
    if (!this.started) return;
    const next = this.currentKey();
    if (next === this.actorKey) return;
    this.halt(); this.actorKey = next; this.blocked = false;
    this.driverName.set(''); this.groupId.set(''); this.limit.set(5);
    if (!next) { this.state.set('forbidden'); this.notice.set('La sesión no autoriza consultar esta organización.'); return; }
    if (this.document.visibilityState === 'hidden') { this.state.set('paused'); return; }
    this.activate();
  }
  private activate(): void {
    if (this.active || !this.actorKey || this.blocked) return;
    this.active = true; this.suspended.set(false); this.state.set('loading'); this.notice.set(''); this.lastPoll = this.clock();
    this.realtime.connect();
    this.events = this.realtime.events.pipe(tap(() => { if (this.canRead()) this.invalidate(true); }), auditTime(300)).subscribe(() => {
      if (!this.canRead()) return;
      // An invalidation can remove consent or close a service. Purge first; only REST authorizes the next markers.
      this.refresh();
    });
    const epoch = this.epoch, key = this.actorKey;
    this.groupQuery = this.api.groups().subscribe({
      next: rows => { if (this.accepts(epoch, key)) this.groups.set(rows); },
      error: error => { if (this.accepts(epoch, key)) this.fail(error); },
    });
    this.refresh();
    this.tick = setInterval(() => {
      if (!this.canRead()) return;
      if (this.anchor) {
        this.publish(visibleLiveMapUnits(this.units(), this.anchor.server + Math.max(0, this.clock() - this.anchor.monotonic)));
        if (!this.selected()) this.selectedId.set('');
      }
      if (this.clock() - this.lastPoll >= 15_000) { this.lastPoll = this.clock(); this.refresh(); }
    }, 1000);
  }
  private canRead(): boolean {
    return this.active && !this.blocked && this.currentKey() === this.actorKey && this.document.visibilityState !== 'hidden';
  }
  private accepts(epoch: number, key: string): boolean { return this.canRead() && epoch === this.epoch && key === this.actorKey; }
  private clearUnits(preserveSelection = false): void {
    this.resumeSelectionId = preserveSelection ? this.selectedId() || this.resumeSelectionId : '';
    this.units.set([]); this.selectedId.set(''); this.anchor = null;
  }
  private halt(): void {
    this.suspended.set(true); this.epoch++; this.sequence++; this.query?.unsubscribe(); this.groupQuery?.unsubscribe(); this.events?.unsubscribe();
    this.query = undefined; this.groupQuery = undefined; this.events = undefined; this.queued = false; this.busy.set(false);
    if (this.tick !== undefined) clearInterval(this.tick); this.tick = undefined;
    if (this.debounce !== undefined) clearTimeout(this.debounce); this.debounce = undefined;
    if (this.active) this.realtime.disconnect(); this.active = false; this.clearUnits(); this.groups.set([]);
  }
  refresh(): void {
    if (!this.canRead()) return;
    if (this.busy()) { this.queued = true; return; }
    this.busy.set(true); const epoch = this.epoch, sequence = ++this.sequence, key = this.actorKey, requestedAt = this.clock();
    const current = () => this.accepts(epoch, key) && this.sequence === sequence;
    this.query = this.api.vehicles(this.limit(), this.groupId(), this.driverName()).subscribe({
      next: snapshot => {
        if (!current()) return;
        this.anchor = { server: Date.parse(snapshot.serverTime), monotonic: requestedAt };
        this.publish(visibleLiveMapUnits(snapshot.items, this.anchor.server + Math.max(0, this.clock() - requestedAt)));
        if (this.resumeSelectionId && this.units().some(row => row.driverId === this.resumeSelectionId)) this.selectedId.set(this.resumeSelectionId);
        this.resumeSelectionId = '';
        if (!this.selected()) this.selectedId.set('');
        this.state.set('ready'); this.notice.set(''); this.busy.set(false); this.runQueued();
      },
      error: error => { if (current()) { this.busy.set(false); this.fail(error); this.runQueued(); } },
    });
  }
  private runQueued(): void { if (this.queued) { this.queued = false; queueMicrotask(() => this.refresh()); } }
  private fail(error: unknown): void {
    this.invalidate(); const status = typeof error === 'object' && error !== null && 'status' in error ? error.status : null;
    if (status === 401 || status === 403 || status === 409) {
      this.halt(); this.blocked = true; this.state.set('forbidden'); this.notice.set('La sesión o sus permisos cambiaron. No se conservan ubicaciones.');
    } else { this.state.set('unavailable'); this.notice.set('No se pudo verificar la ubicación actual. Los puntos se retiraron; vuelve a consultar.'); }
  }
  setLimit(value: number): void { if (!LIVE_MAP_LIMITS.some(limit => limit === value) || value === this.limit()) return; this.limit.set(value); this.changed(); }
  setGroup(value: string): void { if (value && !this.groups().some(group => group.id === value)) return; this.groupId.set(value); this.changed(); }
  setName(value: string): void { this.driverName.set(value.slice(0, 100)); this.changed(true); }
  clearFilters(): void { this.driverName.set(''); this.groupId.set(''); this.limit.set(5); this.changed(); }
  private changed(debounce = false): void {
    if (this.debounce !== undefined) clearTimeout(this.debounce); this.debounce = undefined;
    this.invalidate();
    if (debounce) this.debounce = setTimeout(() => { this.debounce = undefined; this.refresh(); }, 300); else this.refresh();
  }
  private invalidate(preserveSelection = false): void { this.sequence++; this.query?.unsubscribe(); this.query = undefined; this.busy.set(false); this.queued = false; this.clearUnits(preserveSelection); }
  private publish(next: readonly LiveMapUnit[]): void {
    const current = this.units();
    if (current.length === next.length && current.every((row, index) => {
      const other = next[index]; return row.driverId === other.driverId && row.vehicleId === other.vehicleId && row.driverName === other.driverName && row.plate === other.plate
        && row.latitude === other.latitude && row.longitude === other.longitude && row.capturedAt === other.capturedAt && row.receivedAt === other.receivedAt
        && row.expiresAt === other.expiresAt && row.freshness === other.freshness;
    })) return;
    this.units.set(next);
  }
  select(id: string): void { if (this.units().some(row => row.driverId === id)) this.selectedId.set(id); }
}
