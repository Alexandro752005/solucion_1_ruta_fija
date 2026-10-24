import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EMPTY } from 'rxjs';
import { provideRouter } from '@angular/router';
import { AssignmentsPage } from './assignments.page';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import { Assignment } from '../../core/operations/operations.models';

describe('L04 explicit offer acceptance', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ imports:[AssignmentsPage], providers:[provideRouter([]),provideHttpClient(),provideHttpClientTesting(),
      {provide:AuthSessionService,useValue:{user:()=>null}},
      {provide:OperationRealtimeService,useValue:{events:EMPTY,connect:()=>undefined,disconnect:()=>undefined}},
    ] });
    http=TestBed.inject(HttpTestingController);
  });
  afterEach(()=>http.verify());
  function setup() {
    const f=TestBed.createComponent(AssignmentsPage);f.detectChanges();
    for (const r of http.match(r=>r.method==='GET')) r.flush({items:[],page:0,totalPages:0,totalItems:0});
    return f;
  }
  function fill(c:AssignmentsPage) {
    c.formOpen.set(true);
    c.form.setValue({serviceTypeVersionId:'synthetic-type-version',driverId:'',vehicleId:'',routeVersionId:'synthetic-version',
      originText:'Synthetic origin',destinationText:'Synthetic destination',scheduledAt:'2030-01-01T12:00',
      scheduledEndAt:'2030-01-01T13:00',notes:''});
    c.findCandidates();
    http.expectOne(r=>r.url==='/api/v1/assignment-candidates').flush({serviceTypeVersionId:'synthetic-type-version',
      reservationStartsAt:'2030-01-01T11:55:00Z',reservationEndsAt:'2030-01-01T13:05:00Z',nextOffset:null,
      items:[{driverId:'synthetic-driver',vehicleId:'synthetic-unit',periodId:'synthetic-period'}]});
    c.chooseCandidate(c.candidates()[0]);
  }
  it('emits a pending mobile offer without letting CRM set the deadline',()=>{
    const c=setup().componentInstance;fill(c);c.submit();
    const r=http.expectOne('/api/v1/assignments');
    expect(r.request.method).toBe('POST');expect(r.request.body.responseMode).toBe('MOBILE_CONFIRMATION');
    expect(r.request.body.serviceTypeVersionId).toBe('synthetic-type-version');
    expect(r.request.body.responseDeadlineAt).toBeUndefined();expect(r.request.headers.get('Idempotency-Key')).toBeTruthy();
    r.flush({id:'synthetic-offer',status:'PENDING_RESPONSE',responseMode:'MOBILE_CONFIRMATION'});
    http.expectOne(r=>r.url==='/api/v1/assignments').flush({items:[],page:0,totalPages:0,totalItems:0});
    expect(c.notice()).toContain('pendiente de aceptación');
  });
  it('keeps form values after a scheduling conflict without another POST',()=>{
    const c=setup().componentInstance;fill(c);c.submit();
    http.expectOne('/api/v1/assignments').flush({message:'SYNTHETIC_CONFLICT'},{status:409,statusText:'Conflict'});
    expect(c.form.controls.driverId.value).toBe('synthetic-driver');expect(c.formOpen()).toBe(true);
    expect(c.notice()).toBeNull();http.expectNone(r=>r.method==='POST');
  });
  it('never offers CRM reserve or start for pending, accepted or historical direct records',()=>{
    const c=setup().componentInstance;
    for (const item of [{status:'PENDING_RESPONSE',responseMode:'MOBILE_CONFIRMATION'},
      {status:'SCHEDULED',responseMode:'MOBILE_CONFIRMATION',reservedAt:'2030-01-01T10:00:00Z'},
      {status:'SCHEDULED',responseMode:'ADMIN_DIRECT',reservedAt:null}]) {
      const a=item as Assignment;expect(c.canReserve(a)).toBe(false);expect(c.canStart(a)).toBe(false);
      c.reserve(a);c.start(a);expect(c.pendingAction()).toBeNull();
    }
    http.expectNone(r=>r.method==='POST');
  });
  it('keeps legacy material edits disabled until an explicit replacement flow is used',()=>{
    const c=setup().componentInstance;const a={status:'SCHEDULED',responseMode:'ADMIN_DIRECT',reservedAt:null} as Assignment;
    expect(c.canEdit(a)).toBe(false);c.startEdit(a);expect(c.formOpen()).toBe(false);
  });
  it('does not let ADMIN declare route completion for any current or legacy offer',()=>{
    const c=setup().componentInstance;
    for(const serviceId of [null,'synthetic-service']) {
      const a={id:'synthetic-offer',status:'EN_SERVICIO',serviceId} as Assignment;
      expect(c.canComplete(a)).toBe(false);c.complete(a);expect(c.pendingAction()).toBeNull();
    }
    http.expectNone(r=>r.method==='POST');
  });
  it('requires a new candidate query after a schedule change even without a DOM change event',()=>{
    const c=setup().componentInstance;fill(c);c.form.controls.scheduledEndAt.setValue('2030-01-01T14:00');
    c.submit();http.expectNone(r=>r.method==='POST');expect(c.error()?.message).toContain('selecciona un candidato');
  });
  it('ignores a late candidate result after the route or schedule changed',()=>{
    const c=setup().componentInstance;fill(c);c.findCandidates();const old=http.expectOne(r=>r.url==='/api/v1/assignment-candidates');
    c.form.controls.scheduledAt.setValue('2030-01-01T12:30');c.resetCandidates();
    old.flush({items:[{driverId:'old-driver',vehicleId:'old-unit',periodId:'old-period'}],nextOffset:null});
    expect(c.candidates()).toEqual([]);expect(c.form.controls.driverId.value).toBe('');
  });
  it('stops pagination when the server omits the null nextOffset field',()=>{
    const c=setup().componentInstance;fill(c);c.findCandidates();
    http.expectOne(r=>r.url==='/api/v1/assignment-candidates').flush({serviceTypeVersionId:'synthetic-type-version',
      reservationStartsAt:'2030-01-01T11:55:00Z',reservationEndsAt:'2030-01-01T13:05:00Z',items:[]});
    c.findCandidates(true);http.expectNone(r=>r.url==='/api/v1/assignment-candidates');
  });
  it('keeps the durable service when issuing a replacement and reconciles an uncertain response',()=>{
    const c=setup().componentInstance;
    c.selectedAssignment.set({id:'synthetic-current',originText:'Synthetic origin',destinationText:'Synthetic destination'} as Assignment);
    c.startReoffer({id:'synthetic-service',version:4,currentOfferId:'synthetic-current',status:'CONFIRMED',effectiveStatus:'CONFIRMED',serviceTypeVersionId:'synthetic-type-version',routeVersionId:'synthetic-version',scheduledAt:'2030-01-01T12:00:00Z',scheduledEndAt:'2030-01-01T13:00:00Z'});
    for(const r of http.match(r=>r.method==='GET'))r.flush([]);
    fill(c);c.reofferReason.setValue('SYNTHETIC-MATERIAL-CHANGE');c.submit();
    const r=http.expectOne('/api/v1/scheduled-services/synthetic-service/offers');expect(r.request.body.expectedVersion).toBe(4);
    expect(r.request.body.offer.responseMode).toBe('MOBILE_CONFIRMATION');r.flush({}, {status:504,statusText:'Gateway Timeout'});
    expect(c.reofferUncertain()).toBe(true);c.closeForm();expect(c.formOpen()).toBe(true);c.submit();http.expectNone(r=>r.method==='POST');
    c.reconcileReoffer();http.expectOne(r=>r.url.includes('/scheduled-services/operations/')).flush({serviceId:'synthetic-service',action:'REOFFER'});
    http.expectOne(r=>r.url==='/api/v1/assignments').flush({items:[],page:0,totalPages:0,totalItems:0});
    expect(c.formOpen()).toBe(false);expect(c.reofferUncertain()).toBe(false);
  });
  it('does not repost an uncertain initial offer, including when its receipt is still absent',()=>{
    const c=setup().componentInstance;fill(c);c.submit();const r=http.expectOne('/api/v1/assignments');
    const key=r.request.headers.get('Idempotency-Key');r.flush({}, {status:504,statusText:'Gateway Timeout'});
    expect(c.creationUncertain()).toBe(true);c.closeForm();c.startCreate();c.submit();http.expectNone(r=>r.method==='POST');expect(c.formOpen()).toBe(true);
    c.reconcileCreation();http.expectOne('/api/v1/assignments/creation-operations/'+key).flush({}, {status:404,statusText:'Not Found'});
    expect(c.creationUncertain()).toBe(true);c.reconcileCreation();
    http.expectOne('/api/v1/assignments/creation-operations/'+key).flush({id:'synthetic-offer',serviceId:'synthetic-service'});
    http.expectOne(r=>r.url==='/api/v1/assignments').flush({items:[],page:0,totalPages:0,totalItems:0});
    expect(c.creationUncertain()).toBe(false);expect(c.formOpen()).toBe(false);
  });
  it('keeps an uncertain offer cancellation until the server confirms its current state',()=>{
    const c=setup().componentInstance;const a={id:'synthetic-offer',serviceId:'synthetic-service',status:'SCHEDULED',version:2} as Assignment;
    c.cancel(a);c.cancellationForm.controls.reason.setValue('SYNTHETIC-CANCEL');c.submitCancellation();
    http.expectOne('/api/v1/assignments/synthetic-offer/cancel').flush({}, {status:504,statusText:'Gateway Timeout'});
    c.closeCancellation();c.submitCancellation();expect(c.cancellationUncertain()).toBe(true);expect(c.cancellationTarget()).toBe(a);http.expectNone(r=>r.method==='POST');
    c.reconcileCancellation();http.expectOne('/api/v1/assignments/synthetic-offer').flush({...a,status:'CANCELLED'});
    http.expectOne(r=>r.url==='/api/v1/assignments').flush({items:[],page:0,totalPages:0,totalItems:0});expect(c.cancellationUncertain()).toBe(false);
  });
});
