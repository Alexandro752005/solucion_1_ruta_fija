import {HttpErrorResponse} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {of, Subject, throwError} from 'rxjs';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {ApiErrorService} from '../../core/api/api-error.service';
import {AuthSessionService} from '../../core/auth/auth-session.service';
import {IncidentWorkflowApiService} from '../../core/operations/incident-workflow-api.service';
import {Incident} from '../../core/operations/operations.models';
import {IncidentWorkflowPanel} from './incident-workflow-panel';

describe('Incident attention controls (API doubles)', () => {
  afterEach(() => TestBed.resetTestingModule());
  async function setup(status: Incident['status'] = 'OPEN') {
    let rows: unknown[] = [];
    const reply = new Subject<Incident>();
    const api = {history: vi.fn(() => of(rows)), candidates: () => of([{adminId:'synthetic-admin',label:'CUENTA-synthetic-admin'}]), act: vi.fn((_id:string,_kind:string,_body:Readonly<Record<string,unknown>>) => reply)};
    await TestBed.configureTestingModule({imports:[IncidentWorkflowPanel],providers:[
      {provide:IncidentWorkflowApiService,useValue:api},
      {provide:AuthSessionService,useValue:{user:()=>({id:'synthetic-actor'})}},
      {provide:ApiErrorService,useValue:{toUiError:()=>({message:'Resultado sintético sin confirmar'})}},
    ]}).compileComponents();
    const f=TestBed.createComponent(IncidentWorkflowPanel);
    f.componentRef.setInput('incident',{id:'synthetic-incident',version:3,status,severity:'LOW',operationalEffect:'NO_INTERRUPTION_RECORDED'});
    const changed=vi.fn(), blocked=vi.fn();
    f.componentInstance.changed.subscribe(changed); f.componentInstance.blocked.subscribe(blocked);
    f.detectChanges();
    return {f,p:f.componentInstance,api,reply,changed,blocked,setRows:(v:unknown[])=>{rows=v;}};
  }
  it('requires explicit confirmation and records only coded ADMIN responsibility',async()=>{
    const {f,p,api}=await setup(); p.adminId='synthetic-admin';p.reason='SYNTHETIC checked assignment';
    p.submit();expect(api.act).not.toHaveBeenCalled();p.confirmed=true;p.submit();
    expect(api.act).toHaveBeenCalledWith('synthetic-incident','responsibility',expect.objectContaining({adminId:'synthetic-admin',expectedVersion:3,operationKey:expect.any(String)}));
    expect(f.nativeElement.textContent).toContain('CUENTA-synthetic-admin');f.destroy();
  });
  it('requires structured closure and justification for unavailable evidence',async()=>{
    const {p,api,f}=await setup();p.kind='closure';p.confirmed=true;p.diagnosis='SYNTHETIC diagnosis';p.measures='SYNTHETIC actions';p.result='SYNTHETIC result';p.evidenceRequired='YES';p.evidenceMode='NOT_REQUIRED';
    p.submit();expect(api.act).not.toHaveBeenCalled();p.evidenceMode='UNAVAILABLE_JUSTIFIED';p.submit();expect(api.act).not.toHaveBeenCalled();
    p.evidenceExplanation='SYNTHETIC inability checked';p.submit();expect(api.act).toHaveBeenCalledWith('synthetic-incident','closure',expect.objectContaining({evidenceRequired:true,evidenceMode:'UNAVAILABLE_JUSTIFIED',evidenceExplanation:p.evidenceExplanation}));f.destroy();
  });
  it('does not expose a mutation for a terminal case',async()=>{
    const {p,f,api}=await setup('CLOSED_BY_DRIVER');p.confirmed=true;p.reason='SYNTHETIC';p.adminId='synthetic-admin';p.submit();
    expect(f.nativeElement.querySelector('#incident-action')).toBeNull();expect(api.act).not.toHaveBeenCalled();f.destroy();
  });
  it('reconciles an uncertain action by actor and operation key without another POST',async()=>{
    const {p,f,api,reply,changed,setRows}=await setup();p.adminId='synthetic-admin';p.reason='SYNTHETIC';p.confirmed=true;p.submit();
    const key=api.act.mock.calls[0]![2]['operationKey'];reply.error(new HttpErrorResponse({status:504}));
    expect(p.uncertain()).toBe(true);p.submit();expect(api.act).toHaveBeenCalledTimes(1);
    setRows([{id:'synthetic-action',operationKey:key,actorId:'synthetic-actor',kind:'RESPONSIBILITY',detail:{},recordedAt:'2026-10-20T12:00:00Z',resultingVersion:4}]);p.read();
    expect(p.uncertain()).toBe(false);expect(changed).toHaveBeenCalledOnce();expect(api.act).toHaveBeenCalledTimes(1);f.destroy();
  });
  it('keeps uncertainty when history belongs to another actor or key',async()=>{
    const {p,f,api,reply,changed,setRows}=await setup();p.adminId='synthetic-admin';p.reason='SYNTHETIC';p.confirmed=true;p.submit();
    const key=api.act.mock.calls[0]![2]['operationKey'];reply.error(new HttpErrorResponse({status:0}));
    setRows([{id:'different-action',operationKey:key,actorId:'different-actor',kind:'RESPONSIBILITY',detail:{},recordedAt:'2026-10-20T12:00:00Z',resultingVersion:4}]);p.read();
    expect(p.uncertain()).toBe(true);expect(changed).not.toHaveBeenCalled();f.destroy();
  });
  it('keeps entered fields after a definite stale version rejection',async()=>{
    const {p,f,api,changed}=await setup();api.act.mockImplementation(()=>throwError(()=>new HttpErrorResponse({status:409})) as never);
    p.adminId='synthetic-admin';p.reason='SYNTHETIC retained reason';p.confirmed=true;p.submit();
    expect(p.uncertain()).toBe(false);expect(p.reason).toBe('SYNTHETIC retained reason');expect(changed).not.toHaveBeenCalled();f.destroy();
  });
});
