import { HttpClient,HttpContext } from '@angular/common/http';
import { inject,Injectable } from '@angular/core';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';
export interface EffectiveServiceTime {
  readonly segmentId:string;readonly driverId:string;readonly vehicleId:string;readonly ordinal:number;
  readonly originalStartedAt:string;readonly originalEndedAt:string|null;readonly startedAt:string;readonly endedAt:string|null;
}
export interface ServiceTimeRevision {
  readonly id:string;readonly operationKey:string;readonly revision:number;readonly actorId:string;readonly correctedAt:string;
  readonly reason:string;readonly originalTerminalResult:string|null;readonly effectiveTerminalResult:string;readonly verificationEffect:string;
  readonly segments:readonly {readonly segmentId:string;readonly beforeStartedAt:string;readonly beforeEndedAt:string;readonly startedAt:string;readonly endedAt:string}[];
}
export interface ServiceTimeRequest {readonly id:string;readonly segmentId:string;readonly requestedBy:string;readonly reason:string;readonly requestedAt:string;readonly correctionId:string|null;}
export interface ServiceTimeHistory {readonly serviceId:string;readonly status:string;readonly serviceVersion:number;readonly segments:readonly EffectiveServiceTime[];readonly corrections:readonly ServiceTimeRevision[];readonly requests:readonly ServiceTimeRequest[];}
export interface ServiceTimeCommand {readonly operationKey:string;readonly expectedVersion:number;readonly reason:string;readonly requestId?:string;readonly segments:readonly {readonly segmentId:string;readonly startedAt:string;readonly endedAt:string}[];}
@Injectable({providedIn:'root'})
export class ServiceTimeApiService {
  private readonly http=inject(HttpClient);private readonly config=inject(RuntimeConfigService);
  read(id:string){return this.http.get<ServiceTimeHistory>(this.config.apiUrl('/scheduled-services/'+encodeURIComponent(id)+'/time-history'));}
  correct(id:string,c:ServiceTimeCommand){return this.http.post<ServiceTimeHistory>(this.config.apiUrl('/scheduled-services/'+encodeURIComponent(id)+'/time-corrections'),c,{context:new HttpContext().set(SKIP_AUTH_REFRESH,true)});}
}
