import {TestBed} from '@angular/core/testing';
import {of,throwError} from 'rxjs';
import {HttpErrorResponse} from '@angular/common/http';
import {afterEach,describe,expect,it,vi} from 'vitest';
import {NoticesPage} from './notices.page';
import {CommunicationApiService,OperationNotice} from '../../core/operations/communication-api.service';
import {AuthSessionService} from '../../core/auth/auth-session.service';
import {ApiErrorService} from '../../core/api/api-error.service';
describe('Durable notices (API double)',()=>{
 afterEach(()=>TestBed.resetTestingModule());
 async function setup(){
  const notice:OperationNotice={id:'synthetic-notice',userId:'synthetic-admin',eventType:'assignment.pending-response',references:{assignmentId:'synthetic-offer'},createdAt:'2026-10-20T12:00:00Z',externalStatus:'NOT_CONFIGURED',externalReason:'PROVIDER_AND_DEVICE_NOT_PREPARED'};
  const api={inbox:vi.fn(()=>of({items:[notice],page:0,totalPages:1,totalItems:1,size:20})),readNotice:vi.fn((_id:string)=>of({...notice,readAt:'2026-10-20T12:01:00Z'}))};
  await TestBed.configureTestingModule({imports:[NoticesPage],providers:[{provide:CommunicationApiService,useValue:api},{provide:AuthSessionService,useValue:{user:()=>({id:'synthetic-admin'})}},{provide:ApiErrorService,useValue:{toUiError:()=>({message:'SYNTHETIC unknown outcome'})}}]}).compileComponents();const f=TestBed.createComponent(NoticesPage);f.detectChanges();return {f,p:f.componentInstance,api,notice};
 }
 it('does not mark reading while listing and clearly shows no external delivery',async()=>{const {f,api}=await setup();expect(api.readNotice).not.toHaveBeenCalled();expect(f.nativeElement.textContent).toContain('Entrega externa no realizada');expect(f.nativeElement.textContent).not.toContain('Proveedor aceptó');f.destroy();});
 it('only the explicit action marks the selected notice and does not acknowledge a message',async()=>{const {f,p,api,notice}=await setup();p.read(notice);expect(api.readNotice).toHaveBeenCalledExactlyOnceWith(notice.id);expect(p.rows()[0]?.readAt).toBe('2026-10-20T12:01:00Z');f.destroy();});
 it('queries an uncertain read without retrying its POST',async()=>{const {f,p,api,notice}=await setup();api.readNotice.mockImplementation(()=>throwError(()=>new HttpErrorResponse({status:504})) as never);p.read(notice);p.read(notice);expect(api.readNotice).toHaveBeenCalledTimes(1);expect(p.pending()).toBe(notice.id);expect(api.inbox.mock.calls.length).toBeGreaterThan(1);f.destroy();});
});
