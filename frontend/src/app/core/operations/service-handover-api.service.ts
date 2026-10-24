import { inject,Injectable } from '@angular/core';
import { HttpClient,HttpContext } from '@angular/common/http';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';
import { ScheduledService } from './scheduled-services-api.service';
export interface Handover {readonly id:string;readonly operationKey:string;readonly outgoingAssignmentId:string;readonly incomingAssignmentId:string;readonly incomingDriverId:string;readonly incomingVehicleId:string;readonly preparedBy:string;readonly reason:string;readonly pointReference:string;readonly preparedAt:string;readonly state:string;readonly resolvedAt?:string|null;readonly resolvedBy?:string|null;readonly resolutionKey?:string|null;readonly pauseSeconds?:number|null;}
export interface HandoverView {readonly service:ScheduledService;readonly handovers:readonly Handover[];}
export interface HandoverCandidate {readonly driverId:string;readonly vehicleId:string;readonly periodId:string;}
@Injectable({providedIn:'root'})
export class ServiceHandoverApiService {
 private readonly http=inject(HttpClient);private readonly config=inject(RuntimeConfigService);
 private opts(){return {context:new HttpContext().set(SKIP_AUTH_REFRESH,true)};}
 list(service:string){return this.http.get<HandoverView>(this.config.apiUrl('/scheduled-services/'+encodeURIComponent(service)+'/handovers'));}
 candidates(service:string,from:string,to:string,offset=0){return this.http.get<{readonly items:readonly HandoverCandidate[];readonly nextOffset:number|null}>(this.config.apiUrl('/scheduled-services/'+encodeURIComponent(service)+'/handover-candidates'),{params:{from,to,offset}});}
 prepare(service:string,c:{readonly operationKey:string;readonly expectedVersion:number;readonly driverId:string;readonly vehicleId:string;readonly scheduledAt:string;readonly scheduledEndAt:string;readonly reason:string;readonly pointReference:string;readonly safetyAndTransferConfirmed:true}){return this.http.post<HandoverView>(this.config.apiUrl('/scheduled-services/'+encodeURIComponent(service)+'/handovers'),c,this.opts());}
 withdraw(id:string,c:{readonly operationKey:string;readonly expectedVersion:number;readonly reason:string}){return this.http.post<HandoverView>(this.config.apiUrl('/service-handovers/'+encodeURIComponent(id)+'/withdraw'),c,this.opts());}
}
