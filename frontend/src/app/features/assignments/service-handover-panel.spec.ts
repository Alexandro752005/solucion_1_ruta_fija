import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting,HttpTestingController } from '@angular/common/http/testing';
import { ServiceHandoverPanel } from './service-handover-panel';
import { Assignment } from '../../core/operations/operations.models';
import { Handover,HandoverView } from '../../core/operations/service-handover-api.service';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';
describe('L05 ADMIN prepares standby and never assumes for CONDUCTOR',()=>{
 let http:HttpTestingController;
 const view:HandoverView={service:{id:'synthetic-service',status:'IN_SERVICE',effectiveStatus:'IN_SERVICE',version:7,scheduledAt:'2030-01-01T10:00:00Z',scheduledEndAt:'2030-01-01T14:00:00Z',routeVersionId:'synthetic-route',serviceTypeVersionId:'synthetic-type',currentOfferId:'synthetic-outgoing',originalStartedAt:'2030-01-01T10:01:00Z'},handovers:[]};
 const h:Handover={id:'synthetic-handover',operationKey:'synthetic-key',outgoingAssignmentId:'synthetic-outgoing',incomingAssignmentId:'synthetic-incoming',incomingDriverId:'synthetic-driver',incomingVehicleId:'synthetic-unit',preparedBy:'synthetic-admin',reason:'SYNTHETIC',pointReference:'SYNTHETIC_TRANSFER_POINT',preparedAt:'2030-01-01T11:00:00Z',state:'ACCEPTED'};
 beforeEach(()=>{TestBed.configureTestingModule({imports:[ServiceHandoverPanel],providers:[provideHttpClient(),provideHttpClientTesting()]});http=TestBed.inject(HttpTestingController);});afterEach(()=>http.verify());
 function setup(v=view){const f=TestBed.createComponent(ServiceHandoverPanel);f.componentRef.setInput('assignment',{id:'synthetic-outgoing',serviceId:'synthetic-service'} as Assignment);f.detectChanges();http.expectOne('/api/v1/scheduled-services/synthetic-service/handovers').flush(v);return f;}
 function fill(c:ServiceHandoverPanel){c.from='2030-01-01T12:00';c.to='2030-01-01T14:00';c.reason='SYNTHETIC safe transfer';c.pointReference='SYNTHETIC_TRANSFER_POINT';c.selectedPair='synthetic-period';c.candidates.set([{driverId:'synthetic-driver',vehicleId:'synthetic-unit',periodId:'synthetic-period'}]);}
 it('requires explicit safety and sends only a preparation with captured service scope',()=>{
  const c=setup().componentInstance;fill(c);c.prepare();http.expectNone(r=>r.method==='POST');expect(c.safety).toBe(false);c.safety=true;c.prepare();const p=http.expectOne('/api/v1/scheduled-services/synthetic-service/handovers');expect(p.request.body.expectedVersion).toBe(7);expect(p.request.body.driverId).toBe('synthetic-driver');expect(p.request.body.routeVersionId).toBeUndefined();expect(p.request.body.safetyAndTransferConfirmed).toBe(true);expect(p.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);p.flush({...view,handovers:[h]});expect(c.safety).toBe(false);http.expectNone(r=>r.url.includes('/assume-handover'));
 });
 it('consults coded resource pairs and invalidates them when the interval changes',()=>{
  const c=setup().componentInstance;fill(c);c.queryCandidates();const r=http.expectOne(x=>x.url.endsWith('/handover-candidates'));expect(r.request.params.get('from')).toBe(new Date(c.from).toISOString());r.flush({items:[{driverId:'synthetic-driver',vehicleId:'synthetic-unit',periodId:'synthetic-period'}],nextOffset:null});expect(c.candidates().length).toBe(1);c.to='2030-01-01T15:00';c.invalidateCandidates();expect(c.candidates().length).toBe(0);expect(c.selectedPair).toBe('');
 });
 it('resolves an uncertain preparation by key without a second post',()=>{
  const c=setup().componentInstance;fill(c);c.safety=true;c.prepare();const p=http.expectOne(r=>r.method==='POST'),key=p.request.body.operationKey;p.flush({}, {status:504,statusText:'Gateway Timeout'});http.expectOne('/api/v1/scheduled-services/synthetic-service/handovers').flush(view);expect(c.uncertain()).toBe(true);c.prepare();http.expectNone(r=>r.method==='POST');c.read();http.expectOne('/api/v1/scheduled-services/synthetic-service/handovers').flush({...view,handovers:[{...h,operationKey:key}]});expect(c.uncertain()).toBe(false);
 });
 it('requires a separate withdrawal confirmation and preserves the outgoing service',()=>{
  const c=setup({...view,handovers:[h]}).componentInstance;c.withdrawReason='SYNTHETIC standby withdrawal';c.withdraw(h);http.expectNone(r=>r.method==='POST');c.withdrawConfirmed=true;c.withdraw(h);const p=http.expectOne('/api/v1/service-handovers/synthetic-handover/withdraw');expect(p.request.body.expectedVersion).toBe(7);expect(p.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);p.flush({...view,handovers:[{...h,state:'WITHDRAWN'}]});expect(c.view()?.service.status).toBe('IN_SERVICE');expect(c.withdrawConfirmed).toBe(false);
 });
 it('neither terminal services nor another current participant expose a new preparation',()=>{
  const c=setup({...view,service:{...view.service,status:'COMPLETED'}}).componentInstance;expect(c.canPrepare()).toBe(false);c.view.set({...view,service:{...view.service,currentOfferId:'another-offer'}});expect(c.canPrepare()).toBe(false);fill(c);c.safety=true;c.prepare();http.expectNone(r=>r.method==='POST');
 });
});
