import {TestBed} from '@angular/core/testing';
import {of,throwError} from 'rxjs';
import {afterEach,describe,expect,it,vi} from 'vitest';
import {AuditPage} from './audit.page';
import {AuditApiService} from '../../core/audit/audit-api.service';
import {AuthSessionService} from '../../core/auth/auth-session.service';
import {ApiErrorService} from '../../core/api/api-error.service';
import {AuditEvent} from '../../core/audit/audit.models';

describe('Audit views by authorized scope (API doubles)',()=>{
 afterEach(()=>TestBed.resetTestingModule());
 async function setup(platform:boolean,failed=false){
  const event:AuditEvent={id:'synthetic-event',userId:'synthetic-user',action:'SECURITY_ACCESS_DENIED',metadata:{httpStatus:403},occurredAt:'2026-10-21T00:00:00Z',scope:platform?'PLATFORM':'ORGANIZATION',outcome:'DENIED'};
  const page={items:[event],page:0,size:20,totalItems:1,totalPages:1};
  const api={list:vi.fn(()=>of(page)),get:vi.fn(()=>of(event)),platformList:vi.fn(()=>failed?throwError(()=>new Error('SYNTHETIC_FAILURE')):of(page)),platformGet:vi.fn(()=>of(event))};
  await TestBed.configureTestingModule({imports:[AuditPage],providers:[{provide:AuditApiService,useValue:api},{provide:AuthSessionService,useValue:{hasAnyRole:()=>platform,user:()=>({id:'synthetic-user',organizationName:'SYNTHETIC ONLY'})}},{provide:ApiErrorService,useValue:{toUiError:()=>({message:'No fue posible consultar'})}}]}).compileComponents();
  const f=TestBed.createComponent(AuditPage);f.detectChanges();return{f,p:f.componentInstance,api,event};
 }
 it('ADMIN consulta solo la vista de su organización',async()=>{const{f,api}=await setup(false);expect(api.list).toHaveBeenCalledOnce();expect(api.platformList).not.toHaveBeenCalled();expect(f.nativeElement.textContent).toContain('Auditoría de organización');f.destroy();});
 it('SUPER_ADMIN usa exclusivamente endpoints de plataforma y detalle propio',async()=>{const{f,p,api,event}=await setup(true);p.select(event);f.detectChanges();expect(api.platformList).toHaveBeenCalledOnce();expect(api.platformGet).toHaveBeenCalledExactlyOnceWith(event.id);expect(api.list).not.toHaveBeenCalled();expect(api.get).not.toHaveBeenCalled();expect(f.nativeElement.textContent).toContain('Auditoría de plataforma');expect(f.nativeElement.textContent).toContain('Denegado');f.destroy();});
 it('no usa auditoría operativa como respaldo ante un fallo de plataforma',async()=>{const{f,p,api}=await setup(true,true);expect(p.error()?.message).toBe('No fue posible consultar');expect(api.list).not.toHaveBeenCalled();expect(api.get).not.toHaveBeenCalled();f.destroy();});
});
