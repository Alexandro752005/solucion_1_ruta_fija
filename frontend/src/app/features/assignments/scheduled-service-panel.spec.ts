import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ScheduledServicePanel } from './scheduled-service-panel';
import { Assignment } from '../../core/operations/operations.models';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';

describe('L04 durable service panel',()=>{
  let http:HttpTestingController;
  beforeEach(()=>{TestBed.configureTestingModule({imports:[ScheduledServicePanel],providers:[provideHttpClient(),provideHttpClientTesting()]});http=TestBed.inject(HttpTestingController);});
  afterEach(()=>http.verify());
  const service={id:'synthetic-service',version:1,status:'CONFIRMED',effectiveStatus:'CONFIRMED',currentOfferId:'synthetic-offer',scheduledAt:'2030-01-01T12:00:00Z',scheduledEndAt:'2030-01-01T13:00:00Z'};
  function setup(){const f=TestBed.createComponent(ScheduledServicePanel);f.componentRef.setInput('assignment',{id:'synthetic-offer',serviceId:service.id} as Assignment);f.detectChanges();http.expectOne('/api/v1/scheduled-services/'+service.id).flush({service,offers:[]});return f;}
  it('distinguishes cancellation of the whole service and requires an explicit confirmation',()=>{
    const c=setup().componentInstance;c.reason='SYNTHETIC-CANCEL';c.cancel();http.expectNone(r=>r.method==='POST');
    c.confirmed=true;c.cancel();const r=http.expectOne('/api/v1/scheduled-services/'+service.id+'/cancel');
    expect(r.request.body.expectedVersion).toBe(1);expect(r.request.body.operationKey).toBeTruthy();
    expect(r.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    r.flush({service:{...service,status:'CANCELLED',effectiveStatus:'CANCELLED'},offers:[]});expect(c.canChange()).toBe(false);
  });
  it('preserves an uncertain cancellation and does not replay after a missing receipt',()=>{
    const c=setup().componentInstance;c.reason='SYNTHETIC-CANCEL';c.confirmed=true;c.cancel();
    http.expectOne(r=>r.method==='POST').flush({}, {status:504,statusText:'Gateway Timeout'});
    http.expectOne('/api/v1/scheduled-services/'+service.id).flush({service,offers:[]});
    expect(c.uncertain()).toBe(true);c.confirmed=true;c.cancel();http.expectNone(r=>r.method==='POST');
    c.reconcile();http.expectOne(r=>r.url.includes('/operations/')).flush({}, {status:404,statusText:'Not Found'});
    expect(c.uncertain()).toBe(true);
  });
  it('reconciles an operation receipt before enabling more actions',()=>{
    const c=setup().componentInstance;c.reason='SYNTHETIC-CANCEL';c.confirmed=true;c.cancel();
    http.expectOne(r=>r.method==='POST').flush({}, {status:0,statusText:'Unknown Error'});
    http.expectOne('/api/v1/scheduled-services/'+service.id).flush({service,offers:[]});c.reconcile();
    http.expectOne(r=>r.url.includes('/operations/')).flush({serviceId:service.id,action:'CANCEL_SERVICE'});
    http.expectOne('/api/v1/scheduled-services/'+service.id).flush({service:{...service,status:'CANCELLED',effectiveStatus:'CANCELLED'},offers:[]});
    expect(c.uncertain()).toBe(false);expect(c.canChange()).toBe(false);
  });
  it('cannot dispatch using an older offer from the history',()=>{
    const c=setup().componentInstance;c.view.set({service:{...service,currentOfferId:'new-current-offer'} as never,offers:[]});
    expect(c.canChange()).toBe(false);c.requestOffer();http.expectNone(r=>r.method==='POST');
  });
  it('does not certify or mutate legacy rows without service membership',()=>{
    const f=TestBed.createComponent(ScheduledServicePanel);f.componentRef.setInput('assignment',{id:'synthetic-legacy'} as Assignment);f.detectChanges();
    expect(f.componentInstance.canChange()).toBe(false);http.expectNone(r=>true);
  });
  it('allows explicit cancellation of interrupted continuity without a generic reoffer',()=>{
    const c=setup().componentInstance;c.view.set({service:{...service,status:'INTERRUPTED',effectiveStatus:'INTERRUPTED'} as never,offers:[]});expect(c.canChange()).toBe(false);expect(c.canCancel()).toBe(true);c.requestOffer();http.expectNone(r=>r.method==='POST');
    c.confirmed=true;c.reason='SYNTHETIC cancellation of continuity';c.cancel();http.expectOne('/api/v1/scheduled-services/'+service.id+'/cancel').flush({service:{...service,status:'CANCELLED',effectiveStatus:'CANCELLED'},offers:[]});expect(c.canCancel()).toBe(false);
  });
});
