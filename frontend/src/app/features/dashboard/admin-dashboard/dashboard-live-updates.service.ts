import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject } from '@angular/core';
import { Subscription, auditTime, filter, map, tap } from 'rxjs';
import { OperationRealtimeService } from '../../../core/operations/operation-realtime.service';
import { AdminDashboardFacade } from './admin-dashboard.facade';

/** One socket subscriber and one visible-tab timer for the entire page. */
@Injectable()
export class DashboardLiveUpdates {
  private readonly document = inject(DOCUMENT);
  private readonly realtime = inject(OperationRealtimeService);
  private readonly data = inject(AdminDashboardFacade);
  private started = false;
  private events?: Subscription;
  private timer?: ReturnType<typeof setInterval>;
  private routeDirty = false;
  private wasHidden = false;
  private visibilityGeneration = 0;
  private readySeen = false;
  private readonly visibility = () => {
    const hidden = this.document.visibilityState === 'hidden';
    if (hidden) { if (!this.wasHidden) this.visibilityGeneration++; this.wasHidden = true; this.stopTimer(); return; }
    if (!this.wasHidden) return;
    this.wasHidden = false; this.visibilityGeneration++; this.data.refresh();
    if (this.routeDirty) { this.routeDirty = false; this.data.loadRoutes(); }
    this.startTimer();
  };
  constructor() { inject(DestroyRef).onDestroy(() => this.stop()); }
  start(): void {
    if (this.started) return;
    this.started = true; this.readySeen = false; this.wasHidden = this.document.visibilityState === 'hidden';
    this.realtime.connect();
    this.events = this.realtime.events.pipe(filter(event => {
      if (event.event === 'stream.ready') { if (!this.readySeen) { this.readySeen = true; return false; } this.routeDirty = true; }
      return true;
    }), tap(event => { this.routeDirty ||= event.event.startsWith('route.'); }), map(() => this.visibilityGeneration), auditTime(300)).subscribe(generation => {
      if (generation !== this.visibilityGeneration || this.document.visibilityState === 'hidden') return;
      this.data.refresh();
      if (this.routeDirty) { this.routeDirty = false; this.data.loadRoutes(); }
    });
    this.document.addEventListener('visibilitychange', this.visibility);
    if (!this.wasHidden) this.startTimer();
  }
  private startTimer(): void {
    this.stopTimer(); this.timer = setInterval(() => { if (this.document.visibilityState !== 'hidden') this.data.refresh(); }, 60_000);
  }
  private stopTimer(): void { if (this.timer !== undefined) clearInterval(this.timer); this.timer = undefined; }
  stop(): void {
    if (!this.started) return;
    this.started = false; this.stopTimer(); this.events?.unsubscribe(); this.events = undefined; this.routeDirty = false;
    this.document.removeEventListener('visibilitychange', this.visibility); this.realtime.disconnect();
  }
}
