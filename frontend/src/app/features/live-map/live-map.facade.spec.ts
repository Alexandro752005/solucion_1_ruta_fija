import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { SessionUser } from '../../core/auth/auth.models';
import { LiveMapApiService } from '../../core/live-map/live-map-api.service';
import { LiveMapGroup, LiveMapSnapshot } from '../../core/live-map/live-map.models';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import { OperationRealtimeEvent } from '../../core/operations/operations.models';
import { LIVE_MAP_CLOCK, LiveMapFacade } from './live-map.facade';

const unit = { driverId:'11111111-1111-4111-8111-111111111111',vehicleId:'22222222-2222-4222-8222-222222222222',driverName:'Conductor sintético',plate:'TEST-01',latitude:-12,longitude:-77,
  capturedAt:'2026-11-11T12:00:00Z',receivedAt:'2026-11-11T12:00:01Z',expiresAt:'2026-11-11T12:05:01Z',freshness:'CURRENT' as const };
const actor:SessionUser = {id:'synthetic-admin',role:'ADMIN',fullName:'ADMIN sintético',organizationId:'synthetic-tenant',organizationName:'Empresa sintética'};
const snapshot = ():LiveMapSnapshot => ({serverTime:'2026-11-11T12:00:10Z',limit:5,items:[{...unit}]});
describe('live map session, invalidation and expiry boundary', () => {
  let facade:LiveMapFacade, rows:Subject<LiveMapSnapshot>[], groups:Subject<readonly LiveMapGroup[]>[], events:Subject<OperationRealtimeEvent>, now:number, visible:DocumentVisibilityState;
  const user = signal<SessionUser|null>(actor), status=signal('authenticated');
  const api={vehicles:vi.fn(),groups:vi.fn()}, realtime={connect:vi.fn(),disconnect:vi.fn(),connectionState:signal('connected')};
  const visibility=(next:DocumentVisibilityState)=>{visible=next;document.dispatchEvent(new Event('visibilitychange'));};
  beforeEach(()=>{
    vi.useFakeTimers();vi.clearAllMocks();user.set({...actor});status.set('authenticated');rows=[];groups=[];events=new Subject();now=0;visible='visible';
    vi.spyOn(document,'visibilityState','get').mockImplementation(()=>visible);
    api.vehicles.mockImplementation(()=>{const request=new Subject<LiveMapSnapshot>();rows.push(request);return request;});
    api.groups.mockImplementation(()=>{const request=new Subject<readonly LiveMapGroup[]>();groups.push(request);return request;});
    TestBed.configureTestingModule({providers:[LiveMapFacade,{provide:AuthSessionService,useValue:{user,status}},{provide:LiveMapApiService,useValue:api},
      {provide:OperationRealtimeService,useValue:{...realtime,events}},{provide:LIVE_MAP_CLOCK,useValue:()=>now}]});
    facade=TestBed.inject(LiveMapFacade);facade.start();TestBed.tick();
  });
  afterEach(()=>{facade.stop();TestBed.resetTestingModule();vi.restoreAllMocks();vi.useRealTimers();});
  const advance=(ms:number)=>{now+=ms;vi.advanceTimersByTime(ms);};
  it('requests only tenant projections and serializes polling and manual refresh',async()=>{
    expect(api.vehicles).toHaveBeenCalledWith(5,'',''); expect(realtime.connect).toHaveBeenCalledTimes(1);
    facade.start();facade.refresh();advance(15000);expect(rows).toHaveLength(1);
    rows[0].next(snapshot());await Promise.resolve();expect(rows).toHaveLength(2);
    expect(facade.units()).toHaveLength(1);
  });
  it('debounces names and cancels the previous response instead of painting another filter',()=>{
    facade.setName('Pr');advance(150);facade.setName('Prueba');advance(299);expect(rows).toHaveLength(1);
    rows[0].next(snapshot());expect(facade.units()).toEqual([]);advance(1);
    expect(api.vehicles).toHaveBeenLastCalledWith(5,'','Prueba');rows[1].next(snapshot());expect(facade.units()).toHaveLength(1);
  });
  it('purges markers before reconciling any websocket invalidation and rejects the prior request',()=>{
    rows[0].next(snapshot());facade.select(unit.driverId);facade.refresh();
    events.next({event:'location.revoked',occurredAt:'2026-11-11T12:00:11Z',data:{}});advance(300);
    expect(facade.units()).toEqual([]);expect(facade.selectedId()).toBe('');
    rows[1].next(snapshot());expect(facade.units()).toEqual([]);expect(rows).toHaveLength(3);
  });
  it('uses the server anchor and monotonic elapsed time, not the local wall clock',()=>{
    vi.spyOn(Date,'now').mockReturnValue(1);rows[0].next(snapshot());advance(110000);
    expect(facade.units()[0]?.freshness).toBe('STALE');advance(191000);expect(facade.units()).toEqual([]);
  });
  it('preserves identical row references and selection during ticks and unchanged REST refreshes',()=>{
    rows[0].next(snapshot());facade.select(unit.driverId);const previous=facade.units();advance(1000);
    expect(facade.units()).toBe(previous);expect(facade.selectedId()).toBe(unit.driverId);
    facade.refresh();rows[1].next(snapshot());expect(facade.units()).toBe(previous);expect(facade.selectedId()).toBe(unit.driverId);
  });
  it('restores only an identifier after invalidation when the new REST response still authorizes that unit',()=>{
    rows[0].next(snapshot());facade.select(unit.driverId);events.next({event:'driver.changed',occurredAt:unit.capturedAt,data:{}});
    expect(facade.units()).toEqual([]);expect(facade.selectedId()).toBe('');advance(300);rows[1].next(snapshot());
    expect(facade.selectedId()).toBe(unit.driverId);
    events.next({event:'location.revoked',occurredAt:unit.capturedAt,data:{}});advance(300);rows[2].next({...snapshot(),items:[]});expect(facade.selectedId()).toBe('');
  });
  it.each([401,403,409])('purges and blocks all automatic requests after authorization uncertainty %s',code=>{
    rows[0].next(snapshot());facade.refresh();rows[1].error({status:code});
    expect(facade.units()).toEqual([]);expect(facade.groups()).toEqual([]);expect(facade.state()).toBe('forbidden');
    advance(60000);facade.refresh();expect(rows).toHaveLength(2);expect(realtime.disconnect).toHaveBeenCalledTimes(1);
  });
  it('purges on a network failure and permits a fresh REST consultation',()=>{
    rows[0].next(snapshot());facade.refresh();rows[1].error({status:0});expect(facade.units()).toEqual([]);expect(facade.state()).toBe('unavailable');
    facade.refresh();rows[2].next(snapshot());expect(facade.state()).toBe('ready');
  });
  it('cancels groups and telemetry when hidden and reconciles once on return',()=>{
    rows[0].next(snapshot());visibility('hidden');expect(facade.units()).toEqual([]);expect(facade.suspended()).toBe(true);
    groups[0].next([{id:unit.driverId,name:'Late group'}]);advance(60000);expect(facade.groups()).toEqual([]);expect(rows).toHaveLength(1);
    visibility('visible');visibility('visible');expect(rows).toHaveLength(2);expect(realtime.connect).toHaveBeenCalledTimes(2);
  });
  it('ignores telemetry and group callbacks from another actor and never asks as SUPER_ADMIN',()=>{
    user.set({...actor,role:'SUPER_ADMIN'});TestBed.tick();rows[0].next(snapshot());groups[0].next([{id:unit.driverId,name:'Private group'}]);
    expect(facade.units()).toEqual([]);expect(facade.groups()).toEqual([]);expect(facade.state()).toBe('forbidden');expect(rows).toHaveLength(1);
    user.set({...actor,id:'other-admin',organizationId:'other-tenant'});TestBed.tick();expect(rows).toHaveLength(2);
  });
  it('clears the selection after removal and cancels every timer on destruction',()=>{
    rows[0].next(snapshot());facade.select(unit.driverId);facade.refresh();rows[1].next({...snapshot(),items:[]});expect(facade.selected()).toBeNull();
    facade.stop();advance(60000);events.next({event:'driver.changed',occurredAt:unit.capturedAt,data:{}});advance(300);
    expect(rows).toHaveLength(2);expect(facade.units()).toEqual([]);
  });
});
