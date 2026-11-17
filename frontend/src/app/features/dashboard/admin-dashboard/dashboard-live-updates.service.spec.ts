import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { OperationRealtimeEvent } from '../../../core/operations/operations.models';
import { OperationRealtimeService } from '../../../core/operations/operation-realtime.service';
import { AdminDashboardFacade } from './admin-dashboard.facade';
import { DashboardLiveUpdates } from './dashboard-live-updates.service';

describe('Dashboard visible-tab refresh coordination', () => {
  let service: DashboardLiveUpdates, events: Subject<OperationRealtimeEvent>, visibility: DocumentVisibilityState;
  const data = { refresh: vi.fn(), loadRoutes: vi.fn() }, realtime = { connect: vi.fn(), disconnect: vi.fn() };
  const event = (name: string) => events.next({ event: name, occurredAt: '2026-10-26T12:00:00Z', data: {} });
  const setVisibility = (value: DocumentVisibilityState) => { visibility = value; document.dispatchEvent(new Event('visibilitychange')); };
  beforeEach(() => {
    vi.useFakeTimers(); vi.clearAllMocks(); visibility = 'visible'; events = new Subject();
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
    TestBed.configureTestingModule({ providers: [DashboardLiveUpdates, { provide: AdminDashboardFacade, useValue: data }, { provide: OperationRealtimeService, useValue: { ...realtime, events } }] });
    service = TestBed.inject(DashboardLiveUpdates); service.start();
  });
  afterEach(() => { service.stop(); TestBed.resetTestingModule(); vi.restoreAllMocks(); vi.useRealTimers(); });
  it('polls current sources every 60 seconds without loading static routes', () => {
    vi.advanceTimersByTime(59999); expect(data.refresh).not.toHaveBeenCalled(); vi.advanceTimersByTime(1);
    expect(data.refresh).toHaveBeenCalledTimes(1); expect(data.loadRoutes).not.toHaveBeenCalled();
  });
  it('consolidates a burst and retains a route invalidation even when its final event is unrelated', () => {
    event('route.revised'); for (let i=0;i<30;i++) event('driver.changed'); vi.advanceTimersByTime(300);
    expect(data.refresh).toHaveBeenCalledTimes(1); expect(data.loadRoutes).toHaveBeenCalledTimes(1);
  });
  it('pauses while hidden and performs one consolidated round when returning', () => {
    setVisibility('hidden'); event('route.changed'); vi.advanceTimersByTime(180000);
    expect(data.refresh).not.toHaveBeenCalled(); setVisibility('visible'); setVisibility('visible');
    expect(data.refresh).toHaveBeenCalledTimes(1); expect(data.loadRoutes).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(60000); expect(data.refresh).toHaveBeenCalledTimes(2);
  });
  it('does not replay a pre-hide buffered event after the resume refresh', () => {
    event('driver.changed'); setVisibility('hidden'); setVisibility('visible'); vi.advanceTimersByTime(300);
    expect(data.refresh).toHaveBeenCalledTimes(1);
  });
  it('ignores initial readiness but refreshes once after reconnecting', () => {
    event('stream.ready'); vi.advanceTimersByTime(300); expect(data.refresh).not.toHaveBeenCalled();
    event('stream.ready'); vi.advanceTimersByTime(300); expect(data.refresh).toHaveBeenCalledTimes(1); expect(data.loadRoutes).toHaveBeenCalledTimes(1);
  });
  it('owns one subscription and removes timers and listeners on exit', () => {
    service.start(); expect(realtime.connect).toHaveBeenCalledTimes(1); service.stop();
    event('route.changed'); setVisibility('hidden'); setVisibility('visible'); vi.advanceTimersByTime(180000);
    expect(data.refresh).not.toHaveBeenCalled(); expect(realtime.disconnect).toHaveBeenCalledTimes(1);
  });
});
