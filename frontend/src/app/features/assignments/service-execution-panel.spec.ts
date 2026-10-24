import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting,HttpTestingController } from '@angular/common/http/testing';
import { ServiceExecutionPanel } from './service-execution-panel';
import { Assignment } from '../../core/operations/operations.models';
import { GpsAccessRequest } from '../../core/operations/service-execution-api.service';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';

describe('L05 execution and restricted GPS review',()=>{
  let http:HttpTestingController;
  const request={id:'synthetic-request',driverId:'synthetic-driver',serviceId:'synthetic-service',version:0,status:'PENDING',effectiveStatus:'PENDING',fromAt:'2026-10-19T10:00:00Z',toAt:'2026-10-19T10:30:00Z',requestedAt:'2026-10-19T10:31:00Z',reviewDueAt:'2026-10-21T10:31:00Z',grantExpiresAt:null} as GpsAccessRequest;
  beforeEach(()=>{TestBed.configureTestingModule({imports:[ServiceExecutionPanel],providers:[provideHttpClient(),provideHttpClientTesting()]});http=TestBed.inject(HttpTestingController);});
  afterEach(()=>http.verify());
  function finishReads(rows:readonly GpsAccessRequest[]=[request]) {
    http.expectOne(r=>r.url.includes('/scheduled-services/')&&r.url.endsWith('/execution')).flush([]);
    const get=http.expectOne(r=>r.url==='/api/v1/gps/access-requests');expect(get.request.params.get('serviceId')).toBe('synthetic-service');get.flush(rows);
  }
  function setup(){const f=TestBed.createComponent(ServiceExecutionPanel);f.componentRef.setInput('assignment',{id:'synthetic-offer',serviceId:'synthetic-service'} as Assignment);f.detectChanges();finishReads();return f;}
  it('starts with no identity confirmation or stored password and makes no review POST',()=>{
    const f=setup(),c=f.componentInstance;c.select(request);c.review(true);
    expect(c.form.controls.identityConfirmed.value).toBe(false);expect(c.form.controls.password.value).toBe('');http.expectNone(r=>r.method==='POST');
    expect(c.requests()[0].id).toBe('synthetic-request');
  });
  it('requires explicit checks, sends a reauthenticated scope and clears the password immediately',()=>{
    const c=setup().componentInstance;c.select(request);c.form.setValue({identityConfirmed:true,password:'SYNTHETIC_LOCAL_ONLY'});c.review(true);
    const p=http.expectOne('/api/v1/gps/access-requests/synthetic-request/review');expect(p.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    expect(p.request.body.identityConfirmed).toBe(true);expect(p.request.body.verificationReference).toBe('RESTRICTED_IDENTITY_MATCH');
    expect(c.form.controls.password.value).toBe('');p.flush({...request,status:'APPROVED'});finishReads([{...request,status:'APPROVED',effectiveStatus:'APPROVED'}]);
    expect(c.selected()).toBeNull();expect(c.requests()[0].status).toBe('APPROVED');
  });
  it('preserves an uncertain case and consults its state instead of repeating credentials',()=>{
    const c=setup().componentInstance;c.select(request);c.form.setValue({identityConfirmed:true,password:'SYNTHETIC_LOCAL_ONLY'});c.review(true);
    http.expectOne('/api/v1/gps/access-requests/synthetic-request/review').flush({}, {status:504,statusText:'Gateway Timeout'});finishReads();
    expect(c.uncertain()).toBe(true);expect(c.form.controls.password.value).toBe('');c.review(true);http.expectNone(r=>r.method==='POST');
    c.load();finishReads([{...request,status:'APPROVED',effectiveStatus:'APPROVED'}]);expect(c.uncertain()).toBe(false);expect(c.selected()).toBeNull();
  });
  it('shows reauthentication failure without another POST or suppressing the error',()=>{
    const c=setup().componentInstance;c.select(request);c.form.setValue({identityConfirmed:true,password:'SYNTHETIC_WRONG'});c.review(true);
    http.expectOne('/api/v1/gps/access-requests/synthetic-request/review').flush({message:'SYNTHETIC_REAUTH_FAILED'},{status:401,statusText:'Unauthorized'});finishReads();
    expect(c.error()).not.toBeNull();expect(c.uncertain()).toBe(false);expect(c.form.controls.password.value).toBe('');http.expectNone(r=>r.method==='POST');
  });
  it('does not invent execution for legacy records or call any trajectory endpoint',()=>{
    const f=TestBed.createComponent(ServiceExecutionPanel);f.componentRef.setInput('assignment',{id:'synthetic-legacy'} as Assignment);f.detectChanges();
    http.expectNone(r=>r.url.includes('gps'));http.expectNone(r=>r.url.includes('execution'));
    expect(f.nativeElement.textContent).toContain('sin tramos reconstruidos');
  });
});
