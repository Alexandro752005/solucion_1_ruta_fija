import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';
import { Assignment, AssignmentPayload } from './operations.models';

export interface ScheduledService {
  readonly id:string; readonly status:string; readonly effectiveStatus:string; readonly version:number;
  readonly scheduledAt:string; readonly scheduledEndAt:string; readonly routeVersionId:string;
  readonly serviceTypeVersionId:string; readonly currentOfferId?:string|null;
  readonly cancelledAt?:string|null; readonly cancellationReason?:string|null;
  readonly originalStartedAt?:string|null; readonly startOverdue?:boolean;
}
export interface ScheduledServiceView {readonly service:ScheduledService;readonly offers:readonly Assignment[];}
export interface ServiceReceipt {readonly id:string;readonly serviceId:string;readonly operationKey:string;
  readonly action:'REOFFER'|'CANCEL_SERVICE';readonly resultingOfferId?:string|null;readonly resultingVersion:number;readonly occurredAt:string;}
export interface ServiceCommand {readonly expectedVersion:number;readonly operationKey:string;readonly reason:string;}
@Injectable({providedIn:'root'})
export class ScheduledServicesApiService {
  private readonly http=inject(HttpClient);private readonly config=inject(RuntimeConfigService);
  private url(path=''){return this.config.apiUrl('/scheduled-services'+path);}
  list(offset=0):Observable<readonly ScheduledService[]>{return this.http.get<readonly ScheduledService[]>(this.url(),{params:{limit:100,offset}});}
  get(id:string):Observable<ScheduledServiceView>{return this.http.get<ScheduledServiceView>(this.url('/'+encodeURIComponent(id)));}
  operation(key:string):Observable<ServiceReceipt>{return this.http.get<ServiceReceipt>(this.url('/operations/'+encodeURIComponent(key)));}
  reoffer(id:string,c:ServiceCommand&{readonly offer:AssignmentPayload}):Observable<ScheduledServiceView>{return this.http.post<ScheduledServiceView>(this.url('/'+encodeURIComponent(id)+'/offers'),c,this.options());}
  cancel(id:string,c:ServiceCommand):Observable<ScheduledServiceView>{return this.http.post<ScheduledServiceView>(this.url('/'+encodeURIComponent(id)+'/cancel'),c,this.options());}
  private options(){return {context:new HttpContext().set(SKIP_AUTH_REFRESH,true)};}
}
