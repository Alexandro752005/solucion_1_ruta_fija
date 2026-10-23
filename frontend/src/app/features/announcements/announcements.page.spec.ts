import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {NEVER,of,Subject} from 'rxjs';
import {afterEach,describe,expect,it,vi} from 'vitest';
import {AnnouncementsPage} from './announcements.page';
import {CommunicationApiService} from '../../core/operations/communication-api.service';
import {OperationsApiService} from '../../core/operations/operations-api.service';
import {OperationRealtimeService} from '../../core/operations/operation-realtime.service';
import {AuthSessionService} from '../../core/auth/auth-session.service';
import {ManagementApiService} from '../../core/management/management-api.service';
import {ApiErrorService} from '../../core/api/api-error.service';
import {AnnouncementPayload} from '../../core/operations/operations.models';
describe('Announcement publication (API doubles)',()=>{
 afterEach(()=>TestBed.resetTestingModule());
 async function setup(){
  const empty={items:[],page:0,size:20,totalPages:0,totalItems:0};const response=new Subject<never>();let publication:unknown={};
  const api={listAnnouncements:()=>of(empty),createAnnouncement:vi.fn((_p:AnnouncementPayload)=>response)};
  const communications={publication:vi.fn(()=>of(publication)),attention:()=>of({items:[],recipients:0,pending:0})};
  await TestBed.configureTestingModule({imports:[AnnouncementsPage],providers:[
   {provide:OperationsApiService,useValue:api},{provide:CommunicationApiService,useValue:communications},
   {provide:ManagementApiService,useValue:{listGroups:()=>of(empty)}},
   {provide:AuthSessionService,useValue:{hasAnyRole:()=>true,user:()=>({id:'synthetic-admin'})}},
   {provide:ApiErrorService,useValue:{toUiError:()=>({message:'SYNTHETIC response not confirmed',fieldErrors:[]})}},
   {provide:OperationRealtimeService,useValue:{connect(){},disconnect(){},events:NEVER}},
  ]}).compileComponents();const f=TestBed.createComponent(AnnouncementsPage);f.detectChanges();const p=f.componentInstance;p.startCreate();p.form.patchValue({title:'SYNTHETIC title',body:'SYNTHETIC body'});f.detectChanges();
  return {f,p,api,response,communications,setPublication:(v:unknown)=>publication=v};
 }
 it('starts without a checked read acknowledgement requirement and publishes the explicit value',async()=>{
  const {f,p,api}=await setup();expect(p.form.controls.requireReadAck.value).toBe(false);p.form.controls.requireReadAck.setValue(true);p.submit();
  expect(api.createAnnouncement).toHaveBeenCalledWith(expect.objectContaining({requireReadAck:true,operationKey:expect.any(String)}));f.destroy();
 });
 it('keeps an unknown publication frozen and reconciles its own operation without a second POST',async()=>{
  const {f,p,api,response,setPublication}=await setup();p.submit();const key=api.createAnnouncement.mock.calls[0]![0].operationKey;response.error(new HttpErrorResponse({status:504}));
  p.closeForm();p.submit();expect(p.formOpen()).toBe(true);expect(p.uncertain()).toBe(true);expect(api.createAnnouncement).toHaveBeenCalledTimes(1);
  setPublication({operationKey:key,announcementId:'synthetic-announcement',createdAt:'2026-10-20T12:00:00Z'});p.queryPublication();expect(p.uncertain()).toBe(false);expect(p.formOpen()).toBe(false);expect(api.createAnnouncement).toHaveBeenCalledTimes(1);f.destroy();
 });
 it('does not treat a different operation receipt as confirmation',async()=>{
  const {f,p,response,setPublication}=await setup();p.submit();response.error(new HttpErrorResponse({status:0}));setPublication({operationKey:'different',announcementId:'other'});p.queryPublication();expect(p.uncertain()).toBe(true);expect(p.formOpen()).toBe(true);f.destroy();
 });
 it('retains fields after a definite validation rejection',async()=>{
  const {f,p,response}=await setup();p.submit();response.error(new HttpErrorResponse({status:400}));expect(p.uncertain()).toBe(false);expect(p.form.controls.body.value).toBe('SYNTHETIC body');expect(p.formOpen()).toBe(true);f.destroy();
 });
});
