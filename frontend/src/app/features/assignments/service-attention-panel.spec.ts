import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting,HttpTestingController } from '@angular/common/http/testing';
import { ServiceAttentionPanel } from './service-attention-panel';
import { Assignment } from '../../core/operations/operations.models';
import { ServiceAttention } from '../../core/operations/service-attention-api.service';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';

describe('L05 explicit interruption and independent resource review',()=>{
 let http:HttpTestingController;
 const row:ServiceAttention={id:'synthetic-attention',operationKey:'synthetic-interruption-key',serviceId:'synthetic-service',assignmentId:'synthetic-offer',segmentId:'synthetic-segment',driverId:'synthetic-driver',vehicleId:'synthetic-unit',incidentId:'synthetic-incident',recordedBy:'synthetic-driver-account',source:'CONDUCTOR',reason:'UNSAFE_BREAKDOWN',detail:'SYNTHETIC unsafe continuation',safeStopConfirmed:false,interruptedAt:'2026-10-20T10:00:00Z',version:0,driverPending:true,vehiclePending:true,resolutions:[]};
 beforeEach(()=>{TestBed.configureTestingModule({imports:[ServiceAttentionPanel],providers:[provideHttpClient(),provideHttpClientTesting()]});http=TestBed.inject(HttpTestingController);});
 afterEach(()=>http.verify());
 function setup(rows:readonly ServiceAttention[]=[]){const f=TestBed.createComponent(ServiceAttentionPanel);f.componentRef.setInput('assignment',{id:'synthetic-offer',serviceId:'synthetic-service',status:'EN_SERVICIO',version:4} as Assignment);f.detectChanges();http.expectOne('/api/v1/scheduled-services/synthetic-service/attention').flush(rows);return f;}
 function fill(c:ServiceAttentionPanel){c.interruptForm.patchValue({reason:'UNSAFE_BREAKDOWN',detail:'SYNTHETIC checked communication'});}
 it('requires explicit verified safe stop and preserves actual server authorship',()=>{
  const c=setup().componentInstance;fill(c);c.interrupt();http.expectNone(r=>r.method==='POST');expect(c.interruptForm.controls.safeStopConfirmed.value).toBe(false);
  c.interruptForm.controls.safeStopConfirmed.setValue(true);c.interrupt();const p=http.expectOne('/api/v1/assignments/synthetic-offer/interrupt');
  expect(p.request.body.expectedVersion).toBe(4);expect(p.request.body.operationKey).toBeTruthy();expect(p.request.body.safeStopConfirmed).toBe(true);expect(p.request.body.recordedBy).toBeUndefined();expect(p.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
  p.flush(row);http.expectOne('/api/v1/scheduled-services/synthetic-service/attention').flush([row]);expect(c.interruptForm.controls.safeStopConfirmed.value).toBe(false);
 });
 it('resolves an uncertain interruption by its exact key without resubmitting',()=>{
  const c=setup().componentInstance;fill(c);c.interruptForm.controls.safeStopConfirmed.setValue(true);c.interrupt();const p=http.expectOne(r=>r.method==='POST'),key=p.request.body.operationKey;
  p.flush({}, {status:504,statusText:'Gateway Timeout'});http.expectOne('/api/v1/scheduled-services/synthetic-service/attention').flush([]);expect(c.uncertain()).toBe(true);c.interrupt();http.expectNone(r=>r.method==='POST');
  c.read();http.expectOne('/api/v1/scheduled-services/synthetic-service/attention').flush([{...row,operationKey:key}]);expect(c.uncertain()).toBe(false);
 });
 it('withdraws the unit independently and keeps its attention pending',()=>{
  const c=setup([row]).componentInstance;c.select(row);expect(c.resolutionForm.controls.releaseDriver.value).toBe(false);expect(c.resolutionForm.controls.safetyAndTransferConfirmed.value).toBe(false);
  c.resolutionForm.patchValue({vehicleDecision:'KEEP_WITHDRAWN',reason:'SYNTHETIC pending repair',safetyAndTransferConfirmed:true});c.resolve();const p=http.expectOne('/api/v1/service-attention/synthetic-attention/resolve');expect(p.request.body.releaseDriver).toBe(false);expect(p.request.body.expectedVersion).toBe(0);expect(p.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
  const withdrawn={...row,version:1,resolutions:[{id:'synthetic-review',operationKey:p.request.body.operationKey,actorId:'synthetic-admin',resourceKind:'VEHICLE',disposition:'WITHDRAWN',reason:'SYNTHETIC pending repair',verificationReference:'SYNTHETIC',recordedAt:'2026-10-20T10:01:00Z'}]};p.flush(withdrawn);http.expectOne('/api/v1/scheduled-services/synthetic-service/attention').flush([withdrawn]);expect(c.rows()[0].vehiclePending).toBe(true);expect(c.wasWithdrawn(withdrawn)).toBe(true);
 });
 it('does not submit an interruption while another service action is unresolved',()=>{
  const f=setup(),c=f.componentInstance;fill(c);c.interruptForm.controls.safeStopConfirmed.setValue(true);f.componentRef.setInput('externalBlocked',true);f.detectChanges();c.interrupt();http.expectNone(r=>r.method==='POST');
 });
});
