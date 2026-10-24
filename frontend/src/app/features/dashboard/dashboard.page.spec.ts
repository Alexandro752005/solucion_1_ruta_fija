import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EMPTY, of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DashboardPage } from './dashboard.page';
import { DashboardApiService } from '../../core/dashboard/dashboard-api.service';
import { OperationsApiService } from '../../core/operations/operations-api.service';
import { AuditApiService } from '../../core/audit/audit-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';

describe('Role-scoped dashboard priorities (API doubles)',()=>{
  afterEach(()=>TestBed.resetTestingModule());
  async function setup(platform=false,failed=false){
    const summary={generatedAt:'2026-10-21T12:00:00Z',upcomingUntil:'2026-10-22T12:00:00Z',priorities:[{order:1,code:'CRITICAL_INCIDENTS',count:1,route:'/incidents'},{order:3,code:'UPCOMING_UNACCEPTED',count:2,route:'/assignments'}],servicesInProgress:0,activeDrivers:0,activeVehicles:0};
    const dash={admin:vi.fn(()=>of(summary)),platform:vi.fn(()=>failed?throwError(()=>new Error('SYNTHETIC')):of({generatedAt:summary.generatedAt,organizations:5,activeOrganizations:4,activeAdmins:3,pendingAdmins:1}))};
    const ops={availabilityReport:vi.fn(()=>of({drivers:[],vehicles:[],futureScheduledAssignments:0,assignmentsInService:0,openIncidents:1,generatedAt:summary.generatedAt}))}; const audit={list:vi.fn(()=>of({items:[]}))};
    await TestBed.configureTestingModule({imports:[DashboardPage],providers:[provideRouter([]),{provide:DashboardApiService,useValue:dash},{provide:OperationsApiService,useValue:ops},{provide:AuditApiService,useValue:audit},{provide:AuthSessionService,useValue:{hasAnyRole:(roles:string[])=>roles.includes(platform?'SUPER_ADMIN':'ADMIN'),user:()=>({organizationName:'SYNTHETIC ONLY'})}},{provide:OperationRealtimeService,useValue:{events:EMPTY,connect:()=>{},disconnect:()=>{}}}]}).compileComponents();
    const f=TestBed.createComponent(DashboardPage);f.detectChanges();return{f,dash,ops,audit};
  }
  it('places critical safety before unaccepted service and secondary activity',async()=>{const{f,dash}=await setup();const text=f.nativeElement.textContent as string;expect(text.indexOf('Atención prioritaria')).toBeLessThan(text.indexOf('Estado operativo'));expect(text.indexOf('Incidencias críticas abiertas')).toBeLessThan(text.indexOf('Servicios próximos'));expect(dash.admin).toHaveBeenCalledOnce();expect(dash.platform).not.toHaveBeenCalled();f.destroy();});
  it('SUPER_ADMIN only requests platform aggregate data',async()=>{const{f,dash,ops,audit}=await setup(true);expect(dash.platform).toHaveBeenCalledOnce();expect(dash.admin).not.toHaveBeenCalled();expect(ops.availabilityReport).not.toHaveBeenCalled();expect(audit.list).not.toHaveBeenCalled();expect(f.nativeElement.textContent).toContain('Activaciones ADMIN pendientes');f.destroy();});
  it('does not fall back to tenant endpoints or fabricate zero on platform failure',async()=>{const{f,ops,audit}=await setup(true,true);expect(f.nativeElement.textContent).toContain('No disponible');expect(ops.availabilityReport).not.toHaveBeenCalled();expect(audit.list).not.toHaveBeenCalled();f.destroy();});
});
