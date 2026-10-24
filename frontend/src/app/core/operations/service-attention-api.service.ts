import { HttpClient,HttpContext } from '@angular/common/http';
import { inject,Injectable } from '@angular/core';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';
export type SafetyReason='ACCIDENT_RISK'|'UNSAFE_BREAKDOWN'|'IMPOSSIBLE_SERVICE';
export interface SafetyResolution {readonly id:string;readonly operationKey:string;readonly actorId:string;readonly resourceKind:string;readonly disposition:string;readonly reason:string;readonly verificationReference:string;readonly recordedAt:string;}
export interface ServiceAttention {readonly id:string;readonly operationKey:string;readonly serviceId:string;readonly assignmentId:string;readonly segmentId:string;readonly driverId:string;readonly vehicleId:string;readonly incidentId:string;readonly recordedBy:string;readonly source:string;readonly reason:SafetyReason;readonly detail:string;readonly safeStopConfirmed:boolean;readonly interruptedAt:string;readonly version:number;readonly driverPending:boolean;readonly vehiclePending:boolean;readonly resolutions:readonly SafetyResolution[];}
export interface AdminInterruption {readonly operationKey:string;readonly expectedVersion:number;readonly reason:SafetyReason;readonly detail:string;readonly safeStopConfirmed:true;readonly verificationReference:string;}
export interface AttentionResolution {readonly operationKey:string;readonly expectedVersion:number;readonly releaseDriver:boolean;readonly vehicleDecision:'NO_CHANGE'|'READY'|'KEEP_WITHDRAWN';readonly safetyAndTransferConfirmed:true;readonly reason:string;readonly verificationReference:string;}
@Injectable({providedIn:'root'})
export class ServiceAttentionApiService {
  private readonly http=inject(HttpClient);private readonly config=inject(RuntimeConfigService);
  list(service:string){return this.http.get<readonly ServiceAttention[]>(this.config.apiUrl('/scheduled-services/'+encodeURIComponent(service)+'/attention'));}
  interrupt(assignment:string,c:AdminInterruption){return this.http.post<ServiceAttention>(this.config.apiUrl('/assignments/'+encodeURIComponent(assignment)+'/interrupt'),c,{context:new HttpContext().set(SKIP_AUTH_REFRESH,true)});}
  resolve(id:string,c:AttentionResolution){return this.http.post<ServiceAttention>(this.config.apiUrl('/service-attention/'+encodeURIComponent(id)+'/resolve'),c,{context:new HttpContext().set(SKIP_AUTH_REFRESH,true)});}
}
