import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting,HttpTestingController } from '@angular/common/http/testing';
import { ServiceTimePanel } from './service-time-panel';
import { Assignment } from '../../core/operations/operations.models';
import { ServiceTimeHistory } from '../../core/operations/service-time-api.service';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';

describe('L05 append-only time correction UI',()=>{
  let http:HttpTestingController;
  const history:ServiceTimeHistory={serviceId:'synthetic-service',status:'COMPLETED',serviceVersion:4,segments:[{segmentId:'synthetic-segment',driverId:'synthetic-driver',vehicleId:'synthetic-unit',ordinal:1,originalStartedAt:'2026-10-19T10:00:00.123456Z',originalEndedAt:'2026-10-19T10:30:00.654321Z',startedAt:'2026-10-19T10:00:00.123456Z',endedAt:'2026-10-19T10:30:00.654321Z'}],corrections:[],requests:[]};
  beforeEach(()=>{TestBed.configureTestingModule({imports:[ServiceTimePanel],providers:[provideHttpClient(),provideHttpClientTesting()]});http=TestBed.inject(HttpTestingController);});
  afterEach(()=>http.verify());
  function setup(){const f=TestBed.createComponent(ServiceTimePanel);f.componentRef.setInput('assignment',{id:'synthetic-offer',serviceId:'synthetic-service'} as Assignment);f.detectChanges();http.expectOne('/api/v1/scheduled-services/synthetic-service/time-history').flush(history);return f;}
  function fill(c:ServiceTimePanel){c.form.controls.reason.setValue('SYNTHETIC timing correction');c.form.controls.confirmed.setValue(true);c.form.controls.times.at(0).controls.end.setValue(c.local('2026-10-19T10:29:00Z'));}
  it('keeps confirmation unchecked and shows originals separately without a mutation',()=>{const f=setup();f.detectChanges();const c=f.componentInstance;c.submit();expect(c.form.controls.confirmed.value).toBe(false);expect(f.nativeElement.textContent).toContain('Original:');expect(f.nativeElement.textContent).toContain('Vigente para revisión:');http.expectNone(r=>r.method==='POST');});
  it('posts the current service version and exact untouched microseconds without auth replay',()=>{
    const c=setup().componentInstance;fill(c);c.submit();const p=http.expectOne('/api/v1/scheduled-services/synthetic-service/time-corrections');
    expect(p.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);expect(p.request.body.expectedVersion).toBe(4);expect(p.request.body.operationKey).toBeTruthy();
    expect(p.request.body.segments[0].startedAt).toBe(history.segments[0].startedAt);expect(p.request.body.segments[0].endedAt).toBe('2026-10-19T10:29:00.000Z');
    p.flush({...history,serviceVersion:5});expect(c.form.controls.confirmed.value).toBe(false);http.expectNone(r=>r.method==='POST');
  });
  it('keeps an uncertain correction and resolves only its exact operation key from history',()=>{
    const c=setup().componentInstance;fill(c);c.submit();const p=http.expectOne('/api/v1/scheduled-services/synthetic-service/time-corrections'),key=p.request.body.operationKey;
    p.flush({}, {status:504,statusText:'Gateway Timeout'});http.expectOne('/api/v1/scheduled-services/synthetic-service/time-history').flush(history);expect(c.uncertain()).toBe(true);c.submit();http.expectNone(r=>r.method==='POST');
    c.load();http.expectOne('/api/v1/scheduled-services/synthetic-service/time-history').flush({...history,serviceVersion:5,corrections:[{id:'synthetic-correction',operationKey:key,revision:1,actorId:'synthetic-admin',correctedAt:'2026-10-19T11:00:00Z',reason:'SYNTHETIC',originalTerminalResult:'CORROBORATED',effectiveTerminalResult:'NOT_CORROBORATED_STALE_OR_IMPRECISE',verificationEffect:'ORIGINALS_RETAINED_EFFECTIVE_TIME_REVIEW',segments:[]}]});expect(c.uncertain()).toBe(false);
  });
  it('respects another pending service action and does not fabricate history for legacy records',()=>{
    const f=setup(),c=f.componentInstance;fill(c);f.componentRef.setInput('externalBlocked',true);f.detectChanges();c.submit();http.expectNone(r=>r.method==='POST');
    f.componentRef.setInput('assignment',{id:'synthetic-legacy'} as Assignment);f.detectChanges();http.expectNone(r=>r.method==='GET');
  });
});
