import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthSessionService } from '../../../core/auth/auth-session.service';
import { SessionUser } from '../../../core/auth/auth.models';
import { ServiceReportEmission, ServiceReportView } from '../../../core/reports/service-reports.models';
import { DashboardReportFacade } from './dashboard-report.facade';

const day = '2026-10-26', zone = 'America/Lima';
const actorId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', orgId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const emissionId = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', seriesId = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
const current = (): ServiceReportView => ({ ruleVersion:'SYNTHETIC',scope:'ORGANIZATION',from:day,to:day,zoneId:zone,cutoffAt:'2026-10-26T12:00:00Z',totals:{scheduled:0,completed:0,pending:0,cancelled:0,interrupted:0,transferred:0,completionPercent:null},coverage:{excludedLegacyOffers:0,canonicalServicesWithoutFirstOffer:0,notice:''},items:[] });
const emission = (): ServiceReportEmission => ({ id:emissionId,seriesId,revision:1,previousEmissionId:null,issuedAt:'2026-10-26T12:01:00Z',contentSha256:'a'.repeat(64),report:current() });

describe('Dashboard explicit report issuance and persisted downloads (HTTP doubles)', () => {
  let data: DashboardReportFacade, http: HttpTestingController;
  const actor = signal<SessionUser | null>(null);
  beforeEach(() => {
    actor.set({ id:actorId,organizationId:orgId,role:'ADMIN',fullName:'SYNTHETIC' });
    TestBed.configureTestingModule({ providers:[provideHttpClient(),provideHttpClientTesting(),DashboardReportFacade,{provide:AuthSessionService,useValue:{user:actor}}] });
    data=TestBed.inject(DashboardReportFacade);http=TestBed.inject(HttpTestingController);TestBed.tick();
  });
  afterEach(() => { http.verify(); TestBed.resetTestingModule(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
  const open = (rows: readonly ServiceReportEmission[] = []) => {
    data.open(day,zone);
    http.expectOne(request=>request.url==='/api/v1/reports/services').flush(current());
    http.expectOne(request=>request.url==='/api/v1/reports/services/emissions').flush(rows);
  };
  const confirm = () => { open();data.issue();http.expectOne('/api/v1/reports/services/emissions').flush(emission()); };
  it('has no automatic network access and opens with two GET without issuing', () => {
    http.expectNone(()=>true);open();http.expectNone(request=>request.method!=='GET');expect(data.selected()).toBeNull();expect(data.current()?.from).toBe(day);
  });
  it('issues only on command with a fresh operation reference and server-confirmed content', () => {
    open();data.issue();const request=http.expectOne('/api/v1/reports/services/emissions');
    expect(request.request.method).toBe('POST');expect(request.request.body).toMatchObject({from:day,to:day});expect(request.request.body.operationKey).toMatch(/^[a-f\d-]{36}$/);
    request.flush(emission());expect(data.pending()).toBeNull();expect(data.selected()?.contentSha256).toBe('a'.repeat(64));
  });
  it('keeps the exact operation and period after a lost response, close/reopen and recovery', () => {
    open();data.issue();const first=http.expectOne('/api/v1/reports/services/emissions'),command=first.request.body;
    first.error(new ProgressEvent('error'));expect(data.pending()).toEqual(command);
    data.open('2026-10-25',zone);http.expectNone(()=>true);expect(data.day()).toBe(day);
    data.issue();http.expectNone(()=>true);data.recover();const retry=http.expectOne('/api/v1/reports/services/emissions');expect(retry.request.body).toEqual(command);
    retry.flush(emission());expect(data.selected()?.id).toBe(emissionId);expect(data.emissions()).toHaveLength(1);expect(data.pending()).toBeNull();
  });
  it('retains an unresolved reference after an incompatible successful response', () => {
    open();data.issue();const request=http.expectOne('/api/v1/reports/services/emissions'),command=request.request.body;request.flush({...emission(),contentSha256:'invalid'});
    expect(data.pending()).toEqual(command);expect(data.selected()).toBeNull();expect(data.error()).not.toBeNull();
  });
  it('does not emit without a confirmed period or after failed period loading', () => {
    data.issue();http.expectNone(()=>true);data.open(day,zone);
    http.expectOne(request=>request.url==='/api/v1/reports/services').flush({}, {status:403,statusText:'Forbidden'});
    http.expectOne(request=>request.url==='/api/v1/reports/services/emissions'); // forkJoin cancels this sibling.
    data.issue();http.expectNone(request=>request.method==='POST');expect(data.current()).toBeNull();
  });
  it('rejects a report for another period before permitting issuance', () => {
    data.open(day,zone);http.expectOne(request=>request.url==='/api/v1/reports/services').flush({...current(),from:'2026-10-25'});
    http.expectOne(request=>request.url==='/api/v1/reports/services/emissions').flush([]);
    expect(data.current()).toBeNull();data.issue();http.expectNone(request=>request.method==='POST');
  });
  it('reads a selected revision and rejects mismatched content hashes', () => {
    open([emission()]);data.select(emissionId);http.expectOne('/api/v1/reports/services/emissions/'+emissionId).flush({...emission(),contentSha256:'b'.repeat(64)});
    expect(data.selected()).toBeNull();expect(data.error()).not.toBeNull();data.select(emissionId);http.expectOne('/api/v1/reports/services/emissions/'+emissionId).flush(emission());
    expect(data.selected()?.id).toBe(emissionId);http.expectNone(request=>request.method==='POST');
  });
  it('cancels selection when changing the requested day', () => {
    open([emission()]);data.select(emissionId);const old=http.expectOne('/api/v1/reports/services/emissions/'+emissionId);
    data.open('2026-10-25',zone);expect(old.cancelled).toBe(true);
    http.expectOne(request=>request.url==='/api/v1/reports/services').flush({...current(),from:'2026-10-25',to:'2026-10-25'});
    http.expectOne(request=>request.url==='/api/v1/reports/services/emissions').flush([]);expect(data.selected()).toBeNull();
  });
  it('downloads three formats from the same emission without recalculation or a new POST', () => {
    const nativeURL=URL,created=vi.fn(()=> 'blob:synthetic'),revoked=vi.fn();
    vi.stubGlobal('URL',class extends nativeURL {static override createObjectURL=created;static override revokeObjectURL=revoked;});
    const click=vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(()=>{});confirm();
    for(const format of ['csv','xlsx','pdf'] as const){data.download(format);const request=http.expectOne(`/api/v1/reports/services/emissions/${emissionId}/export?format=${format}`);request.flush(new Blob(['SYNTHETIC'],{type:'application/octet-stream'}));}
    expect(click).toHaveBeenCalledTimes(3);expect(created).toHaveBeenCalledTimes(3);expect(data.selected()?.contentSha256).toBe('a'.repeat(64));http.expectNone(request=>request.method==='POST');
  });
  it('rejects an HTML error body without erasing the confirmed selection', () => {
    confirm();data.download('pdf');http.expectOne(`/api/v1/reports/services/emissions/${emissionId}/export?format=pdf`).flush(new Blob(['<html>'],{type:'text/html'}));
    expect(data.error()).not.toBeNull();expect(data.selected()?.id).toBe(emissionId);
  });
  it('clears pending operations and downloads on an actor change', () => {
    open();data.issue();const request=http.expectOne('/api/v1/reports/services/emissions');actor.set({...actor()!,organizationId:emissionId});TestBed.tick();
    expect(request.cancelled).toBe(true);expect(data.pending()).toBeNull();expect(data.selected()).toBeNull();expect(data.day()).toBe('');
  });
  it('does not load or issue for a non ADMIN actor', () => {
    actor.set({...actor()!,role:'SUPER_ADMIN'});TestBed.tick();data.open(day,zone);data.issue();http.expectNone(()=>true);
  });
  it('rejects invalid dates, zones and unknown emission IDs', () => {
    data.open('2026-02-31',zone);data.open(day,'invalid/zone');data.select('unknown');http.expectNone(()=>true);
  });
  it('clears a previously selected emission when the date context becomes unavailable',()=>{
    confirm();data.open('','');expect(data.selected()).toBeNull();expect(data.current()).toBeNull();data.download('pdf');http.expectNone(()=>true);
  });
});
